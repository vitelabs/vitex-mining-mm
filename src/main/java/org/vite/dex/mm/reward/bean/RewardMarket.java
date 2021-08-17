package org.vite.dex.mm.reward.bean;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Data
@Accessors(chain = true)
public class RewardMarket {


	private final int market;
	private final Map<String, RewardTradePair> pairs = new HashMap<>();


	private final List<RewardOrder> orders;

	private double factorSum;

	private double releasedVx;

	public RewardMarket(int market, List<RewardOrder> rewardOrders) {
		this.market = market;
		Map<String, List<RewardOrder>> pairOrders =
				rewardOrders.stream().collect(Collectors.groupingBy(RewardOrder::getTradePair));
		pairOrders.forEach((k, v) -> {
			this.pairs.put(k, new RewardTradePair(k, v));
		});
		this.orders = rewardOrders;
	}

	public void apply(double releasedVx, double f) {
		double sharedVX = releasedVx * f;
		this.factorSum = this.orders.stream().mapToDouble(RewardOrder::getTotalFactorDouble).sum();


		this.pairs.values().forEach(pair -> {
			pair.apply0(this.factorSum, sharedVX);
		});
	}
}

