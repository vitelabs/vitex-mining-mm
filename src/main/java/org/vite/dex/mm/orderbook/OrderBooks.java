package org.vite.dex.mm.orderbook;

import org.apache.commons.codec.digest.DigestUtils;
import org.vite.dex.mm.entity.BlockEvent;
import org.vite.dex.mm.entity.OrderBookInfo;
import org.vite.dex.mm.entity.TradePair;
import org.vite.dex.mm.utils.client.ViteCli;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class OrderBooks implements IBlockEventHandler {
	private List<TradePair> tradePairs;
	private Map<String, OrderBook> books = new HashMap<String, OrderBook>();
	private ViteCli viteCli;

	// current trade contract height
	private Long currentHeight;

	public List<TradePair> getTradePairs() {
		return tradePairs;
	}

	public Map<String, OrderBook> getBooks() {
		return books;
	}

	public OrderBooks(ViteCli viteCli, List<TradePair> tradePairs) {
		this.viteCli = viteCli;
		this.tradePairs = tradePairs;
	}

	/**
	 * get order books of all trade pair in curruent time
	 * 
	 * @throws IOException
	 */
	public void init() throws IOException {
		for (TradePair tp : this.tradePairs) {
			OrderBookInfo book = viteCli.getOrdersFromMarket(tp.getTradeTokenId(), tp.getQuoteTokenId(), 100);
			OrderBook orderBook = new OrderBook.Impl();
			orderBook.init(book.getOrderModels(), book.getCurrBlockheight());
			books.put(tp.getTradePair(), orderBook);
		}
	}

	@Override
	public void revert(BlockEvent blockEvent) {
		String tp = blockEvent.getTp();
		OrderBook ob = books.get(tp);
		if (ob == null) {
			return;
		}
		ob.revert(blockEvent);
		this.currentHeight = blockEvent.getHeight();
	}

	@Override
	public void onward(BlockEvent blockEvent) {
		String tp = blockEvent.getTp();
		OrderBook ob = books.get(tp);
		if (ob == null) {
			return;
		}
		ob.onward(blockEvent);
		this.currentHeight = blockEvent.getHeight();
	}

	public void setOrderAware(IOrderEventHandleAware aware) {
		this.books.values().forEach(t -> {
			t.setOrderAware(aware);
		});
	}

	public String hash() {
		List<OrderBook> orderBooks = books.entrySet().stream().sorted(Comparator.comparing(Entry::getKey))
				.map(t -> t.getValue()).collect(Collectors.toList());

		String result = orderBooks.stream().map(t -> t.hash()).collect(Collectors.joining("-"));
		return DigestUtils.md5Hex(result);
	}
}
