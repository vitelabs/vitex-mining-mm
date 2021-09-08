package org.vite.data.dex;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.io.Files;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.vite.dex.mm.DexApplication;
import org.vite.dex.mm.entity.BlockEvent;
import org.vite.dex.mm.entity.OrderBookInfo;
import org.vite.dex.mm.entity.OrderModel;
import org.vite.dex.mm.entity.TradePair;
import org.vite.dex.mm.orderbook.BlockEventStream;
import org.vite.dex.mm.orderbook.OrderBooks;
import org.vite.dex.mm.orderbook.Tokens;
import org.vite.dex.mm.orderbook.TradeRecover;
import org.vite.dex.mm.orderbook.TradeRecover.RecoverResult;
import org.vite.dex.mm.orderbook.Traveller;
import org.vite.dex.mm.reward.RewardKeeper;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootTest(classes = DexApplication.class)
@Slf4j
class DexApplicationTests {

    @Resource
    private ViteCli viteCli;

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
        List<TradePair> tradePairs = viteCli.getMarketMiningTradePairs();
        Tokens tokens = viteCli.getAllTokenInfos();

        long snapshotTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"))
                .parse("2019-10-03 12:30:00", new ParsePosition(0)).getTime() / 1000;

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
                .parse("2019-10-02 12:00:00", new ParsePosition(0)).getTime() / 1000;
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
        TradeRecover tradeRecover = new TradeRecover();
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
        double totalReleasedViteAmount = 1000000.0;
        Map<String, Map<Integer, BigDecimal>> finalRes = rewardKeeper.calcAddressMarketReward(recoveOrderBooks,
                eventStream, totalReleasedViteAmount, prevTime, endTime, tradeRecover.miningRewardCfgMap(viteCli));
        log.info("succeed to calc each address`s market mining rewards, the result {}", finalRes);
    }

    @Test
    public void testMarketMining() throws Exception {
        Traveller traveller = new Traveller();
        TradeRecover tradeRecover = new TradeRecover();

        // long snapshotTime = CommonUtils.getFixedTime();
        long snapshotTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"))
                .parse("2019-10-03 12:30:00", new ParsePosition(0)).getTime() / 1000;
        long prevTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).parse("2019-10-02 12:00:00", new ParsePosition(0))
                .getTime() / 1000;
        long endTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).parse("2019-10-03 12:00:00", new ParsePosition(0))
                .getTime() / 1000;

        List<TradePair> tradePairs = viteCli.getMarketMiningTradePairs();
        Tokens tokens = viteCli.getAllTokenInfos();
        // 1.travel to snapshot time
        OrderBooks snapshotOrderBooks = traveller.travelInTime(snapshotTime, tokens, viteCli, tradePairs);

        // 2.recover orderbooks
        RecoverResult res = tradeRecover.recoverInTime(snapshotOrderBooks, prevTime, tokens, viteCli);
        OrderBooks recovedOrderBooks = res.getOrderBooks();
        BlockEventStream eventStream = res.getStream();
        tradeRecover.fillAddressForOrdersGroupByTimeUnit(recovedOrderBooks.getBooks(), viteCli);

        // 3.market-mining
        RewardKeeper rewardKeeper = new RewardKeeper(viteCli);
        double totalReleasedViteAmount = 1000000.0;
        Map<String, Map<Integer, BigDecimal>> finalRes = rewardKeeper.calcAddressMarketReward(recovedOrderBooks,
                eventStream, totalReleasedViteAmount, prevTime, endTime, tradeRecover.miningRewardCfgMap(viteCli));
        System.out.println(finalRes);
    }

    @Test
    public void testGetSnapshotBlockByHeight() throws Exception {
        List<AccountBlock> a = viteCli.getAccountBlocksByHeightRange(1000, 1000);
        System.out.println(a);
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
}
