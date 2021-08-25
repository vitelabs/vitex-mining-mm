package org.vite.data.dex;

import com.alibaba.fastjson.JSON;
import com.google.common.io.Files;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.vite.dex.mm.DexApplication;
import org.vite.dex.mm.entity.OrderEvent;
import org.vite.dex.mm.entity.OrderModel;
import org.vite.dex.mm.orderbook.TradeRecover;
import org.vite.dex.mm.reward.RewardKeeper;
import org.vite.dex.mm.utils.ViteDataDecodeUtils;
import org.vite.dex.mm.utils.client.ViteCli;
import org.vitej.core.protocol.methods.response.SnapshotBlock;
import org.vitej.core.protocol.methods.response.TokenInfo;
import org.vitej.core.protocol.methods.response.VmLogInfo;

import javax.annotation.Resource;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootTest(classes = DexApplication.class)
class DexApplicationTests {

    @Resource
    private ViteCli viteCli;

    @Test
    void contextLoads() {}

    @Test
    public void testOrderBooks() throws Exception {
        TradeRecover tradeRecover = new TradeRecover(viteCli);
        tradeRecover.prepareData();
        tradeRecover.prepareOrderBooks();
    }

    @Test
    public void testOrderBooks_get() throws Exception {
        TradeRecover tradeRecover = new TradeRecover(viteCli);
        List<OrderModel> first = tradeRecover.getSingleSideOrders("tti_687d8a93915393b219212c73",
                "tti_80f3751485e4e83456059473", true, 1000);
        List<OrderModel> two = tradeRecover.getSingleSideOrders("tti_687d8a93915393b219212c73",
                "tti_80f3751485e4e83456059473", false, 1000);
        System.out.println(first.size());
        System.out.println(two.size());
    }

    @Test
    public void testEvents() throws Exception {
        TradeRecover tradeRecover = new TradeRecover(viteCli);
        long startTime = System.currentTimeMillis() / 1000 - 60 * 5;
        tradeRecover.prepareEvents(startTime);
    }

    @Test
    public void testRevertOrderBooks() throws Exception {
        TradeRecover tradeRecover = new TradeRecover(viteCli);
        long startTime = System.currentTimeMillis() / 1000 - 240 * 60;
        tradeRecover.prepareData();
        tradeRecover.prepareOrderBooks();
        tradeRecover.prepareEvents(startTime);
        tradeRecover.filterEvents();
        tradeRecover.revertOrderBooks();
    }

    // test reward result
    @Test
    public void testRewardResult() throws Exception {
        TradeRecover tradeRecover = new TradeRecover(viteCli);
        RewardKeeper rewardKeeper = new RewardKeeper(tradeRecover);
        long startTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"))
                .parse("2019-10-02 12:00:00", new ParsePosition(0)).getTime() / 1000;
        long endTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).parse("2019-10-03 12:30:00", new ParsePosition(0))
                .getTime() / 1000;

        tradeRecover.prepareData();
        tradeRecover.prepareOrderBooks();
        tradeRecover.prepareEvents(startTime);
        tradeRecover.filterEvents();
        tradeRecover.revertOrderBooks();

        double totalReleasedViteAmount = 1000000.0;
        Map<String, Map<Integer, Double>> finalRes = rewardKeeper.calcAddressMarketReward(totalReleasedViteAmount,
                startTime, endTime);
        System.out.println(finalRes);
    }


    @Test
    public void testRewardResult2() throws Exception {
        TradeRecover tradeRecover = new TradeRecover(viteCli);
        long startTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"))
                .parse("2019-10-02 12:00:00", new ParsePosition(0)).getTime() / 1000;
        long endTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).parse("2019-10-03 12:30:00", new ParsePosition(0))
                .getTime() / 1000;

        tradeRecover.prepareData();
        tradeRecover.prepareOrderBooks();
        tradeRecover.prepareEvents(startTime);
        tradeRecover.filterEvents();
        tradeRecover.revertOrderBooks();



        Map<String, Collection<OrderModel>> orderMap = new HashMap<>();
        Map<String, Collection<OrderEvent>> eventMap = new HashMap<>();

        tradeRecover.getOrderBooks().forEach((k, v) -> {
            orderMap.put(k, v.getOrders().values());
        });

        tradeRecover.getEventStreams().forEach((k, v) -> {
            eventMap.put(k, v.getEvents());
        });

        Map<String, Object> result = new HashMap<>();
        result.put("orderbook", orderMap);
        result.put("events", eventMap);


        File file = new File("dataset.raw");
        Files.write(JSON.toJSONBytes(result), file);
    }



    @Test
    public void test() throws IOException {
        List<VmLogInfo> vmlogs = viteCli.getEventsByHeightRange(3798100L, 3798123L, 1000);

        List<OrderEvent> events = new ArrayList<>();
        vmlogs.forEach(vlog -> {
            OrderEvent orderEvent = new OrderEvent(vlog);
            orderEvent.parse();
            events.add(orderEvent);
        });

        System.out.println(JSON.toJSONString(events));
    }

    @Test
    public void testDecomposeOrderId() throws IOException {
        String sellOrder = "000003010000000c4ddf84758000006112302c000032";
        byte[] orderBytes = sellOrder.getBytes(StandardCharsets.UTF_8);
        System.out.println(ViteDataDecodeUtils.getOrderSideByParseOrderId(orderBytes));
    }

    @Test
    public void testDecomposeOrderIdTime() throws IOException, DecoderException {
        String sellOrder = "000003010000000c4ddf84758000006112302c000032";
        byte[] orderBytes = Hex.decodeHex(sellOrder);
        System.out.println(ViteDataDecodeUtils.getOrderCTimeByParseOrderId(orderBytes));
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
    public void testGetOrderSide() throws IOException {
        String tradeTokenId = "tti_687d8a93915393b219212c73";
        String quoteTokenId = "tti_80f3751485e4e83456059473";
        List<OrderModel> orders = viteCli.getOrdersFromMarket(tradeTokenId, quoteTokenId, true, 0, 100);
        System.out.println(orders);
    }
}
