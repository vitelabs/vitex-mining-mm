package org.vite.data.dex;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.io.Files;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.vite.dex.mm.DexApplication;
import org.vite.dex.mm.entity.BlockEvent;
import org.vite.dex.mm.entity.MiningAddressQuoteToken;
import org.vite.dex.mm.entity.OrderBookInfo;
import org.vite.dex.mm.entity.OrderModel;
import org.vite.dex.mm.entity.TradePair;
import org.vite.dex.mm.mapper.MiningAddressQuoteTokenRepository;
import org.vite.dex.mm.orderbook.BlockEventStream;
import org.vite.dex.mm.orderbook.OrderBooks;
import org.vite.dex.mm.orderbook.Tokens;
import org.vite.dex.mm.orderbook.TradeRecover;
import org.vite.dex.mm.orderbook.TradeRecover.RecoverResult;
import org.vite.dex.mm.orderbook.Traveller;
import org.vite.dex.mm.reward.RewardEngine;
import org.vite.dex.mm.reward.RewardKeeper;
import org.vite.dex.mm.reward.RewardKeeper.FinalResult;
import org.vite.dex.mm.reward.SettleService;
import org.vite.dex.mm.utils.CommonUtils;
import org.vite.dex.mm.utils.ViteDataDecodeUtils;
import org.vite.dex.mm.utils.client.ViteCli;
import org.vite.dex.mm.utils.decode.BytesUtils;
import org.vitej.core.protocol.methods.response.AccountBlock;
import org.vitej.core.protocol.methods.response.SnapshotBlock;
import org.vitej.core.protocol.methods.response.TokenInfo;

import javax.annotation.Resource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootTest(classes = DexApplication.class)
@Slf4j
class DexApplicationTests {

    @Resource
    private ViteCli viteCli;

    @Autowired
    MiningAddressQuoteTokenRepository addressMarketRewardRepository;

    @Autowired
    RewardEngine engine;

    @Autowired
    SettleService settleService;

    @Test
    void contextLoads() {
    }

    @Test
    public void testGetOrderBooks() throws Exception {
        OrderBookInfo orderBook = viteCli.getOrdersFromMarket("tti_687d8a93915393b219212c73",
                "tti_80f3751485e4e83456059473", 1000);
        orderBook.getCurrOrders().forEach(t -> {
            System.out.println(t.getOrderId());
        });
    }

    @Test
    public void testTravel() throws Exception {
        Traveller traveller = new Traveller();
        List<TradePair> tradePairs = CommonUtils.getMarketMiningTradePairs();
        Tokens tokens = viteCli.getAllTokenInfos();

        long snapshotTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"))
                .parse("2021-10-12 12:30:00", new ParsePosition(0)).getTime() / 1000;

        OrderBooks snapshotOrderBooks = traveller.travelInTime(snapshotTime, tokens, viteCli, tradePairs);

        // serialize
        Map<String, Collection<OrderModel>> books = new HashMap<>();
        snapshotOrderBooks.getBooks().forEach((tradePair, orderBook) -> {
            books.put(tradePair, orderBook.getOrders().values());
        });
        Long currentHeight = snapshotOrderBooks.getCurrentHeight();
        Map<String, Object> result = new HashMap<>();
        result.put("orderbooks", books);
        result.put("currentHeight", currentHeight);

        File file = new File("dataset_orderbooks_snapshot.raw");
        Files.write(JSON.toJSONBytes(result), file);
    }

    @Test
    public void testRecoverOrderBooks() throws Exception {
        Tokens tokens = viteCli.getAllTokenInfos();
        TradeRecover tradeRecover = new TradeRecover();
        long startTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"))
                .parse("2021-10-11 12:00:00", new ParsePosition(0)).getTime() / 1000;
        // unserialize from snapshot
        OrderBooksData data = JSONObject.parseObject(new FileInputStream(new File("dataset_orderbooks_snapshot.raw")),
                OrderBooksData.class);
        Map<String, List<OrderModel>> books = data.getOrderbooks();
        Long currentHeight = data.getCurrentHeight();
        OrderBooks snapshotOrderBooks = new OrderBooks(viteCli);

        snapshotOrderBooks.initFrom(books, currentHeight);

        // recover
        RecoverResult res = tradeRecover.recoverInTime(snapshotOrderBooks, startTime, tokens, viteCli);

        OrderBooks recoverdOrderBooks = res.getOrderBooks();
        BlockEventStream eventStream = res.getStream();
        eventStream.patchTimestampToOrderEvent(viteCli);
        tradeRecover.fillAddressForOrdersGroupByTimeUnit(recoverdOrderBooks.getBooks(), viteCli);

        // serialize
        Map<String, Collection<OrderModel>> orderbooks = new HashMap<>();
        List<BlockEvent> events = eventStream.getEvents();
        recoverdOrderBooks.getBooks().forEach((tradePair, orderBook) -> {
            orderbooks.put(tradePair, orderBook.getOrders().values());
        });
        currentHeight = recoverdOrderBooks.getCurrentHeight();

        Map<String, Object> result = new HashMap<>();
        result.put("orderbooks", orderbooks);
        result.put("currentHeight", currentHeight);
        result.put("events", events);

        File file = new File("dataset_orderbooks_recovered.raw");
        Files.write(JSON.toJSONBytes(result), file);
    }

    @Data
    public static class OrderBooksData {
        private Map<String, List<OrderModel>> orderbooks;
        private Long currentHeight;
        private List<BlockEvent> events;
    }

    @Test
    public void testMarketMiningFromFile() throws Exception {
        long prevTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).parse("2021-10-11 12:00:00", new ParsePosition(0))
                .getTime() / 1000;
        long endTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).parse("2021-10-12 12:00:00", new ParsePosition(0))
                .getTime() / 1000;

        // unserialize
        OrderBooksData data = JSONObject.parseObject(new FileInputStream(new File("dataset_orderbooks_recovered.raw")),
                OrderBooksData.class);
        Map<String, List<OrderModel>> books = data.getOrderbooks();
        Long currentHeight = data.getCurrentHeight();
        List<BlockEvent> blockEvents = data.getEvents();

        OrderBooks recoveOrderBooks = new OrderBooks(viteCli);
        recoveOrderBooks.initFrom(books, currentHeight);
        BlockEventStream eventStream = new BlockEventStream(blockEvents.get(0).getHeight(),
                blockEvents.get(blockEvents.size() - 1).getHeight(), blockEvents);

        // calc rewards
        RewardKeeper rewardKeeper = new RewardKeeper(viteCli);
        int cycleKey = viteCli.getCurrentCycleKey() - 1;
        BigDecimal totalReleasedViteAmount = CommonUtils.getVxAmountByCycleKey(cycleKey);
        FinalResult finalRes = rewardKeeper.calcAddressMarketReward(recoveOrderBooks, eventStream,
                totalReleasedViteAmount, prevTime, endTime);
        log.info("succeed to calc each address`s market mining rewards and invite mining rewards, the result {}",
                finalRes.getOrderMiningFinalRes());
        settleService.saveMiningRewards(finalRes, totalReleasedViteAmount, cycleKey);
    }

    @Test
    public void testMarketMining() throws Exception {
        // long snapshotTime = CommonUtils.getFixedSnapshotTime();
        long snapshotTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"))
                .parse("2021-10-12 12:30:00", new ParsePosition(0)).getTime() / 1000;
        long prevTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).parse("2021-10-11 12:00:00", new ParsePosition(0))
                .getTime() / 1000;
        long endTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).parse("2021-10-12 12:00:00", new ParsePosition(0))
                .getTime() / 1000;

        Traveller traveller = new Traveller();
        Tokens tokens = viteCli.getAllTokenInfos();
        List<TradePair> tradePairs = CommonUtils.getMarketMiningTradePairs();

        // 1.travel to snapshot time
        OrderBooks snapshotOrderBooks = traveller.travelInTime(snapshotTime, tokens, viteCli, tradePairs);
        log.info("travel to checkpoint successfully, the snapshotOrderBooks`s currentHeight is {}",
                snapshotOrderBooks.getCurrentHeight());

        // 2.recover orderbooks
        TradeRecover tradeRecover = new TradeRecover();
        TradeRecover.RecoverResult recoverResult = tradeRecover.recoverInTime(snapshotOrderBooks, prevTime, tokens,
                viteCli);
        OrderBooks recoveredOrderBooks = recoverResult.getOrderBooks();
        BlockEventStream stream = recoverResult.getStream();
        log.info(
                "recovered to cycle`s startTime successfully, the recoveredOrderBooks`s currentHeight is {},the eventStream startHeight {} and endHeight {}",
                recoveredOrderBooks.getCurrentHeight(), stream.getStartHeight(), stream.getEndHeight());
        stream.patchTimestampToOrderEvent(viteCli);
        tradeRecover.fillAddressForOrdersGroupByTimeUnit(recoveredOrderBooks.getBooks(), viteCli);

        // 3.market-mining
        int cycleKey = viteCli.getCurrentCycleKey() - 1;
        BigDecimal totalReleasedViteAmount = CommonUtils.getVxAmountByCycleKey(cycleKey);

        RewardKeeper rewardKeeper = new RewardKeeper(viteCli);
        FinalResult finalRes = rewardKeeper.calcAddressMarketReward(recoveredOrderBooks, stream,
                totalReleasedViteAmount, prevTime, endTime);
        log.info("succeed to calc each address`s market mining rewards, the result {}", finalRes);

        // 4.write reward results to DB and FundContract chain
        settleService.saveMiningRewards(finalRes, totalReleasedViteAmount, cycleKey);
    }

    @Test
    public void testEstimateMarketMining() throws Exception {
        // long snapshotTime = CommonUtils.getFixedSnapshotTime();
        long snapshotTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"))
                .parse("2021-10-11 14:20:00", new ParsePosition(0)).getTime() / 1000;
        long startTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"))
                .parse("2021-10-11 12:00:00", new ParsePosition(0)).getTime() / 1000;

        Traveller traveller = new Traveller();
        List<TradePair> tradePairs = CommonUtils.getMarketMiningTradePairs();
        Tokens tokens = viteCli.getAllTokenInfos();
        // 1.travel to snapshot time
        OrderBooks snapshotOrderBooks = traveller.travelInTime(snapshotTime, tokens, viteCli, tradePairs);

        // 2.recover orderbooks
        TradeRecover tradeRecover = new TradeRecover();
        RecoverResult res = tradeRecover.recoverInTime(snapshotOrderBooks, startTime, tokens, viteCli);
        OrderBooks recovedOrderBooks = res.getOrderBooks();
        BlockEventStream eventStream = res.getStream();
        eventStream.patchTimestampToOrderEvent(viteCli);
        tradeRecover.fillAddressForOrdersGroupByTimeUnit(recovedOrderBooks.getBooks(), viteCli);

        // 3.market-mining
        RewardKeeper rewardKeeper = new RewardKeeper(viteCli);
        int cycleKey = viteCli.getCurrentCycleKey();
        BigDecimal totalReleasedViteAmount = CommonUtils.getVxAmountByCycleKey(cycleKey);
        FinalResult finalRes = rewardKeeper.calcAddressMarketReward(recovedOrderBooks, eventStream,
                totalReleasedViteAmount, startTime, snapshotTime);
        settleService.saveOrderMiningEstimateRes(finalRes.getOrderMiningFinalRes(), cycleKey);
        System.out.println("the task is end");
    }

    @Test
    public void testEstimateMarketMiningFromFile() throws Exception {
        long prevTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).parse("2019-10-02 12:00:00", new ParsePosition(0))
                .getTime() / 1000;
        long endTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).parse("2019-10-03 12:00:00", new ParsePosition(0))
                .getTime() / 1000;

        // unserialize
        OrderBooksData data = JSONObject.parseObject(new FileInputStream(new File("dataset_orderbooks_recovered.raw")),
                OrderBooksData.class);
        Map<String, List<OrderModel>> books = data.getOrderbooks();
        Long currentHeight = data.getCurrentHeight();
        List<BlockEvent> blockEvents = data.getEvents();

        OrderBooks recoveOrderBooks = new OrderBooks(viteCli);
        recoveOrderBooks.initFrom(books, currentHeight);
        BlockEventStream eventStream = new BlockEventStream(blockEvents.get(0).getHeight(),
                blockEvents.get(blockEvents.size() - 1).getHeight(), blockEvents);

        // calc rewards
        RewardKeeper rewardKeeper = new RewardKeeper(viteCli);
        int cycleKey = viteCli.getCurrentCycleKey();
        BigDecimal totalReleasedViteAmount = CommonUtils.getVxAmountByCycleKey(cycleKey);
        FinalResult finalRes = rewardKeeper.calcAddressMarketReward(recoveOrderBooks, eventStream,
                totalReleasedViteAmount, prevTime, endTime);
        log.info("succeed to calc each address`s market mining rewards, the result {}",
                finalRes.getOrderMiningFinalRes());

        SettleService settleService = new SettleService(viteCli);
        settleService.saveOrderMiningEstimateRes(finalRes.getOrderMiningFinalRes(), cycleKey);
    }

    @Test
    public void testGetSnapshotBlockByHeight() throws Exception {
        List<AccountBlock> a = viteCli.getAccountBlocksByHeightRange(1000, 1000);
        System.out.println(a);
    }

    @Test
    public void testFindAddressForOrder() throws Exception {
        List<OrderModel> l = new ArrayList<>();
        OrderModel order = new OrderModel();
        String orderId = "00002601000000000000005217a000615abc36000e61";
        order.setOrderId(orderId);
        byte[] orderBytes = Hex.decodeHex(orderId);
        order.setTimestamp(ViteDataDecodeUtils.getOrderCTimeByParseOrderId(orderBytes));
        l.add(order);

        TradeRecover tradeRecover = new TradeRecover();
        tradeRecover.fillAddressForOrders(l, viteCli);
    }

    @Test
    public void testOrderSideByDecomposeOrderId() throws IOException {
        String sellOrder = "000003010000000c4ddf84758000006112302c000032";
        byte[] orderBytes = sellOrder.getBytes(StandardCharsets.UTF_8);
        System.out.println(ViteDataDecodeUtils.getOrderSideByParseOrderId(orderBytes));
    }

    @Test
    public void testCTimeByDecomposeOrderId() throws IOException, DecoderException {
        String sellOrder = "000003010000000c4ddf84758000006112302c000032";
        byte[] orderBytes = Hex.decodeHex(sellOrder);
        System.out.println(ViteDataDecodeUtils.getOrderCTimeByParseOrderId(orderBytes));
    }

    @Test
    public void testPriceByDecomposeOrderId() throws IOException, DecoderException {
        String orderId = "00000300ffffffff4fed5fa0dfff005d94f2bd000014";
        byte[] orderBytes = Hex.decodeHex(orderId);
        System.out.println(ViteDataDecodeUtils.getPriceByParseOrderId(orderBytes));
    }

    @Test
    public void testPriceByParseLog() throws IOException, DecoderException {
        String orderId = "00000300ffffffff4fed5fa0dfff005d94f2bd000014";
        String priceStr = "00000000b012a05f2000";
        byte[] orderBytes = Hex.decodeHex(orderId);
        byte[] priceBytes = Hex.decodeHex(priceStr);
        System.out.println("parseLog: " + BytesUtils.priceToBigDecimal(priceBytes));
        System.out.println("parseOrderId : " + ViteDataDecodeUtils.getPriceByParseOrderId(orderBytes));
    }

    @Test
    public void testTradeInfo() throws IOException {
        List<TokenInfo> tokenInfoList = viteCli.getTokenInfoList(0, 1000);
        System.out.println(tokenInfoList);
    }

    @Test
    public void testTradeInfoById() throws IOException {
        String tokenId = "tti_687d8a93915393b219212c73";
        TokenInfo tokenInfoList = viteCli.getTokenInfo(tokenId);
        System.out.println(tokenInfoList);
    }

    @Test
    public void testGetSnapshotBlockBeforeTime() throws IOException {
        long beforeTime = System.currentTimeMillis() / 1000 - 10 * 60;
        SnapshotBlock snapshotBlockBeforeTime = viteCli.getSnapshotBlockBeforeTime(beforeTime);
        System.out.println(snapshotBlockBeforeTime);
    }

    @Test
    public void testGetReward() throws IOException {
        long beforeTime = System.currentTimeMillis() / 1000 - 10 * 60;
        SnapshotBlock snapshotBlockBeforeTime = viteCli.getSnapshotBlockBeforeTime(beforeTime);
        System.out.println(snapshotBlockBeforeTime);
    }

    @Test
    public void testGetAllOrderBook() throws IOException {
        String tradeTokenId = "tti_687d8a93915393b219212c73";
        String quoteTokenId = "tti_80f3751485e4e83456059473";
        OrderBookInfo orderBookInfo = viteCli.getOrdersFromMarket(tradeTokenId, quoteTokenId, 0, 100, 0, 100);
        System.out.println(orderBookInfo.getCurrOrders());
    }

    @Test
    public void testHttpReqUtils() throws IOException {
        List<TradePair> tradePairs = CommonUtils.getMarketMiningTradePairs();
        System.out.println(tradePairs);
    }

    @Test
    public void testGetCurrCycleKey() throws IOException {
        int cycleKey = viteCli.getCurrentCycleKey();
        System.out.println(cycleKey);
    }

    @Test
    public void testSaveDB() throws IOException {
        MiningAddressQuoteToken s = new MiningAddressQuoteToken();
        s.setAddress("12345");
        s.setAmount(new BigDecimal("1234.4556643"));
        s.setCycleKey(336);
        s.setQuoteTokenType(2);
        s.setFactorRatio(new BigDecimal("0.4556643"));
        s.setCtime(new Date());
        s.setUtime(new Date());
        addressMarketRewardRepository.save(s);
    }

    @Test
    public void testStartCycleIdx() throws Exception {
        String dbtime1 = "2019-12-06";
        String dbtime2 = "2021-09-15";
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        Date date1 = format.parse(dbtime1);
        Date date2 = format.parse(dbtime2);
        int a = (int) ((date2.getTime() - date1.getTime()) / (1000 * 3600 * 24));
        System.out.println("multi: " + a);
    }

    @Test
    public void testInvitee2InviterMap() throws Exception {
        List<String> l = Arrays.asList("vite_1934ae0ed27d135ae08a252bb3484824097497c82532b358ff",
                "vite_2dcdd77a40162c2d5d954691be68bb2cf335124a0752d10ee2");
        Map<String, String> res = viteCli.getInvitee2InviterMap(l);
        System.out.println(res);
    }
}
