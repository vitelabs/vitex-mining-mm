package org.vite.dex.mm.orderbook;

import org.vite.dex.mm.entity.BlockEvent;

/**
 * IBlockEventHandler is the interface implemented by the OrderBooks which is
 * responsible for reverting and onwarding according to a block of events
 */
public interface IBlockEventHandler {
	// back-trace according to a event
	void revert(BlockEvent event);

	// front-trace according to a event
	void onward(BlockEvent event);
}
