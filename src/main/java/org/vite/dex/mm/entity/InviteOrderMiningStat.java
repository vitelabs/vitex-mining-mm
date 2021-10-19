package org.vite.dex.mm.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * the orderMining invitation stastics for each each address
 */
@Data
public class InviteOrderMiningStat {
    private String address;

    private BigDecimal ratio; // ratio = amount / totalReleasedVX

    private BigDecimal amount;
}
