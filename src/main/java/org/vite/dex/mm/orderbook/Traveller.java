package org.vite.dex.mm.orderbook;

import org.vite.dex.mm.utils.client.ViteCli;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class Traveller {

	public OrderBooks travelInTime(Long time, Tokens tokens, ViteCli viteCli) throws IOException {
		List<OrderBooks> candidates = new ArrayList<OrderBooks>();
		int n = 5;
		for (int i = 0; i < n; i++) {
			OrderBooks orderBooks = new OrderBooks();
			orderBooks.init();
			candidates.add(orderBooks);
		}

		Long startHeight = viteCli.getContractChainHeight(time);
		Long endHeight = viteCli.getLatestAccountHeight();

		BlockEventStream stream = new BlockEventStream(startHeight, endHeight);
		stream.init(viteCli, tokens);


		candidates.forEach(orderBooks -> {
			stream.forEach(orderBooks, true, true);
		});

		return elect(candidates);
	}


	// @todo
	private OrderBooks elect(List<OrderBooks> candidates) {
		Map<String, OrderBooks> candidateMap = new HashMap<String, OrderBooks>();
		Map<String, Integer> cntMap = new HashMap<String, Integer>();

		candidates.forEach(t -> {
			String hash = t.hash();
			candidateMap.put(hash, t);
			cntMap.put(hash, cntMap.getOrDefault(hash, 0));
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
