package org.vite.dex.mm.orderbook;

import org.vite.dex.mm.utils.client.ViteCli;

import java.io.IOException;

public class TradeRecover2 {

	public OrderBooks recoverInTime(OrderBooks orderBooks, Long time, Tokens tokens, ViteCli viteCli)
			throws IOException {
		Long startHeight = viteCli.getContractChainHeight(time);
		Long endHeight = viteCli.getLatestAccountHeight();

		BlockEventStream stream = new BlockEventStream(startHeight, endHeight);
		stream.init(viteCli, tokens);


		stream.forEach(orderBooks, true, true);

		return orderBooks;
	}
}
