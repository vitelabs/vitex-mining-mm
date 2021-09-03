package org.vite.dex.mm.orderbook;

import org.vite.dex.mm.entity.BlockEvent;

public interface IBlockEventHandler {
	// back-trace according to a event
	void revert(BlockEvent event);

	// front-trace according to a event
	void onward(BlockEvent event);
}
