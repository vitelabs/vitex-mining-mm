package org.vite.dex.mm.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class User {
    String address;

    // the perDay mm-reward of VITE market
    BigDecimal mmRewardOfVITEMarket;

    // the perDay mm-reward of ETH market
    BigDecimal mmRewardOfETHMarket;

    // the perDay mm-reward in BTC market
    BigDecimal mmRewardOfBTCMarket;

    // the perDay mm-reward in USDT market
    BigDecimal mmRewardOfUSDTMarket;

    List<CurrentOrder> orders;
}
