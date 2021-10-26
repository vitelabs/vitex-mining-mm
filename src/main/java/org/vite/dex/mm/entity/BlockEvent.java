package org.vite.dex.mm.entity;

import lombok.Data;
import org.vite.dex.mm.orderbook.IOrderEventHandler;
import org.vite.dex.mm.orderbook.Tokens;

import java.util.ArrayList;
import java.util.List;

/**
 * BlockEvent is a collection containing a set of OrderEvents which located in
 * the same block.
 * 
 */
@Data
public class BlockEvent {
	private final List<OrderEvent> orderEvents;
	private final Long height;
	private final String hash;
	private final String tp;

	public BlockEvent(List<OrderEvent> orderEvents, Long height, String hash) {
		this.orderEvents = orderEvents;
		this.tp = orderEvents.get(0).getOrderLog().getTradePair();
		this.height = height;
		this.hash = hash;
	}

	public static BlockEvent fromAccBlockVmlogs(AccBlockVmLogs accBlockVmLogs, Tokens tokens) {
		// parse Tx in the block
		accBlockVmLogs.parseTransaction(); 

		List<OrderEvent> events = new ArrayList<>();
		accBlockVmLogs.getVmLogs().forEach(vmLog -> {
			OrderEvent orderEvent = new OrderEvent();
			orderEvent.parse(vmLog.getVmlog(), accBlockVmLogs, tokens); // parse NewOrder/UpdateOrder event
			orderEvent.setBlockHash(vmLog.getAccountBlockHashRaw());
			events.add(orderEvent);
		});

		BlockEvent result = new BlockEvent(events, accBlockVmLogs.getHeight(), accBlockVmLogs.getHash());
		return result;
	}

	/**
	 * make orderbook travel by OrderEvents resided in BlockEvent
	 * @param handler
	 * @param reverted
	 * @param reversed
	 */
	public void travel(IOrderEventHandler handler, boolean reverted, boolean reversed) {
		if (reversed) {
			for (int i = orderEvents.size() - 1; i >= 0; i--) {
				OrderEvent t = orderEvents.get(i);
				if (reverted) {
					handler.revert(t);
				} else {
					handler.onward(t);
				}
			}
		} else {
			orderEvents.forEach(t -> {
				if (reverted) {
					handler.revert(t);
				} else {
					handler.onward(t);
				}
			});
		}
	}
}
