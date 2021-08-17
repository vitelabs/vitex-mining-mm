package org.vite.dex.mm.reward.bean;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;


@Data
@Accessors(chain = true)
public class RewardTradePair {


	private final String tp;
	private final List<RewardOrder> orders;



	private double factorSum;

	private double pairSharedVX;

	private double sellAmountSum;
	private double buyAmountSum;


	private double sellSharedVx;
	private double buySharedVx;

	public RewardTradePair(String tp, List<RewardOrder> orders) {
		this.tp = tp;
		this.orders = orders;
	}

	public void apply0(double marketFactorSum, double marketSharedVX) {
		this.factorSum = this.orders.stream().mapToDouble(RewardOrder::getTotalFactorDouble).sum();


		this.pairSharedVX = this.factorSum / marketFactorSum * marketSharedVX;

		this.sellAmountSum = orders.stream().filter(reward -> reward.getOrderSide())
				.mapToDouble(RewardOrder::getAmount).sum();
		this.buyAmountSum = orders.stream().filter(reward -> !reward.getOrderSide())
				.mapToDouble(RewardOrder::getAmount).sum();
	}

	private void calVx() {
		// TODO
	}

	private void calOrderVx() {
		double sellSharedVxPerAmount = this.sellSharedVx / this.sellAmountSum;
		double buyShardVxPerAmount = this.buySharedVx / this.buyAmountSum;

		this.orders.forEach(order -> {
			order.applyReward(sellSharedVxPerAmount, buyShardVxPerAmount);
		});
	}

}
