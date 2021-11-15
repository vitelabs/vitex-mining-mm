package org.vite.dex.mm.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.vite.dex.mm.config.MiningConfiguration;
import org.vite.dex.mm.constant.consist.MiningConst;
import org.vite.dex.mm.model.pojo.Tokens;
import org.vite.dex.mm.model.pojo.TradePair;
import org.vite.dex.mm.model.pojo.TradePairSetting;
import org.vite.dex.mm.orderbook.BlockEventStream;
import org.vite.dex.mm.orderbook.OrderBooks;
import org.vite.dex.mm.orderbook.TradeRecover;
import org.vite.dex.mm.orderbook.Traveller;
import org.vite.dex.mm.reward.RewardKeeper;
import org.vite.dex.mm.reward.RewardSettler;
import org.vite.dex.mm.reward.RewardKeeper.FinalResult;
import org.vite.dex.mm.utils.CommonUtils;
import org.vite.dex.mm.utils.client.ViteCli;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * the core scheduler engine to calc marketMining reward
 */
@Slf4j
@Service
public class RewardEngine {
	@Autowired
	private ViteCli viteCli;

	@Autowired
	private MiningConfiguration miningConfig;

	@Autowired
	private RewardSettler rewardSettler;

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
		int cycleKey = viteCli.getCurrentCycleKey() - 1;
		boolean firstCalcOfCyclekey = rewardSettler.firstCalcOfCyclekey(cycleKey);
		if (!firstCalcOfCyclekey) {
			return;
		}

		log.info("the runDaily reward calc is start! The cycleKey is: {}", cycleKey);
		long endTime = CommonUtils.getTimestampByCyclekey(cycleKey + 1);
		long snapshotTime = endTime + 1800;
		long prevTime = endTime - 86400;
		Traveller traveller = new Traveller();
		TradeRecover tradeRecover = new TradeRecover();
		Tokens tokens = viteCli.getAllTokenInfos();

		List<TradePair> tradePairs = CommonUtils.getMarketMiningTradePairs(miningConfig.getMetaUrl(), cycleKey);
		OrderBooks recoveredOrderBooks = null;
		BlockEventStream stream = null;

		int retryNum = 3;
		while (retryNum > 0) {
			try {
				// 1.travel to snapshot time
				OrderBooks snapshotOrderBooks = traveller.travelInTime(snapshotTime, tokens, viteCli, tradePairs);
				log.info("travel to checkpoint successfully, the snapshotOrderBooks`s currentHeight is {}",
						snapshotOrderBooks.getCurrentHeight());

				// 2.recover orderbooks
				TradeRecover.RecoverResult recoverResult = tradeRecover.recoverInTime(snapshotOrderBooks, prevTime,
						tokens, viteCli);
				recoveredOrderBooks = recoverResult.getOrderBooks();
				stream = recoverResult.getStream();
				stream.patchTimestampToOrderEvent(viteCli);
				tradeRecover.fillAddressForOrdersGroupByTimeUnit(recoveredOrderBooks.getBooks(), viteCli);
				log.info(
						"recovered to cycle`s startTime successfully, the recoveredOrderBooks`s currentHeight is {},the eventStream startHeight {} and endHeight {}",
						recoveredOrderBooks.getCurrentHeight(), stream.getStartHeight(), stream.getEndHeight());
				break;
			} catch (Exception e) {
				log.error("failed to revert OrderBooks, the err: ", e);
				if (--retryNum == 0) {
					throw new RuntimeException("the runDaily task was failed!");
				}
				TimeUnit.MINUTES.sleep(1);
			}
		}

		// 3.market-mining
		BigDecimal vxMineTotal = viteCli.getVxMineTotalByCyclekey(cycleKey);
		RewardKeeper rewardKeeper = new RewardKeeper(viteCli, miningConfig);
		FinalResult finalRes = rewardKeeper.calcAddressMarketReward(recoveredOrderBooks, stream,
				vxMineTotal, cycleKey, prevTime, endTime);
		log.info("succeed to calc each address`s market mining rewards, the result {}", finalRes);

		// 4. save reward results
		if (miningConfig.getSaveStrategy().equals("DB")) {
            rewardSettler.saveMiningRewards(finalRes, vxMineTotal, cycleKey);
        } else if (miningConfig.getSaveStrategy().equals("CSV")) {
            rewardSettler.saveMiningRewardToCSV(finalRes, vxMineTotal, cycleKey);
        }
		log.info("save mining rewards successfully");
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
		TradeRecover tradeRecover = new TradeRecover();
		Tokens tokens = viteCli.getAllTokenInfos();
		List<TradePair> tradePairs = CommonUtils.getMarketMiningTradePairs(miningConfig.getMetaUrl(), cycleKey);

		OrderBooks recoveredOrderBooks = null;
		BlockEventStream stream = null;
		int retryNum = 3;
		while (retryNum > 0) {
			try {
				// 1.travel to 10 minutes age
				OrderBooks snapshotOrderBooks = traveller.travelInTime(snapshotTime, tokens, viteCli, tradePairs);
				log.info("travel to checkpoint successfully, the snapshotOrderBooks`s currentHeight is {}",
						snapshotOrderBooks.getCurrentHeight());

				// 2.recover orderbooks
				TradeRecover.RecoverResult recoverResult = tradeRecover.recoverInTime(snapshotOrderBooks, startTime,
						tokens, viteCli);
				recoveredOrderBooks = recoverResult.getOrderBooks();
				stream = recoverResult.getStream();
				stream.patchTimestampToOrderEvent(viteCli);
				tradeRecover.fillAddressForOrdersGroupByTimeUnit(recoveredOrderBooks.getBooks(), viteCli);
				log.info(
						"recovered to cycle`s startTime successfully, the recoveredOrderBooks`s currentHeight is {},the eventStream startHeight {} and endHeight {}",
						recoveredOrderBooks.getCurrentHeight(), stream.getStartHeight(), stream.getEndHeight());
				break;
			} catch (Exception e) {
				log.error("failed to revert OrderBooks, the err: ", e);
				if (--retryNum == 0) {
					throw new RuntimeException("the runHalfHour task was failed!");
				}
				TimeUnit.MINUTES.sleep(1);
			}
		}

		// 3.market-mining
		RewardKeeper rewardKeeper = new RewardKeeper(viteCli, miningConfig);
		BigDecimal vxMineTotal = viteCli.getVxMineTotalByCyclekey(cycleKey);
		FinalResult finalRes = rewardKeeper.calcAddressMarketReward(recoveredOrderBooks, stream,
				vxMineTotal, cycleKey, startTime, estimateTime);
		log.info("succeed to calc each address`s market mining rewards, the result {}", finalRes);

		// 4.save estimated result to DB
		rewardSettler.saveOrderMiningEstimateRes(finalRes.getOrderMiningFinalRes(), cycleKey);
		log.info("update estimate rewards successfully");
	}
}
