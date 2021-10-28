package org.vite.dex.mm.reward;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.vite.dex.mm.config.MiningConfiguration;
import org.vite.dex.mm.entity.CycleKeyRecord;
import org.vite.dex.mm.entity.TradePair;
import org.vite.dex.mm.orderbook.BlockEventStream;
import org.vite.dex.mm.orderbook.OrderBooks;
import org.vite.dex.mm.orderbook.Tokens;
import org.vite.dex.mm.orderbook.TradeRecover;
import org.vite.dex.mm.orderbook.Traveller;
import org.vite.dex.mm.reward.RewardKeeper.FinalResult;
import org.vite.dex.mm.utils.CommonUtils;
import org.vite.dex.mm.utils.client.ViteCli;

import java.math.BigDecimal;
import java.util.List;

/**
 * the core engine to calc marketMining reward
 */
@Slf4j
@Component
public class RewardEngine {
	@Autowired
	private ViteCli viteCli;

	@Autowired
	private MiningConfiguration miningConfig;

	@Autowired
	private SettleService settleService;

	/**
	 * run at 13:00 O`clock everyday and calc mining reward of each address during
	 * last cycle
	 *
	 * @param prevTime yesterday 12:00 p.m.
	 * @param endTime  today 12:00 p.m.
	 * @throws Exception
	 */
	@Scheduled(cron = "0 0 13 * * ?", zone = "Asia/Singapore")
	public void runDaily() throws Exception {
		int cycleKey = viteCli.getCurrentCycleKey();
		List<CycleKeyRecord> records = settleService.getCycleKeyRecords(cycleKey);
		if (!records.isEmpty()) {
			log.warn("the reward calc of cycleKey {} has been triggered", cycleKey);
			return;
		}
		settleService.addCycleKeyRecord(cycleKey);
		log.info("the runDaily reward calc is start! The cycleKey is: {}", cycleKey);
		
		long endTime = CommonUtils.getTimestampByCyclekey(cycleKey);
		long snapshotTime = endTime + 1800;
		long prevTime = endTime - 86400;
		Traveller traveller = new Traveller();
		Tokens tokens = viteCli.getAllTokenInfos();
		List<TradePair> tradePairs = CommonUtils
				.getMarketMiningTradePairs(miningConfig.getTradePairSettingUrl());

		// 1.travel to snapshot time
		OrderBooks snapshotOrderBooks = traveller.travelInTime(snapshotTime, tokens, viteCli, tradePairs);
		log.info("travel to checkpoint successfully, the snapshotOrderBooks`s currentHeight is {}",
				snapshotOrderBooks.getCurrentHeight());

		// 2.recover orderbooks
		TradeRecover tradeRecover = new TradeRecover();
		TradeRecover.RecoverResult recoverResult = tradeRecover.recoverInTime(snapshotOrderBooks, prevTime,
				tokens, viteCli);
		OrderBooks recoveredOrderBooks = recoverResult.getOrderBooks();
		BlockEventStream stream = recoverResult.getStream();
		stream.patchTimestampToOrderEvent(viteCli);
		tradeRecover.fillAddressForOrdersGroupByTimeUnit(recoveredOrderBooks.getBooks(), viteCli);
		log.info(
				"recovered to cycle`s startTime successfully, the recoveredOrderBooks`s currentHeight is {},the eventStream startHeight {} and endHeight {}",
				recoveredOrderBooks.getCurrentHeight(), stream.getStartHeight(), stream.getEndHeight());

		// 3.market-mining
		BigDecimal totalReleasedVxAmount = CommonUtils.getVxAmountByCycleKey(cycleKey - 1);
		RewardKeeper rewardKeeper = new RewardKeeper(viteCli, miningConfig);
		FinalResult finalRes = rewardKeeper.calcAddressMarketReward(recoveredOrderBooks, stream,
				totalReleasedVxAmount, prevTime, endTime);
		log.info("succeed to calc each address`s market mining rewards, the result {}", finalRes);

		// 4. save reward results to DB
		settleService.saveMiningRewards(finalRes, totalReleasedVxAmount, cycleKey - 1);
	}

	/**
	 * run every half hour and estimate mining reward of each address during this
	 * cycle (not the last cycle but this current ones)
	 *
	 * @param estimateNodeTime
	 * @throws Exception
	 */
	@Scheduled(cron = "0 20,50 * * * ?", zone = "Asia/Singapore")
	public void runHalfHour() throws Exception {
		int cycleKey = viteCli.getCurrentCycleKey();
		log.info("the runHalfHour scheduler has been started! The cycleKey is: {}", cycleKey);
		long startTime = CommonUtils.getTimestampByCyclekey(cycleKey);
		long estimateTime = System.currentTimeMillis() / 1000;
		if (estimateTime < startTime) {
			startTime = startTime - 86400;
		}
		long snapshotTime = estimateTime - 10 * 60;

		Traveller traveller = new Traveller();
		Tokens tokens = viteCli.getAllTokenInfos();
		List<TradePair> tradePairs = CommonUtils
				.getMarketMiningTradePairs(miningConfig.getTradePairSettingUrl());

		// 1.travel to 10 minutes age
		OrderBooks snapshotOrderBooks = traveller.travelInTime(snapshotTime, tokens, viteCli, tradePairs);
		log.info("travel to checkpoint successfully, the snapshotOrderBooks`s currentHeight is {}",
				snapshotOrderBooks.getCurrentHeight());

		// 2.recover orderbooks
		TradeRecover tradeRecover = new TradeRecover();
		TradeRecover.RecoverResult recoverResult = tradeRecover.recoverInTime(snapshotOrderBooks, startTime,
				tokens, viteCli);
		OrderBooks recoveredOrderBooks = recoverResult.getOrderBooks();
		BlockEventStream stream = recoverResult.getStream();
		stream.patchTimestampToOrderEvent(viteCli);
		tradeRecover.fillAddressForOrdersGroupByTimeUnit(recoveredOrderBooks.getBooks(), viteCli);
		log.info(
				"recovered to cycle`s startTime successfully, the recoveredOrderBooks`s currentHeight is {},the eventStream startHeight {} and endHeight {}",
				recoveredOrderBooks.getCurrentHeight(), stream.getStartHeight(), stream.getEndHeight());

		// 3.market-mining
		RewardKeeper rewardKeeper = new RewardKeeper(viteCli, miningConfig);

		BigDecimal totalReleasedVxAmount = CommonUtils.getVxAmountByCycleKey(cycleKey);
		FinalResult finalRes = rewardKeeper.calcAddressMarketReward(recoveredOrderBooks, stream,
				totalReleasedVxAmount, startTime, estimateTime);
		log.info("succeed to calc each address`s market mining rewards, the result {}", finalRes);

		// 4.save estimated result to DB
		settleService.saveOrderMiningEstimateRes(finalRes.getOrderMiningFinalRes(), cycleKey);
	}
}
