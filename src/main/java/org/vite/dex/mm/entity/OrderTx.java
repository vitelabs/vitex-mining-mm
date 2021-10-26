package org.vite.dex.mm.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderTx {
    private String txId;
    private String takerOrderId;
    private String makerOrderId;
    private BigDecimal price;
    private BigDecimal amount;
    private BigDecimal quantity;
}