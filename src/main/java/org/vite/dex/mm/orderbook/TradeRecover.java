package org.vite.dex.mm.orderbook;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.spongycastle.util.encoders.Hex;
import org.vite.dex.mm.constant.enums.EventType;
import org.vite.dex.mm.entity.OrderEvent;
import org.vite.dex.mm.entity.OrderModel;
import org.vite.dex.mm.entity.TradePair;
import org.vite.dex.mm.model.proto.DexTradeEvent;
import org.vite.dex.mm.utils.ViteDataDecodeUtils;
import org.vite.dex.mm.utils.client.ViteCli;
import org.vitej.core.protocol.methods.Hash;
import org.vitej.core.protocol.methods.response.AccountBlock;
import org.vitej.core.protocol.methods.response.SnapshotBlock;
import org.vitej.core.protocol.methods.response.TokenInfo;
import org.vitej.core.protocol.methods.response.VmLogInfo;
import org.vitej.core.protocol.methods.response.Vmlog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.vite.dex.mm.constant.constants.MarketMiningConst.TRADE_CONTRACT_ADDRESS;
import static org.vite.dex.mm.constant.enums.EventType.NewOrder;
import static org.vite.dex.mm.utils.ViteDataDecodeUtils.getEventType;

/**
 * 1. prepare order book, get the current order book 
 * 2. prepare events, get all events from last cycle to current 
 * 3. recover order book, recover order order to last cycle by events
 * 4. mm mining, calculate the market making mining rewards
 */
@Slf4j
public class TradeRecover {
    private final Map<String, EventStream> eventStreams = Maps.newHashMap(); // <TradePairSymbol,EventStream>
    private final Map<String, OrderBook> orderBooks = Maps.newHashMap(); // <TradePairSymbol,OrderBook>
    private Map<String, AccountBlock> accountBlockMap = Maps.newHashMap(); // <Hash,AccountBlock>
    private static List<TradePair> tradePairs = getMarketMiningOpenedTp();

    private final ViteCli viteCli;

    public TradeRecover(ViteCli viteCli) {
        this.viteCli = viteCli;
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

    public Map<String, TokenInfo> getAllTokenInfo() throws IOException {
        List<TokenInfo> tokenInfos = viteCli.getTokenInfoList(0, 500);
        Map<String, TokenInfo> tokenId2TokenInfoMap = tokenInfos.stream()
                .collect(Collectors.toMap(TokenInfo::getTokenIdRaw, tokenInfo -> tokenInfo, (k1, k2) -> k1));
        return tokenId2TokenInfoMap;
    }

    /**
     * prepare order books, get the order book of all market on current time
     *
     * @throws IOException
     */
    public void prepareOrderBooks() throws IOException {
        try {
            for (TradePair tp : tradePairs) {
                OrderBook orderBook = new OrderBook.Impl();
                String tradePairSymbol = tp.getTradePair();
                // get sell orders of the trade-pair
                List<OrderModel> sellOrders = getSingleSideOrders(tp.getTradeTokenId(), tp.getQuoteTokenId(), true,
                        100);
                // get buy orders of the trade-pair
                List<OrderModel> buysOrders = getSingleSideOrders(tp.getTradeTokenId(), tp.getQuoteTokenId(), false,
                        100);
                orderBook.init(buysOrders, sellOrders);
                // buysOrders.stream().forEach(o -> {
                //     if(o.getOrderId().equals("00000300ffffffff4fed5fa0dfff005d94f2bd000014")){
                //         System.out.println("aaaaa");
                //     }
                // });
                this.orderBooks.put(tradePairSymbol, orderBook);
                log.info("the order book of tradePair [{}] is prepared", tp.getTradePair());
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
    public List<OrderModel> getSingleSideOrders(String tradeTokenId, String quoteTokenId, boolean side, int pageCnt)
            throws IOException {
        List<OrderModel> singleSideOrders = Lists.newLinkedList();
        int round = 0;
        while (true) {
            List<OrderModel> orders = viteCli.getOrdersFromMarket(tradeTokenId, quoteTokenId, side, pageCnt * round,
                    pageCnt * (round + 1));
            if (orders == null || orders.isEmpty()) {
                break;
            }
            singleSideOrders.addAll(orders);
            if (orders.size() < pageCnt) {
                break;
            }
            round++;
        }
        return singleSideOrders;
    }

    /**
     * 1. get trade contract vmLogs 2. parse and group vmLogs
     *
     * @param startTime second
     */
    public void prepareEvents(long startTime) throws Exception {
        // 1. get all events
        AccountBlock latestAccountBlock = viteCli.getLatestAccountBlock();
        Hash currentHash = latestAccountBlock.getHash();
        AccountBlock cycleStartBlock = getLastCycleStartAccountBlock(startTime, currentHash);
        if (cycleStartBlock == null) {
            throw new Exception("cycle start block is not exist");
        }
        Long startHeight = cycleStartBlock.getHeight();
        Long endHeight = latestAccountBlock.getHeight();
        List<VmLogInfo> vmLogInfoList = viteCli.getEventsByHeightRange(startHeight, endHeight, 1000);
        log.info("succeed to get [{}] events from height {} to {} of the trade-contract chain", vmLogInfoList.size(),
                startHeight, endHeight);

        // 2. parse vmLogs and group these vmLogs by trade-pair
        for (VmLogInfo vmLogInfo : vmLogInfoList) {
            OrderEvent orderEvent = OrderEvent.fromVmLog(vmLogInfo);

            if (!orderEvent.ignore()) {
                AccountBlock block = accountBlockMap.get(orderEvent.getBlockHash());
                if (block != null) {
                    orderEvent.setTimestamp(block.getTimestampRaw());
                }
                EventStream eventStream = eventStreams.getOrDefault(orderEvent.tradePair(), new EventStream());
                eventStream.addEvent(orderEvent);
                eventStreams.put(orderEvent.tradePair(), eventStream);
            }
        }
        log.info("parse vmLogs and divide them into different group successfully");
    }

    //TODOdifficult to solve. Assuming that the filtered event is right 
    public void filterEvents() {
        // tradePairs.forEach(tp -> {
        //     EventStream events = eventStreams.get(tp.getTradePair());
        //     OrderBook orderBook = orderBooks.get(tp.getTradePair());

        //     if (events != null && orderBook != null) {
        //         events.filter(orderBook);
        //     }
        // });
    }

    private Long getContractChainHeight(long time) throws IOException {
        SnapshotBlock snapshotBlock = viteCli.getSnapshotBlockBeforeTime(time);
        Long endHeight = snapshotBlock.getHeight();

        while (true) {
            Map<String, SnapshotBlock.HashHeight> snapshotContent = snapshotBlock.getSnapshotDataRaw();
            if (snapshotContent != null && snapshotContent.containsKey(TRADE_CONTRACT_ADDRESS)) {
                SnapshotBlock.HashHeight hashHeight = snapshotContent.get(TRADE_CONTRACT_ADDRESS);
                endHeight = hashHeight.getHeight();
                break;
            }
            endHeight--;
            snapshotBlock = viteCli.getSnapshotBlockByHeight(endHeight);
        }
        return endHeight;
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
        blocks = blocks.stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(o -> o.getHeight()))),
                        ArrayList::new));
        this.accountBlockMap = blocks.stream()
                .collect(Collectors.toMap(AccountBlock::getHashRaw, block -> block, (b0, b1) -> b0));
        return blocks.get(0);
    }

    /**
     * get all account blocks whose created time is greater than the startTime and
     * its hash is lower than endHash
     * 
     * @param startTime
     * @param endHash
     * @return
     * @throws IOException
     */
    private List<AccountBlock> getAccountBlocks(long startTime, Hash endHash) throws IOException {
        List<AccountBlock> blocks = Lists.newArrayList();
        while (true) {
            // the result contains the endHash block [startHash, endHash]
            List<AccountBlock> result = viteCli.getAccountBlocksBelowCurrentHash(endHash, 1000);
            if (CollectionUtils.isEmpty(result)) {
                break;
            }

            // sort blocks in descending order of height
            result.sort((block0, block1) -> block1.getHeight().compareTo(block0.getHeight()));
            for (AccountBlock aBlock : result) {
                if (aBlock.getTimestampRaw() >= startTime) {
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
    public void revertOrderBooks() throws Exception {
        for (TradePair tp : tradePairs) {
            String tradePairSymbol = tp.getTradePair();
            OrderBook orderBook = orderBooks.get(tradePairSymbol);
            EventStream eventStream = eventStreams.get(tradePairSymbol);
            if (orderBook == null || eventStream == null) {
                continue;
            }

            List<OrderEvent> events = reverseOrderEvents(eventStream.getEvents());
            for (OrderEvent event : events) {
                // System.out.println("price after parse " + event.getOrderLog().getPrice().toString());
                orderBook.revert(event);
            }
            log.info("the order book[{}] has been reverted, the addCnt {}, the removeCnt {}", tp.getTradePair(),
                    orderBook.getAddCnt(), orderBook.getRemoveCnt());

            fillAddressForOrdersGroupByTimeUnit(orderBook.getOrders().values());
            log.info("revert the order book [{}] to the start time of last cycle", tp.getTradePair());
        }
    }

    private List<OrderEvent> reverseOrderEvents(List<OrderEvent> l) {
        List<OrderEvent> events = new ArrayList<>();
        events.addAll(l);
        Collections.reverse(events);
        return events;
    }

    /**
     * divide and conquer: group orders by timeUnit and filled with address
     * 
     * @param orders
     * @throws Exception
     */
    private void fillAddressForOrdersGroupByTimeUnit(Collection<OrderModel> orders) throws Exception {
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
        Long startHeight = getContractChainHeight(startTime);
        Long endHeight = getContractChainHeight(endTime);

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

    public Map<String, EventStream> getEventStreams() {
        return eventStreams;
    }

    public Map<String, OrderBook> getOrderBooks() {
        return orderBooks;
    }

    public Map<String, AccountBlock> getAccountBlockMap() {
        return accountBlockMap;
    }

    public void run(long pointTime) throws Exception {
        prepareOrderBooks();
        prepareEvents(pointTime);
        filterEvents();
        revertOrderBooks();
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
