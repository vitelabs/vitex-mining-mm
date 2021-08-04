package org.vite.dex.mm.orderbook;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.vite.dex.mm.entity.OrderEvent;
import org.vite.dex.mm.entity.OrderModel;
import org.vite.dex.mm.entity.TradePair;
import org.vite.dex.mm.utils.client.ViteCli;
import org.vitej.core.protocol.methods.Hash;
import org.vitej.core.protocol.methods.response.AccountBlock;
import org.vitej.core.protocol.methods.response.CommonResponse;
import org.vitej.core.protocol.methods.response.VmLogInfo;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 1. prepare order book, get the current order book
 * 2. prepare events, get all events from last cycle to current
 * 3. recover order book, recover order order to last cycle by events
 * 4. mm mining, calculate the market making mining rewards
 */

@Service
@Slf4j
public class TradeRecover {
    private Map<String, EventStream> eventStreams; //<TradePairSymbol,EventStream>
    private Map<String, OrderBook> orderBooks; //<TradePairSymbol,OrderBook>
    private Map<String, AccountBlock> accountBlockMap = new HashMap<>(); //<Hash,AccountBlock>

    // TODO create a method to query from Trade Contract to get all trade-pairs
    private static List<TradePair> tradePairs = getAllTradePairs();

    @Resource
    ViteCli viteCli;


    public static List<TradePair> getAllTradePairs() {
        List<TradePair> res = new ArrayList<>();
        TradePair tp = new TradePair();
        tp.setTradeTokenId("tti_687d8a93915393b219212c73");
        tp.setQuoteTokenId("tti_80f3751485e4e83456059473");
        res.add(tp);
        return res;
    }

    /**
     * prepare order book, get the current order book
     *
     * @throws IOException
     */
    public void prepareOrderBooks() throws IOException {
        try {
            for (TradePair tp : tradePairs) {
                OrderBook orderBook = new OrderBook.Impl();
                String tradePairSymbol = tp.getTradePairSymbol();
                // get sell orders of the trade-pair
                List<OrderModel> sellOrders = getSingleSideOrders(tp.getTradeTokenId(), tp.getQuoteTokenId(), true);
                // get buy orders of the trade-pair
                List<OrderModel> buysOrders = getSingleSideOrders(tp.getTradeTokenId(), tp.getQuoteTokenId(), false);
                orderBook.init(buysOrders, sellOrders);
                this.orderBooks.put(tradePairSymbol, orderBook);
            }
        } catch (Exception e) {
            log.error("prepareOrderBooks occurs exception,the err:", e);
            throw e;
        }
    }

    /**
     * get the single side orders from the order book
     *
     * @param tradeTokenId
     * @param quoteTokenId
     * @param side
     * @return
     * @throws IOException
     */
    private List<OrderModel> getSingleSideOrders(String tradeTokenId, String quoteTokenId, boolean side)
            throws IOException {
        List<OrderModel> singleSideOrders = new LinkedList<>();
        while (true) {
            int round = 0;
            CommonResponse response =
                    viteCli.getOrdersFromMarket(tradeTokenId, quoteTokenId, side, 100 * round, 100 * (round + 1));
            String mapStrSell = JSON.toJSONString(response.getResult());
            Map<String, Object> resMap = JSON.parseObject(mapStrSell, Map.class);
            String jsonString = JSONObject.toJSONString(resMap.get("orders"));
            List<OrderModel> orders = JSON.parseArray(jsonString, OrderModel.class);
            if (orders == null || orders.size() == 0) {
                break;
            }
            singleSideOrders.addAll(orders);
            round++;
        }
        return singleSideOrders;
    }

    /**
     * 1. get trade contract vmLogs
     * 2. parse and group vmLogs
     *
     * @param startTime
     */
    public void prepareEvents(long startTime) throws IOException {
        // 1. get all events
        AccountBlock latestAccountBlock = viteCli.getLatestAccountBlock();
        Hash currentHash = latestAccountBlock.getHash();
        AccountBlock cycleStartBlock = getLastCycleStartAccountBlock(startTime, currentHash);
        if (cycleStartBlock == null) {
            return;
        }
        Long startHeight = cycleStartBlock.getHeight();
        Long endHeight = latestAccountBlock.getHeight();
        List<VmLogInfo> vmLogInfoList = viteCli.getEventsByHeightRange(startHeight, endHeight, 100);

        // 2. parse vmLogs and group these vmLogs by trade-pair
        for (VmLogInfo vmLogInfo : vmLogInfoList) {
            OrderEvent orderEvent = new OrderEvent(vmLogInfo);
            orderEvent.parse();
            orderEvent.setTimestamp(accountBlockMap.get(orderEvent.getBlockHash()).getTimestampRaw());
            if (!orderEvent.ignore()) {
                eventStreams.putIfAbsent(orderEvent.getTradePairSymbol(), new EventStream()).addEvent(orderEvent);
            }
        }
    }

    public void filterEvents() {
        tradePairs.forEach(tp -> {
            filterEvents(tp);
        });
    }

    private void filterEvents(TradePair tp) {
        EventStream events = eventStreams.get(tp.getTradePairSymbol());
        OrderBook orderBook = orderBooks.get(tp.getTradePairSymbol());
        events.filter(orderBook);
    }

    /**
     * get the start account block
     *
     * @param startTime
     * @param currentHash
     * @return
     * @throws IOException
     */
    private AccountBlock getLastCycleStartAccountBlock(long startTime, Hash currentHash) throws IOException {
        if (currentHash == null) {
            return null;
        }

        List<AccountBlock> blocks = getAccountBlocks(startTime, currentHash);
        if (CollectionUtils.isEmpty(blocks)) {
            return null;
        }
        this.accountBlockMap = blocks.stream().collect(Collectors.toMap(AccountBlock::getHashRaw, block -> block));
        return blocks.get(blocks.size() - 1);
    }

    private List<AccountBlock> getAccountBlocks(long startTime, Hash endHash) throws IOException {
        List<AccountBlock> blocks = new ArrayList<>();
        while (true) {
            // todo [] () 
            List<AccountBlock> result = viteCli.getAccountBlocksBelowCurrentHash(endHash, 200);
            if (CollectionUtils.isEmpty(result)) {
                break;
            }
            // sort blocks in descending order of height
            result.sort((block0, block1) -> block1.getHeight().compareTo(block0.getHeight()));
            for (AccountBlock aBlock : result) {
                if (!aBlock.getTimestamp().isBefore(startTime)) {
                    blocks.add(aBlock);
                    endHash = aBlock.getHash();
                } else {
                    // ignore
                    return blocks;
                }
            }
        }
        return blocks;
    }

    /**
     * recover order book of all trade pair to the previous cycle
     */
    public void revertOrderBooks() {
        for (TradePair tp : tradePairs) {
            String tradePairSymbol = tp.getTradePairSymbol();
            OrderBook orderBook = orderBooks.get(tradePairSymbol);
            EventStream eventStream = eventStreams.get(tradePairSymbol);
            for (OrderEvent event : eventStream.getEvents()) {
                orderBook.revert(event);
            }
        }
    }

    public void run(long pointTime) throws IOException {
        prepareOrderBooks();
        prepareEvents(pointTime);
        filterEvents();
        revertOrderBooks();
    }
}
