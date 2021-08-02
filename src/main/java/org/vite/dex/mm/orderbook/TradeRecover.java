package org.vite.dex.mm.orderbook;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.vite.dex.mm.constant.enums.EventType;
import org.vite.dex.mm.entity.OrderEvent;
import org.vite.dex.mm.entity.OrderModel;
import org.vite.dex.mm.entity.TradePair;
import org.vite.dex.mm.model.proto.DexTradeEvent;
import org.vite.dex.mm.utils.EventParserUtils;
import org.vite.dex.mm.utils.ViteDataDecodeUtils;
import org.vite.dex.mm.utils.client.ViteCli;
import org.vitej.core.protocol.methods.Hash;
import org.vitej.core.protocol.methods.response.AccountBlock;
import org.vitej.core.protocol.methods.response.CommonResponse;
import org.vitej.core.protocol.methods.response.VmLogInfo;
import org.vitej.core.protocol.methods.response.Vmlog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.vite.dex.mm.constant.constants.MMConst.UnderscoreStr;
import static org.vite.dex.mm.constant.enums.OrderEventType.*;

/**
 * 1. prepare order book, get the current order book
 * 2. prepare events, get all events from last cycle to current
 * 3. recover order book, recover order order to last cycle by events
 * 4. mm mining, calculate the market making mining rewards
 */

@Service
@Slf4j
public class TradeRecover {
    private static final Logger logger = LoggerFactory.getLogger(TradeRecover.class);
    private Map<String, EventStream> eventStreams; //<TradePairStr,EventStream>
    private Map<String, OrderBook> orderBooks;     //<TradePairStr,OrderBook>
    private Map<String, AccountBlock> accountBlockMap; //<BlockHash,AccountBlock>
    private long currentCheckPointTime;

    // TODO create a method to query from Trade Contract to get all trade-pairs
    private List<TradePair> tradePairs = new ArrayList<>();

    @Autowired
    ViteCli viteCli;

    /**
     * prepare order book, get the current order book
     *
     * @throws IOException
     */
    public void prepareOrderBooks() throws IOException {
        currentCheckPointTime = System.currentTimeMillis() / 1000;
        try {
            for (TradePair tp : tradePairs) {
                OrderBook orderBook = new OrderBook.Impl();
                String tradePair = tp.getTradeTokenId() + UnderscoreStr + tp.getQuoteTokenId();
                // get the sell orders of the trade-pair
                List<OrderModel> sellOrders = getSingleSideOrders(tp.getTradeTokenId(), tp.getQuoteTokenId(), true);
                // get the buy orders of the trade-pair
                // how to ensure that the sellOrders and buysOrders are taken at the same time?
                List<OrderModel> buysOrders = getSingleSideOrders(tp.getTradeTokenId(), tp.getQuoteTokenId(), false);
                orderBook.init(buysOrders, sellOrders);
                this.orderBooks.put(tradePair, orderBook);
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
     * 2. parse vmLogs
     * 3. mark timestamp for vm log
     * 4. category
     *
     * @param startTime
     */
    public void prepareEvents(long startTime) throws IOException {
        try {
            // 1. get all events
            AccountBlock latestAccountBlock = viteCli.getLatestAccountBlock();
            Hash currentHash = latestAccountBlock.getHash();
            AccountBlock cycleStartBlock = getCycleStartAccountBlock(currentHash, startTime);
            Long startHeight = cycleStartBlock.getHeight();
            Long endHeight = latestAccountBlock.getHeight();
            List<VmLogInfo> vmLogInfoList = viteCli.getEventsByHeightRange(startHeight, endHeight, 50);

            // 2. parse vmLogs and group these vmLogs by trade-pair
            for (VmLogInfo vmLogInfo : vmLogInfoList) {
                Vmlog log = vmLogInfo.getVmlog();
                byte[] event = log.getData();
                EventType eventType = EventParserUtils.getEventType(log.getTopicsRaw());

                switch (eventType) {
                    case NewOrder:
                        DexTradeEvent.NewOrderInfo dexOrder = DexTradeEvent.NewOrderInfo.parseFrom(event);
                        String tradeTokenOfNewOrder =
                                ViteDataDecodeUtils.getShowToken(dexOrder.getTradeToken().toByteArray());
                        String quoteTokenOfNewOrder =
                                ViteDataDecodeUtils.getShowToken(dexOrder.getQuoteToken().toByteArray());
                        String tradePairOfNewOrder = tradeTokenOfNewOrder + UnderscoreStr + quoteTokenOfNewOrder;
                        Long newOrderTimestamp = accountBlockMap.get(vmLogInfo.getAccountBlockHash().toString()).getTimestampRaw();
                        if (newOrderTimestamp > currentCheckPointTime) {
                            continue;
                        }
                        OrderEvent orderEvent = new OrderEvent(vmLogInfo, newOrderTimestamp, OrderNew);
                        eventStreams.putIfAbsent(tradePairOfNewOrder, new EventStream()).addEvent(orderEvent);
                        break;
                    // both cancel and fill order will emit the updateEvent
                    case UpdateOrder:
                        DexTradeEvent.OrderUpdateInfo orderUpdateInfo = DexTradeEvent.OrderUpdateInfo.parseFrom(event);
                        String tradeTokenOfUpdateOrder =
                                ViteDataDecodeUtils.getShowToken(orderUpdateInfo.getTradeToken().toByteArray());
                        String quoteTokenOfUpdateOrder =
                                ViteDataDecodeUtils.getShowToken(orderUpdateInfo.getQuoteToken().toByteArray());
                        String tradePairOfUpdateOrder =
                                tradeTokenOfUpdateOrder + UnderscoreStr + quoteTokenOfUpdateOrder;
                        Long updateOrderTimestamp = accountBlockMap.get(vmLogInfo.getAccountBlockHash().toString()).getTimestampRaw();
                        if (updateOrderTimestamp > currentCheckPointTime) {
                            continue;
                        }
                        OrderEvent e = new OrderEvent(vmLogInfo, updateOrderTimestamp, OrderUpdate);
                        eventStreams.putIfAbsent(tradePairOfUpdateOrder, new EventStream()).addEvent(e);
                        break;
                    //why should parse this ones?
                    case TX:

                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * get the start account block
     *
     * @param currentHash
     * @param startTime
     * @return
     * @throws IOException
     */
    private AccountBlock getCycleStartAccountBlock(Hash currentHash, long startTime) throws IOException {
        if (currentHash == null) {
            return null;
        }

        AccountBlock cycleStartBlock = null;
        while (true) {
            boolean found = false;
            List<AccountBlock> result = viteCli.getAccountBlocksBelowCurrentHash(currentHash, 50);
            // sort blocks in descending order of height
            result.sort((block0, block1) -> block1.getHeight().compareTo(block0.getHeight()));

            for (AccountBlock aBlock : result) {
                accountBlockMap.put(aBlock.getHash().toString(), aBlock);
                if (aBlock.getTimestamp().isBefore(startTime)) {
                    cycleStartBlock = aBlock;
                    found = true;
                    break;
                } else {
                    cycleStartBlock = aBlock;
                }
            }
            if (found) {
                break;
            }
            currentHash = cycleStartBlock.getHash();
        }
        return cycleStartBlock;
    }

    /**
     * recover order book of all trade pair to the previous cycle
     */
    public void recoverOrderBooks() {
        for (TradePair tp : tradePairs) {
            String tradePair = tp.getTradeTokenId() + UnderscoreStr + tp.getQuoteTokenId();
            OrderBook orderBook = orderBooks.get(tradePair);
            EventStream eventStream = eventStreams.get(tradePair);
            for (OrderEvent event : eventStream.getEvents()) {
                orderBook.revert(event.getVmLogInfo());
            }
        }
    }
}
