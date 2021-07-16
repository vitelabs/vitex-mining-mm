package org.vite.dex.mm.reward;

import org.vite.dex.mm.orderbook.EventStream;
import org.vite.dex.mm.orderbook.OrderBook;
import org.vite.dex.mm.orderbook.TradeRecover;
import org.vite.dex.mm.reward.bean.MiningRewardCfg;
import org.vite.dex.mm.reward.bean.RewardOrder;

import java.util.HashMap;
import java.util.Map;

public class RewardKeeper {
	private TradeRecover tradeRecover;

	// orderId -> reward
	private Map<String, RewardOrder> orderRewards;


	public Map<String, RewardOrder> mmMining(EventStream eventStream, OrderBook orderBook, MiningRewardCfg cfg) {
		return new HashMap<String, RewardOrder>();
	}

	public void start(long start, long end) {

	}
}
