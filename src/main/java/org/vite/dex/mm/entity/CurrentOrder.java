package org.vite.dex.mm.entity;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CurrentOrder {

    @JSONField(name = "Id")
    private String orderId;

    private BigDecimal price;

    private BigDecimal quantity; // origin quantity

    private BigDecimal amount; // origin amount

    private BigDecimal executedQuantity = BigDecimal.ZERO; // the quantity which has been executed

    private BigDecimal executedAmount = BigDecimal.ZERO; // the amount which has been executed

    private String address;

    private boolean side;

    private long timestamp; // order created time
}
