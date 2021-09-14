package org.vite.dex.mm.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AddressSettleReward {
    private String address;

    private Integer cycleKey;

    private Integer dataPage;

    private BigDecimal totalAmount;

    private BigDecimal amountPercent;
}
