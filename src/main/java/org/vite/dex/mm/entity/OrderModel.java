package org.vite.dex.mm.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderModel {
    String id;
    String address;
    int marketId;
    boolean side;
    int type;
    BigDecimal price;
    double takerFeeRate;
    double makerFeeRate;
    double takerOperatorFeeRate;
    double makerOperatorFeeRate;
    BigDecimal quantity;
    BigDecimal amount;
    int status;
    BigDecimal executedQuantity;
    BigDecimal executedAmount;
    BigDecimal executedBaseFee;
    BigDecimal executedOperatorFee;
    long timestamp;
    String sendHash;

}
