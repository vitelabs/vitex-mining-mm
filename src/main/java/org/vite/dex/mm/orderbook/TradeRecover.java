package org.vite.dex.mm.orderbook;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.vite.dex.mm.constant.enums.EventType;
import org.vite.dex.mm.entity.OrderModel;
import org.vite.dex.mm.entity.TradePair;
import org.vite.dex.mm.exception.GlobalExceptionHandler;
import org.vite.dex.mm.model.proto.DexTradeEvent;
import org.vite.dex.mm.utils.EventParserUtils;
import org.vite.dex.mm.utils.ViteDataDecodeUtils;
import org.vite.dex.mm.utils.client.ViteCli;
import org.vitej.core.protocol.methods.Address;
import org.vitej.core.protocol.methods.Hash;
import org.vitej.core.protocol.methods.request.VmLogFilter;
import org.vitej.core.protocol.methods.response.*;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.vite.dex.mm.constant.constants.MMConst.*;

/**
 * 1. prepare order book, get the current order book
 * 2. prepare events, get all events from last cycle to current
 * 3. recover order book, recover order order to last cycle by events
 * 4. mm mining, calculate the market making mining rewards
 */

@Service
public class TradeRecover {
    private static final Logger logger = LoggerFactory.getLogger(TradeRecover.class);
    private Map<String, EventStream> eventStreams;
    private Map<String, OrderBook> orderBooks;

    // TODO create a method to query from db to get all trade-pairs
    private List<TradePair> tradePairs = new ArrayList<>();

    @Autowired
    ViteCli viteCli;

    /**
     * prepare order book, get the current order book
     *
     * @throws IOException
     */
    public void prepareOrderBooks() throws IOException {
        try {
            for (TradePair tp : tradePairs) {
                OrderBook orderBook = new OrderBook.Impl();
                String tradePair = tp.getTradeTokenId() + UnderscoreStr + tp.getQuoteTokenId();
                // get the sell orders of the trade-pair
                List<OrderModel> sellOrders = getSingleSideOrders(tp.getTradeTokenId(), tp.getQuoteTokenId(), true);
                orderBook.setSells(sellOrders);
                // get the buy orders of the trade-pair
                List<OrderModel> buysOrders = getSingleSideOrders(tp.getTradeTokenId(), tp.getQuoteTokenId(), false);
                orderBook.setBuys(buysOrders);
                this.orderBooks.put(tradePair, orderBook);
            }
        } catch (Exception e) {
            e.printStackTrace();
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
    private List<OrderModel> getSingleSideOrders(String tradeTokenId, String quoteTokenId, boolean side) throws IOException {
        List<OrderModel> singleSideOrders = new LinkedList<>();
        while (true) {
            int round = 0;
            CommonResponse response = viteCli.getOrdersFromMarket(tradeTokenId, quoteTokenId, side, round, 100 * (round + 1));
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
     *
     * @param startTime
     */
    public void prepareEvents(long startTime) throws IOException {
        try {
            // 1. get all events
            AccountBlock latestAccountBlock = viteCli.getLatestAccountBlock();
            Hash currentHash = latestAccountBlock.getHash();
            AccountBlock cycleStartABlock = getCycleStartAccountBlock(currentHash, startTime);
            Long startHeight = cycleStartABlock.getHeight();
            Long endHeight = latestAccountBlock.getHeight();
            List<VmLogInfo> vmLogInfoList = viteCli.getEventsByHeightRange(startHeight, endHeight);

            // 2. parse vmLogs and group these vmLogs by trade-pair
            for (VmLogInfo vmLogInfo : vmLogInfoList) {
                Vmlog log = vmLogInfo.getVmlog();
                EventType eventType = EventParserUtils.getEventType(log.getTopicsRaw());
                byte[] event = log.getData();

                switch (eventType) {
                    case NewOrder:
                        DexTradeEvent.NewOrderInfo dexOrder = DexTradeEvent.NewOrderInfo.parseFrom(event);
                        String tradeTokenOfNewOrder = ViteDataDecodeUtils.getShowToken(dexOrder.getTradeToken().toByteArray());
                        String quoteTokenOfNewOrder = ViteDataDecodeUtils.getShowToken(dexOrder.getQuoteToken().toByteArray());
                        String tradePairOfNewOrder = tradeTokenOfNewOrder + UnderscoreStr + quoteTokenOfNewOrder;
                        eventStreams.putIfAbsent(tradePairOfNewOrder, new EventStream()).addEvent(vmLogInfo);
                        break;
                    // both cancel and fill order will emit the updateEvent
                    case UpdateOrder:
                        DexTradeEvent.OrderUpdateInfo orderUpdateInfo = DexTradeEvent.OrderUpdateInfo.parseFrom(event);
                        String tradeTokenOfUpdateOrder = ViteDataDecodeUtils.getShowToken(orderUpdateInfo.getTradeToken().toByteArray());
                        String quoteTokenOfUpdateOrder = ViteDataDecodeUtils.getShowToken(orderUpdateInfo.getQuoteToken().toByteArray());
                        String tradePairOfUpdateOrder = tradeTokenOfUpdateOrder + UnderscoreStr + quoteTokenOfUpdateOrder;
                        eventStreams.putIfAbsent(tradePairOfUpdateOrder, new EventStream()).addEvent(vmLogInfo);
                        break;
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
        AccountBlock cycleStartABlock = null;
        while (true) {
            boolean found = false;
            List<AccountBlock> result = viteCli.getAccountBlocksBelowCurrentHash(currentHash, 50);
            for (AccountBlock aBlock : result) {
                if (aBlock.getTimestamp().isBefore(startTime)) {
                    cycleStartABlock = aBlock;
                    found = true;
                    break;
                } else {
                    cycleStartABlock = aBlock;
                }
            }
            if (found) {
                break;
            }
            currentHash = cycleStartABlock.getHash();
        }
        return cycleStartABlock;
    }

    /**
     * recover order book of all trade pair to the previous cycle
     */
    public void recoverOrderBooks() {
        for (TradePair tp : tradePairs) {
            String tradePair = tp.getTradeTokenId() + UnderscoreStr + tp.getQuoteTokenId();
            OrderBook orderBook = orderBooks.get(tradePair);
            EventStream eventStream = eventStreams.get(tradePair);
            for (VmLogInfo logInfo : eventStream.getEvents()) {
                orderBook.revert(logInfo);
            }
        }
    }
}
