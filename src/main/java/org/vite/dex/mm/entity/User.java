package org.vite.dex.mm.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class User {
    String address;

    // the market mining total reward perDay
    BigDecimal mmTotalReward;

    List<Order> orders;
}
