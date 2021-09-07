package org.vite.dex.mm.orderbook;

import org.vite.dex.mm.entity.TradePair;
import org.vite.dex.mm.utils.client.ViteCli;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class Traveller {

	/**
	 * make all of orderbooks travel to a certain time in the past.Simply get the
	 * snapshot of orderbooks.
	 * 
	 * @param prevTime the timestamp in the past
	 * @param tokens   all tokens in the chain
	 * @param viteCli  the vite client
	 * @return
	 * @throws IOException
	 */
	public OrderBooks travelInTime(Long prevTime, Tokens tokens, ViteCli viteCli, List<TradePair> tradePairs)
			throws IOException {
		// prepare orderbook
		List<OrderBooks> candidates = new ArrayList<OrderBooks>();
		int n = 5;
		for (int i = 0; i < n; i++) {
			OrderBooks orderBooks = new OrderBooks(viteCli, tradePairs);
			orderBooks.init();
			candidates.add(orderBooks);
		}

		// prepare events
		Long startHeight = viteCli.getContractChainHeight(prevTime);
		Long endHeight = viteCli.getLatestAccountHeight();
		BlockEventStream stream = new BlockEventStream(startHeight, endHeight);
		stream.init(viteCli, tokens);
		stream.patchTimestampToOrderEvent(viteCli);

		// revert as a whole to previous time
		candidates.forEach(orderBooks -> {
			stream.action(orderBooks, true, true);
		});

		// elect the most suitable candidate
		return elect(candidates);
	}

	private OrderBooks elect(List<OrderBooks> candidates) {
		Map<String, OrderBooks> candidateMap = new HashMap<String, OrderBooks>();
		Map<String, Integer> cntMap = new HashMap<String, Integer>();

		candidates.forEach(t -> {
			String hash = t.hash();
			candidateMap.put(hash, t);
			cntMap.put(hash, cntMap.getOrDefault(hash, 0) + 1);
		});
		String max = null;
		int maxCnt = 0;

		for (Entry<String, Integer> entry : cntMap.entrySet()) {
			if (entry.getValue() > maxCnt) {
				maxCnt = entry.getValue();
				max = entry.getKey();
			}
		}
		return candidateMap.get(max);
	}
}
