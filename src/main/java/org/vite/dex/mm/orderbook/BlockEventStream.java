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
import java.util.stream.Collectors;

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

	// get vmLogs between startHeight and endHeight and parse them into BlockEvent
	public void init(ViteCli viteCli, Tokens tokens) throws IOException {
		List<AccBlockVmLogs> accBlockVmlogList = viteCli.getAccBlockVmLogsByHeightRange(startHeight, endHeight, 1000);
		for (AccBlockVmLogs accBlockVmLogs : accBlockVmlogList) {
			BlockEvent blockEvent = BlockEvent.fromAccBlockVmlogs(accBlockVmLogs, tokens);
			this.addEvent(blockEvent);
		}
	}

	public void patchTimestampToOrderEvent(ViteCli viteCli) throws IOException {
		// inject block`timestamp to vmLogs
		Map<String, AccountBlock> accountBlockMap = viteCli.getAccountBlockMap(startHeight, endHeight);
		events.forEach(blockEvent -> {
			blockEvent.getOrderEvents().forEach(orderEvent -> {
				if (!orderEvent.ignore()) {
					AccountBlock accountBlock = accountBlockMap.get(orderEvent.getBlockHash());
					if (accountBlock != null) {
						orderEvent.setTimestamp(accountBlock.getTimestampRaw());
					}
				}
			});
		});
	}

	public void action(IBlockEventHandler handler, boolean reversed, boolean reverted) {
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

	public BlockEventStream subStream(Long start, Long end) {
		List<BlockEvent> subEvents = this.events.stream()
				.filter(blockEvent -> blockEvent.getHeight() >= start && blockEvent.getHeight() <= end)
				.collect(Collectors.toList());
		return new BlockEventStream(start, end, subEvents);
	}
}
