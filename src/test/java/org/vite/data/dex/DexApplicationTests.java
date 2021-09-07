package org.vite.data.dex;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.io.Files;
import lombok.Data;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.vite.dex.mm.DexApplication;
import org.vite.dex.mm.entity.OrderBookInfo;
import org.vite.dex.mm.entity.OrderEvent;
import org.vite.dex.mm.entity.OrderModel;
import org.vite.dex.mm.entity.TradePair;
import org.vite.dex.mm.orderbook.OrderBooks;
import org.vite.dex.mm.orderbook.Tokens;
import org.vite.dex.mm.orderbook.TradeRecover;
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
class DexApplicationTests {

    @Resource
    private ViteCli viteCli;

    @Test
    void contextLoads() {
    }

    @Test
    public void testGetOrderBooks() throws Exception {
        TradeRecover tradeRecover = new TradeRecover(viteCli);
        OrderBookInfo orderBook = tradeRecover.getOrdersFromMarket("tti_687d8a93915393b219212c73",
                "tti_80f3751485e4e83456059473", 1000);

        orderBook.getCurrOrders().forEach(t -> {
            System.out.println(t.getOrderId());
        });
    }

    @Test
    public void testTraveller() throws Exception {
        Traveller traveller = new Traveller();
        TradeRecover tradeRecover = new TradeRecover(viteCli);

        // long snapshotTime = CommonUtils.getFixedTime();
        long snapshotTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"))
                .parse("2019-10-03 12:30:00", new ParsePosition(0)).getTime() / 1000;
        List<TradePair> tradePairs = TradeRecover.getMarketMiningOpenedTp();
        Tokens tokens = tradeRecover.prepareData();
        OrderBooks snapshotOrderBooks = traveller.travelInTime(snapshotTime, tokens, viteCli, tradePairs);
        int size = snapshotOrderBooks.getBooks().get("tti_687d8a93915393b219212c73_tti_80f3751485e4e83456059473")
                .getOrders().values().size();
        int buySize = snapshotOrderBooks.getBooks().get("tti_687d8a93915393b219212c73_tti_80f3751485e4e83456059473")
                .getBuys().size();
        int sellSize = snapshotOrderBooks.getBooks().get("tti_687d8a93915393b219212c73_tti_80f3751485e4e83456059473")
                .getSells().size();
        assert (size == 277);
        assert (buySize == 154);
        assert (sellSize == 123);
        System.out.println(snapshotOrderBooks);
    }

    @Test
    public void testMarketMining() throws Exception {
        Traveller traveller = new Traveller();
        TradeRecover tradeRecover = new TradeRecover(viteCli);

        // long snapshotTime = CommonUtils.getFixedTime();
        long snapshotTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"))
                .parse("2019-10-03 12:30:00", new ParsePosition(0)).getTime() / 1000;
        long prevTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).parse("2019-10-02 12:00:00", new ParsePosition(0))
                .getTime() / 1000;
        long endTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).parse("2019-10-03 12:00:00", new ParsePosition(0))
                .getTime() / 1000;
        List<TradePair> tradePairs = TradeRecover.getMarketMiningOpenedTp();
        Tokens tokens = tradeRecover.prepareData();
        // 1.travel to snapshot time
        OrderBooks snapshotOrderBooks = traveller.travelInTime(snapshotTime, tokens, viteCli, tradePairs);

        // 2.recover orderbooks
        OrderBooks orderBooks = tradeRecover.recoverInTime(snapshotOrderBooks, prevTime, tokens, viteCli);
        tradeRecover.fillAddressForOrdersGroupByTimeUnit(orderBooks.getBooks());
        tradeRecover.setOrderBooks(orderBooks.getBooks()); 

        // 3.market-mining
        RewardKeeper rewardKeeper = new RewardKeeper(viteCli);
        double totalReleasedViteAmount = 1000000.0;
        Map<String, Map<Integer, BigDecimal>> finalRes = rewardKeeper.calcAddressMarketReward(orderBooks,
                tradeRecover.getBlockEventStream(), totalReleasedViteAmount, prevTime, endTime,
                tradeRecover.miningRewardCfgMap());
        System.out.println(finalRes);
    }

    @Test
    public void testMarketMining1() throws Exception {
        Traveller traveller = new Traveller();
        TradeRecover tradeRecover = new TradeRecover(viteCli);

        // long snapshotTime = CommonUtils.getFixedTime();
        long snapshotTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"))
                .parse("2019-10-03 12:30:00", new ParsePosition(0)).getTime() / 1000;
        long prevTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).parse("2019-10-02 12:00:00", new ParsePosition(0))
                .getTime() / 1000;
        long endTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).parse("2019-10-03 12:00:00", new ParsePosition(0))
                .getTime() / 1000;
        List<TradePair> tradePairs = TradeRecover.getMarketMiningOpenedTp();
        Tokens tokens = tradeRecover.prepareData();
        // 1.travel to snapshot time
        OrderBooks snapshotOrderBooks = traveller.travelInTime(snapshotTime, tokens, viteCli, tradePairs);

        // 2.recover orderbooks
        OrderBooks orderBooks = tradeRecover.recoverInTime(snapshotOrderBooks, prevTime, tokens, viteCli);
        tradeRecover.fillAddressForOrdersGroupByTimeUnit(orderBooks.getBooks());
        tradeRecover.setOrderBooks(orderBooks.getBooks()); 

        // 3.market-mining
        RewardKeeper rewardKeeper = new RewardKeeper(viteCli);
        double totalReleasedViteAmount = 1000000.0;
        Map<String, Map<Integer, BigDecimal>> finalRes = rewardKeeper.calcAddressMarketReward(orderBooks,
                tradeRecover.getBlockEventStream(), totalReleasedViteAmount, prevTime, endTime,
                tradeRecover.miningRewardCfgMap());
        System.out.println(finalRes);
    }

    @Test
    public void testGetSnapshotBlockByHeight() throws Exception {
        List<AccountBlock> a = viteCli.getAccountBlocksByHeightRange(1000, 1000);
        System.out.println(a);
    }

    @Test
    public void testGetCurrentOrderBookAndEvent() throws Exception {
        TradeRecover tradeRecover = new TradeRecover(viteCli);
        long startTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"))
                .parse("2019-10-02 12:00:00", new ParsePosition(0)).getTime() / 1000;
        // tradeRecover.prepareOrderBooks();
        /*
         * tradeRecover.prepareEvents(startTime); tradeRecover.filterEvents();
         */

        Map<String, Collection<OrderModel>> orderMap = new HashMap<>();
        Map<String, Collection<OrderEvent>> eventMap = new HashMap<>();
        tradeRecover.getOrderBooks().forEach((k, v) -> {
            orderMap.put(k, v.getOrders().values());
        });

        // tradeRecover.getEventStreams().forEach((k, v) -> {
        //     eventMap.put(k, v.getEvents());
        // });

        Map<String, Object> result = new HashMap<>();
        result.put("orderbook", orderMap);
        result.put("events", eventMap);

        File file = new File("dataset_origin.raw");
        Files.write(JSON.toJSONBytes(result), file);
    }

    @Test
    public void testRevertOrderBook() throws Exception {
        TradeRecover tradeRecover = new TradeRecover(viteCli);
        SData data = JSONObject.parseObject(new FileInputStream(new File("dataset_origin.raw")), SData.class);
        Map<String, List<OrderModel>> orders = data.getOrderbook();
        Map<String, List<OrderEvent>> events = data.getEvents();

        // tradeRecover.initFrom(orders, events);
        // tradeRecover.revertOrderBooks();
    }

    @Test
    public void testRevertOrderBookToFile() throws Exception {
        TradeRecover tradeRecover = new TradeRecover(viteCli);
        long startTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"))
                .parse("2019-10-02 12:00:00", new ParsePosition(0)).getTime() / 1000;
        tradeRecover.prepareData();
        // tradeRecover.prepareOrderBooks();
        // tradeRecover.prepareEvents(startTime);
        // tradeRecover.filterEvents();
        // tradeRecover.revertOrderBooks();

        Map<String, Collection<OrderModel>> orderMap = new HashMap<>();
        Map<String, Collection<OrderEvent>> eventMap = new HashMap<>();

        tradeRecover.getOrderBooks().forEach((k, v) -> {
            orderMap.put(k, v.getOrders().values());
        });

        // tradeRecover.getEventStreams().forEach((k, v) -> {
        //     eventMap.put(k, v.getEvents());
        // });

        Map<String, Object> result = new HashMap<>();
        result.put("orderbook", orderMap);
        result.put("events", eventMap);

        File file = new File("dataset_reverted.raw");
        Files.write(JSON.toJSONBytes(result), file);
    }

    @Data
    public static class SData {
        private Map<String, List<OrderModel>> orderbook;
        private Map<String, List<OrderEvent>> events;
    }

    @Test
    public void testRewardResultFromFile() throws Exception {
        TradeRecover tradeRecover = new TradeRecover(viteCli);
        long startTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"))
                .parse("2019-10-02 12:00:00", new ParsePosition(0)).getTime() / 1000;
        long endTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).parse("2019-10-03 12:00:00", new ParsePosition(0))
                .getTime() / 1000;

        SData data = JSONObject.parseObject(new FileInputStream(new File("dataset_reverted.raw")), SData.class);
        Map<String, List<OrderModel>> orders = data.getOrderbook();
        Map<String, List<OrderEvent>> events = data.getEvents();

        // tradeRecover.initFrom(orders, events);

        // RewardKeeper rewardKeeper = new RewardKeeper(tradeRecover);
        // Map<String, Map<Integer, BigDecimal>> finalRes =
        // rewardKeeper.calcAddressMarketReward(1000000.0, startTime,
        // endTime);
        // System.out.println(finalRes);
    }

    // test reward result
    @Test
    public void testRewardResult() throws Exception {
        TradeRecover tradeRecover = new TradeRecover(viteCli);
        RewardKeeper rewardKeeper = new RewardKeeper(viteCli);
        long startTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"))
                .parse("2019-10-02 12:00:00", new ParsePosition(0)).getTime() / 1000;
        long endTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).parse("2019-10-03 12:00:00", new ParsePosition(0))
                .getTime() / 1000;

        /* tradeRecover.prepareOrderBooks();
        tradeRecover.prepareEvents(startTime);
        tradeRecover.filterEvents();
        tradeRecover.revertOrderBooks();

        double totalReleasedViteAmount = 1000000.0;
        Map<String, Map<Integer, BigDecimal>> finalRes = rewardKeeper.calcAddressMarketReward(totalReleasedViteAmount,
                startTime, endTime);
        System.out.println("aaaaa"); */
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
