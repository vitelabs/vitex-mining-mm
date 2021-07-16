package org.vite.dex.mm.orderbook;

import org.vitej.core.protocol.Vitej;

import java.util.Map;

/**
 * 1. prepare order book, get the current order book
 * 2. prepare events, get all events from last cycle to current
 * 3. recover order book, recover order order to last cycle by events 
 * 4. mm mining, calculate the market making mining rewards
 */
public class TradeRecover {
	// connect to vite node
	private Vitej vitej;

	// key: trade pair, example: VITE-000/BTC-000
	private Map<String, EventStream> eventStreams;
	private Map<String, OrderBook> orderBook;



	public void prepareOrderBook(String quote, String trade) {

	}

	/**
	 * 1. get trade contract vmLogs 
	 * 2. parse vmLogs 
	 * 3. mark timestamp for vm log
	 * @param start
	 */
	public void prepareEvents(String quote, String trade, long start) {

	}

	public void recoverOrderBook(EventStream eventStream, OrderBook orderBook) {

	}

}
