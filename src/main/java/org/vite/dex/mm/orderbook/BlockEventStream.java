package org.vite.dex.mm.orderbook;

import com.google.common.collect.Lists;
import org.vite.dex.mm.entity.AccBlockVmLogs;
import org.vite.dex.mm.entity.BlockEvent;
import org.vite.dex.mm.utils.client.ViteCli;
import org.vitej.core.protocol.methods.response.AccountBlock;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class BlockEventStream {
	private List<BlockEvent> events = Lists.newArrayList();
	private final Long startHeight;
	private final Long endHeight;


	public BlockEventStream(long startHeight, long endHeight) {
		this.startHeight = startHeight;
		this.endHeight = endHeight;
	}

	public BlockEventStream(long startHeight, long endHeight, List<BlockEvent> events) {
		this(startHeight, endHeight);
		this.events.addAll(events);
	}

	public List<BlockEvent> getEvents() {
		events.sort(Comparator.comparing(BlockEvent::getHeight));
		return events;
	}

	public void addEvent(BlockEvent event) {
		events.add(event);
	}


	public void init(ViteCli viteCli, Tokens tokens) throws IOException {
		List<AccBlockVmLogs> vmLogInfoList = viteCli.getAccBlocksByHeightRange(startHeight, endHeight,
				1000);

		// parse vmLogs and group these vmLogs by trade-pair
		for (AccBlockVmLogs accBlock : vmLogInfoList) {
			BlockEvent blockEvent = BlockEvent.fromAccBlockVmLogs(accBlock, tokens);
			this.addEvent(blockEvent);
		}
		//@todo patch timestamp
	}

	public void patchTimestampToOrderEvent(Map<String, AccountBlock> accountBlockMap) {
		events.forEach(blockEvent -> {
			blockEvent.getOrderEvents().forEach(orderEvent -> {
				if (!orderEvent.ignore()) {
					AccountBlock block = accountBlockMap.get(orderEvent.getBlockHash());
					if (block != null) {
						orderEvent.setTimestamp(block.getTimestampRaw());
					}
				}
			});
		});
	}

	public void forEach(IBlockEventHandler handler, boolean reverted, boolean reversed) {
		if (reversed) {
			for (int i = events.size() - 1; i >= 0; i--) {
				BlockEvent t = events.get(i);
				if (reverted) {
					handler.revert(t);
				} else {
					handler.onward(t);
				}
			}
		} else {
			events.forEach(t -> {
				if (reverted) {
					handler.revert(t);
				} else {
					handler.onward(t);
				}
			});
		}
	}


	// @todo for example subString
	public BlockEventStream subStream(Long start, Long end) {
		return null;
	}

	// @todo

	public BlockEventStream concat(BlockEventStream a, BlockEventStream b) {
		return null;
	}
}
