package org.vite.dex.mm.reward;

import org.vite.dex.mm.entity.TradePair;
import org.vite.dex.mm.orderbook.OrderBooks;
import org.vite.dex.mm.orderbook.Tokens;
import org.vite.dex.mm.orderbook.TradeRecover;
import org.vite.dex.mm.orderbook.Traveller;
import org.vite.dex.mm.utils.CommonUtils;
import org.vite.dex.mm.utils.client.ViteCli;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class RewardEngine {
    private ViteCli viteCli;

    public Map<String, Map<Integer, BigDecimal>> run(long prevTime, long endTime) throws Exception {
        Traveller traveller = new Traveller();
        TradeRecover tradeRecover = new TradeRecover(viteCli);

        long snapshotTime = CommonUtils.getFixedTime();
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
        return finalRes;
    }
}
