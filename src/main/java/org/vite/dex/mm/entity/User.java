package org.vite.dex.mm.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class User {
    String address;

    // the perDay mm-reward for USDT market
    BigDecimal mmRewardForUSDTMarket;

    // the perDay mm-reward for BTC market
    BigDecimal mmRewardForBTCMarket;

    // the perDay mm-reward for ETH market
    BigDecimal mmRewardForETHMarket;

    // the perDay mm-reward for VITE market
    BigDecimal mmRewardForVITEMarket;

    List<CurrentOrder> orders;
}
