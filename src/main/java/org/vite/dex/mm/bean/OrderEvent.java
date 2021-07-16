package org.vite.dex.mm.bean;

public class OrderEvent {
	// incremental
	private long id;
	// millis seconds
	private long timestamp;

	// 1. new order
	// 2. fill
	// 3. cancel
	private int type;
}
