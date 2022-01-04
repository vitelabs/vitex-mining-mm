package org.vite.dex.mm.orderbook;

import org.vite.dex.mm.entity.OrderEvent;

public interface IOrderEventHandler {
	// back-trace according to a event
	void revert(OrderEvent event);

	// front-trace according to a event
	void onward(OrderEvent event);
}
