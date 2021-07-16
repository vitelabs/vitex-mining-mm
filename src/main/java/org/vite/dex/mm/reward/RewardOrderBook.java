package org.vite.dex.mm.reward;

import org.vite.dex.mm.bean.Order;
import org.vite.dex.mm.bean.OrderEvent;
import org.vite.dex.mm.orderbook.OrderBook;

import java.util.List;

public class RewardOrderBook extends OrderBook.Impl {

	@Override
	public void deal(OrderEvent e) {
		super.deal(e);
	}

	@Override
	public void init(List<Order> buys, List<Order> sells) {
		super.init(buys, sells);
	}

}
