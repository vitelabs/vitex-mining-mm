package org.vite.data.dex;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.vite.dex.mm.DexApplication;
import org.vite.dex.mm.entity.TradePair;
import org.vite.dex.mm.orderbook.EventStream;
import org.vite.dex.mm.orderbook.OrderBook;
import org.vite.dex.mm.orderbook.TradeRecover;
import org.vite.dex.mm.reward.RewardKeeper;
import org.vite.dex.mm.reward.bean.RewardOrder;
import org.vite.dex.mm.reward.cfg.MiningRewardCfg;
import org.vite.dex.mm.utils.ViteDataDecodeUtils;
import org.vite.dex.mm.utils.client.ViteCli;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
    public void testEvents() throws IOException {
        TradeRecover tradeRecover = new TradeRecover(viteCli);
        long startTime = System.currentTimeMillis() / 1000 - 60 * 5;
        tradeRecover.prepareEvents(startTime);
    }

    @Test
    public void testRevertOrderBooks() throws IOException {
        TradeRecover tradeRecover = new TradeRecover(viteCli);
        long startTime = System.currentTimeMillis() / 1000 - 240 * 60;
        tradeRecover.prepareOrderBooks();
        tradeRecover.prepareEvents(startTime);
        tradeRecover.filterEvents();
        tradeRecover.revertOrderBooks();
    }

    // test orderbook revert and onward
    @Test
    public void testOnward() throws IOException {
        TradeRecover tradeRecover = new TradeRecover(viteCli);
        RewardKeeper rewardKeeper = new RewardKeeper(tradeRecover);
        long startTime = System.currentTimeMillis() / 1000 - 120 * 60;
        long endTime = System.currentTimeMillis() / 1000;
        tradeRecover.prepareOrderBooks();
        tradeRecover.prepareEvents(startTime);
        tradeRecover.filterEvents();
        tradeRecover.revertOrderBooks();
        for (TradePair tp : TradeRecover.getAllTradePairs()) {
            String tradePairSymbol = tp.getTradePairSymbol();
            OrderBook orderBook = tradeRecover.getOrderBooks().get(tradePairSymbol);
            EventStream eventStream = tradeRecover.getEventStreams().get(tradePairSymbol);
            MiningRewardCfg miningRewardCfg = new MiningRewardCfg();
            miningRewardCfg.setMarketId(tp.getMarket());
            miningRewardCfg.setEffectiveDistance(tp.getEffectiveInterval());
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
}
