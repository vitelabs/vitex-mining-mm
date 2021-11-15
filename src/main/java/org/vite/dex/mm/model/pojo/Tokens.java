package org.vite.dex.mm.model.pojo;

import org.vitej.core.protocol.methods.response.TokenInfo;

import java.util.HashMap;
import java.util.Map;

public class Tokens {
	private Map<String, TokenInfo> tokenMap = new HashMap<String, TokenInfo>();

	public Tokens(Map<String, TokenInfo> tokenMap) {
		this.tokenMap = tokenMap;
	}

	public int getDecimals(String tti) {
		return tokenMap.get(tti).getDecimals();
	}

}
