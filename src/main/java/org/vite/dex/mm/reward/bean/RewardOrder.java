package org.vite.dex.mm.reward.bean;

import lombok.Data;
import org.vite.dex.mm.bean.Order;
import org.vite.dex.mm.bean.OrderEvent;
import org.vite.dex.mm.reward.RewardOrderBook;

import java.math.BigDecimal;

@Data
public class RewardOrder {
	private Order order;
	private BigDecimal rewardTotal;
	private long lastCalculatedTime;

	public RewardOrder(Order order) {
		this.order = order;
	}

	public void revert(OrderEvent event) {

	}

	public void deal(OrderEvent event, RewardOrderBook orderbook, MiningRewardCfg cfg) {

	}
}
