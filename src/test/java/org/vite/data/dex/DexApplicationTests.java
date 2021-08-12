package org.vite.data.dex;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.vite.dex.mm.DexApplication;
import org.vite.dex.mm.entity.SnapshotBlock;
import org.vite.dex.mm.entity.TradePair;
import org.vite.dex.mm.orderbook.EventStream;
import org.vite.dex.mm.orderbook.OrderBook;
import org.vite.dex.mm.orderbook.TradeRecover;
import org.vite.dex.mm.reward.RewardKeeper;
import org.vite.dex.mm.reward.bean.RewardOrder;
import org.vite.dex.mm.reward.cfg.MiningRewardCfg;
import org.vite.dex.mm.utils.ApiCollectionUtils;
import org.vite.dex.mm.utils.ViteDataDecodeUtils;
import org.vite.dex.mm.utils.client.ViteCli;
import org.vitej.core.protocol.methods.response.CommonResponse;
import org.vitej.core.protocol.methods.response.TokenInfo;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@SpringBootTest(classes = DexApplication.class)
class DexApplicationTests {

    @Resource
    private ViteCli viteCli;

    @Test
    void contextLoads() {
    }

    // test yubikey
    @Test
    public void testOrderBooks() throws IOException {
        TradeRecover tradeRecover = new TradeRecover(viteCli);
        tradeRecover.prepareOrderBooks();
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
        long startTime = System.currentTimeMillis() / 1000 - 24 * 60;
        tradeRecover.prepareData();
        tradeRecover.prepareOrderBooks();
        tradeRecover.prepareEvents(startTime);
        tradeRecover.filterEvents();
        tradeRecover.revertOrderBooks();
    }

    // test orderbook revert and onward
    @Test
    public void testOnward() throws Exception {
        TradeRecover tradeRecover = new TradeRecover(viteCli);
        RewardKeeper rewardKeeper = new RewardKeeper(tradeRecover);
        long startTime = System.currentTimeMillis() / 1000 - 120 * 60;
        long endTime = System.currentTimeMillis() / 1000;
        tradeRecover.prepareOrderBooks();
        tradeRecover.prepareEvents(startTime);
        tradeRecover.filterEvents();
        tradeRecover.revertOrderBooks();
        for (TradePair tp : TradeRecover.getMMOpenedTradePairs()) {
            String tradePairSymbol = tp.getTradePairSymbol();
            OrderBook orderBook = tradeRecover.getOrderBooks().get(tradePairSymbol);
            EventStream eventStream = tradeRecover.getEventStreams().get(tradePairSymbol);
            MiningRewardCfg miningRewardCfg = new MiningRewardCfg();
            miningRewardCfg.setMarketId(tp.getMarket());
            miningRewardCfg.setEffectiveDistance(tp.getMmEffectiveInterval());
            Map<String, RewardOrder> rewardOrders = rewardKeeper.mmMining(eventStream, orderBook,
                    miningRewardCfg, startTime, endTime);
            System.out.println(rewardOrders);
        }
    }

    // test reward result
    @Test
    public void testRewardResult() throws IOException {
        TradeRecover tradeRecover = new TradeRecover(viteCli);
        RewardKeeper rewardKeeper = new RewardKeeper(tradeRecover);
        double totalReleasedViteAmount = 1000000.0;
        long startTime = System.currentTimeMillis() / 1000 - 120 * 60;
        long endTime = System.currentTimeMillis() / 1000;
        Map<String, Map<Integer, Double>> finalRes =
                rewardKeeper.calculateAddressReward(totalReleasedViteAmount, startTime, endTime);
        System.out.println("the final result is:" + finalRes);
    }

    @Test
    public void testDecomposeOrderId() throws IOException {
        String sellOrder = "000003010000000c4ddf84758000006112302c000032";
        byte[] orderBytes = sellOrder.getBytes(StandardCharsets.UTF_8);
        System.out.println(ViteDataDecodeUtils.getOrderSideByParseOrderId(orderBytes));
    }

    @Test
    public void testTradeInfo() throws IOException {
        List<TokenInfo> tokenInfoList = viteCli.getTokenInfoList(0, 1000);
        System.out.println(tokenInfoList);
    }

    @Test
    public void testTradeInfoById() throws IOException {
//        String tokenId = "tti_687d8a93915393b219212c73";
//        TokenInfo tokenInfoList = viteCli.getTokenInfo(tokenId);
//        System.out.println(tokenInfoList);

//        String num = "422456348.83328";
//        String s = BigDecimalUtils.priceFormat(num).toPlainString();
//        System.out.println(s);
        long beforeTime = System.currentTimeMillis() / 1000 - 10 * 60;
        CommonResponse resp = viteCli.getSnapshotBlockBeforeTime(beforeTime);
        System.out.println(resp);
    }

    @Test
    public void testRPC() throws Exception {
        long beforeTime = System.currentTimeMillis() / 1000 - 10 * 60;
        SnapshotBlock snapshotBlockBeforeTime = ApiCollectionUtils.getSnapshotBlockBeforeTime(beforeTime);
        System.out.println(snapshotBlockBeforeTime);
    }
}
