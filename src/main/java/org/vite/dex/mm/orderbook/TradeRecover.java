package org.vite.dex.mm.orderbook;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.spongycastle.util.encoders.Hex;
import org.vite.dex.mm.constant.enums.EventType;
import org.vite.dex.mm.entity.OrderBookInfo;
import org.vite.dex.mm.entity.OrderEvent;
import org.vite.dex.mm.entity.OrderModel;
import org.vite.dex.mm.entity.TradePair;
import org.vite.dex.mm.model.proto.DexTradeEvent;
import org.vite.dex.mm.reward.RewardKeeper;
import org.vite.dex.mm.reward.cfg.MiningRewardCfg;
import org.vite.dex.mm.utils.CommonUtils;
import org.vite.dex.mm.utils.ViteDataDecodeUtils;
import org.vite.dex.mm.utils.client.ViteCli;
import org.vitej.core.protocol.methods.response.TokenInfo;
import org.vitej.core.protocol.methods.response.VmLogInfo;
import org.vitej.core.protocol.methods.response.Vmlog;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.vite.dex.mm.constant.enums.EventType.NewOrder;
import static org.vite.dex.mm.utils.ViteDataDecodeUtils.getEventType;

/**
 * 1. prepare order book, get the current order book 2. prepare events, get all
 * events from last cycle to current 3. recover order book, recover order order
 * to last cycle by events 4. mm mining, calculate the market making mining
 * rewards
 */
@Slf4j
public class TradeRecover {
    private final Map<String, EventStream> eventStreams = Maps.newHashMap(); // <TradePairSymbol,EventStream>
    private Map<String, OrderBook> orderBooks = Maps.newHashMap(); // <TradePairSymbol,OrderBook>
    private BlockEventStream blockEventStream = null;

    private static List<TradePair> tradePairs = getMarketMiningOpenedTp();

    private final ViteCli viteCli;

    public TradeRecover(ViteCli viteCli) {
        this.viteCli = viteCli;
    }

    public Map<String, EventStream> getEventStreams() {
        return eventStreams;
    }

    public Map<String, OrderBook> getOrderBooks() {
        return orderBooks;
    }

    public void setOrderBooks(Map<String, OrderBook> orderBooks) {
        this.orderBooks = orderBooks;
    }

    public BlockEventStream getBlockEventStream() {
        return blockEventStream;
    }

    public void setBlockEventStream(BlockEventStream blockEventStream) {
        this.blockEventStream = blockEventStream;
    }

    // TODO get data from contract
    public static List<TradePair> getMarketMiningOpenedTp() {
        List<TradePair> res = new ArrayList<>();
        TradePair tp = new TradePair();
        tp.setTradeTokenSymbol("ETH-000");
        tp.setQuoteTokenSymbol("USDT-000");
        tp.setTradeTokenId("tti_687d8a93915393b219212c73"); // ETH
        tp.setQuoteTokenId("tti_80f3751485e4e83456059473"); // USDT
        tp.setMmEffectiveInterval(0.2);
        tp.setMarketMiningOpen(true);
        tp.setMmRewardMultiple(5.0);
        tp.setBuyAmountThanSellRatio(10);
        tp.setSellAmountThanBuyRatio(10);
        res.add(tp);

        return res;
    }

    public Map<String, MiningRewardCfg> miningRewardCfgMap() {
        Map<String, MiningRewardCfg> tradePairCfgMap = new HashMap<>();

        getMarketMiningOpenedTp().stream().forEach(tp -> {
            String symbol = tp.getTradePair();
            MiningRewardCfg miningRewardCfg = MiningRewardCfg.fromTradePair(tp);
            tradePairCfgMap.put(symbol, miningRewardCfg);
        });

        return tradePairCfgMap;
    }

    public Map<String, TokenInfo> getAllTokenInfo() throws IOException {
        List<TokenInfo> tokenInfos = viteCli.getTokenInfoList(0, 500);
        Map<String, TokenInfo> tokenId2TokenInfoMap = tokenInfos.stream()
                .collect(Collectors.toMap(TokenInfo::getTokenIdRaw, tokenInfo -> tokenInfo, (k1, k2) -> k1));
        return tokenId2TokenInfoMap;
    }

    /**
     * prerequisite data is necessary
     */
    public Tokens prepareData() throws Exception {
        try {
            Map<String, TokenInfo> tokensMap = getAllTokenInfo();
            return new Tokens(tokensMap);
        } catch (Exception e) {
            log.error("failed to prepare prerequisite data, err: ", e);
            throw e;
        }
    }

    /**
     * get orders from specified order book of trade-pair
     * 
     * @param tradeTokenId
     * @param quoteTokenId
     * @param pageCnt
     * @return
     * @throws IOException
     */
    public OrderBookInfo getOrdersFromMarket(String tradeTokenId, String quoteTokenId, int pageCnt) throws IOException {
        List<OrderModel> orderModels = Lists.newLinkedList();
        List<Long> heights = Lists.newLinkedList();

        int idx = 0;
        while (true) {
            OrderBookInfo orderBookInfo = viteCli.getOrdersFromMarket(tradeTokenId, quoteTokenId, pageCnt * idx,
                    pageCnt * (idx + 1), pageCnt * idx, pageCnt * (idx + 1));
            if (orderBookInfo == null || CollectionUtils.isEmpty(orderBookInfo.getCurrOrders())) {
                break;
            }

            orderBookInfo.getCurrOrders().forEach(currOrder -> {
                orderModels.add(OrderModel.fromCurrentOrder(currOrder, tradeTokenId, quoteTokenId));
            });

            heights.add(orderBookInfo.getCurrBlockheight());

            if (orderBookInfo.getCurrOrders().size() < pageCnt) {
                break;
            }

            idx++;
        }
        // System.out.println(heights);
        Long maxHeight = heights.stream().max(Long::compareTo).get();
        return OrderBookInfo.fromOrderModelsAndHeight(orderModels, maxHeight);
    }

    /**
     * divide and conquer: group orders by timeUnit and filled with address
     * respectively
     * 
     * @param orders
     * @throws Exception
     */
    public void fillAddressForOrdersGroupByTimeUnit(Map<String, OrderBook> books) throws Exception {
        List<OrderModel> allOrders = new ArrayList<>();
        books.values().forEach(orderBook -> {
            allOrders.addAll(orderBook.getOrders().values());
        });

        List<OrderModel> orders = allOrders;

        orders = orders.stream().filter(t -> t.emptyAddress()).collect(Collectors.toList());

        Map<Long, List<OrderModel>> orderGroups = orders.stream()
                .collect(Collectors.groupingBy(t -> t.getTimestamp() / TimeUnit.MINUTES.toSeconds(10)));

        for (List<OrderModel> v : orderGroups.values()) {
            fillAddressForOrders(v);
        }

        orders.stream().forEach(order -> {
            assert !order.emptyAddress();
        });

    }

    /**
     * Add missing address to the restored orderBook
     *
     * @param orders
     * @throws IOException
     */
    private void fillAddressForOrders(Collection<OrderModel> orders) throws Exception {
        Map<String, OrderModel> orderMap = orders.stream()
                .collect(Collectors.toMap(OrderModel::getOrderId, o -> o, (k0, k1) -> k0));

        long start = orders.stream().min(Comparator.comparing(OrderModel::getTimestamp)).get().getTimestamp();
        long end = orders.stream().max(Comparator.comparing(OrderModel::getTimestamp)).get().getTimestamp();

        start = start - TimeUnit.MINUTES.toSeconds(5);
        end = end + TimeUnit.MINUTES.toSeconds(5);

        orderMap = fillAddressForOrders(orderMap, start, end);
        if (orderMap.isEmpty()) {
            return;
        }

        // downward and upward
        int cnt = 1;
        while (true) {
            long start0 = start - TimeUnit.MINUTES.toSeconds(5);
            orderMap = fillAddressForOrders(orderMap, start0, start);
            if (orderMap.isEmpty()) {
                break;
            }

            long end1 = end + TimeUnit.MINUTES.toSeconds(5);
            orderMap = fillAddressForOrders(orderMap, end, end1);
            if (orderMap.isEmpty()) {
                break;
            }

            start = start0;
            end = end1;

            if (++cnt >= 3) {
                throw new RuntimeException("the address of Order is not found!");
            }
        }
    }

    /**
     * 1.get height range of contract-chain between startTime to endTime 2.get
     * eventLogs in the range of height 3.find NewOrder eventLog, filled the address
     * in Order
     *
     * @param orderMap
     * @param startTime
     * @param endTime
     * @return
     * @throws IOException
     */
    private Map<String, OrderModel> fillAddressForOrders(Map<String, OrderModel> orderMap, long startTime, long endTime)
            throws IOException {
        Long startHeight = viteCli.getContractChainHeight(startTime);
        Long endHeight = viteCli.getContractChainHeight(endTime);

        List<VmLogInfo> vmLogInfoList = viteCli.getEventsByHeightRange(startHeight, endHeight, 1000);

        for (VmLogInfo vmLogInfo : vmLogInfoList) {
            Vmlog vmlog = vmLogInfo.getVmlog();
            byte[] event = vmlog.getData();
            EventType eventType = getEventType(vmlog.getTopicsRaw());
            if (eventType == NewOrder) {
                DexTradeEvent.NewOrderInfo dexOrder = DexTradeEvent.NewOrderInfo.parseFrom(event);
                String newOrderId = Hex.toHexString(dexOrder.getOrder().getId().toByteArray());
                OrderModel order = orderMap.get(newOrderId);
                if (order != null && StringUtils.isEmpty(order.getAddress())) {
                    order.setAddress(
                            ViteDataDecodeUtils.getShowAddress(dexOrder.getOrder().getAddress().toByteArray()));
                }
            }
        }
        orderMap = orderMap.values().stream().filter(t -> t.emptyAddress())
                .collect(Collectors.toMap(OrderModel::getOrderId, o -> o, (k0, k1) -> k0));
        return orderMap;
    }

    public void run(long prevTime, long endTime) throws Exception {
        Traveller traveller = new Traveller();
        long snapshotTime = CommonUtils.getFixedTime();

        Tokens tokens = prepareData();
        OrderBooks snapshotOrderBooks = traveller.travelInTime(snapshotTime, tokens, viteCli, tradePairs);
        OrderBooks originOrderBooks = recoverInTime(snapshotOrderBooks, prevTime, tokens, viteCli);
        fillAddressForOrdersGroupByTimeUnit(originOrderBooks.getBooks());
        this.setOrderBooks(originOrderBooks.getBooks());

        RewardKeeper rewardKeeper = new RewardKeeper(viteCli);
        double totalReleasedViteAmount = 1000000.0;
        Map<String, Map<Integer, BigDecimal>> finalRes = rewardKeeper.calcAddressMarketReward(originOrderBooks,
                getBlockEventStream(), totalReleasedViteAmount, prevTime, endTime, miningRewardCfgMap());
        System.out.println(finalRes);
    }

    // recover all orderbooks to start time of last cycle
    public OrderBooks recoverInTime(OrderBooks orderBooks, Long time, Tokens tokens, ViteCli viteCli)
            throws IOException {
        Long startHeight = viteCli.getContractChainHeight(time);
        Long endHeight = viteCli.getLatestAccountHeight();

        BlockEventStream stream = new BlockEventStream(startHeight, endHeight);
        stream.init(viteCli, tokens);
        stream.patchTimestampToOrderEvent(viteCli);
        this.setBlockEventStream(stream);

        stream.action(orderBooks, true, true);

        return orderBooks;
    }

    public void initFrom(Map<String, List<OrderModel>> orders, Map<String, List<OrderEvent>> events) {
        orders.forEach((k, v) -> {
            orderBooks.put(k, new OrderBook.Impl().initFromOrders(v));
        });

        events.forEach((k, v) -> {
            EventStream es = new EventStream(
                    v.stream().sorted(Comparator.comparing(OrderEvent::getTimestamp)).collect(Collectors.toList()));
            eventStreams.put(k, es);
        });
    }
}
