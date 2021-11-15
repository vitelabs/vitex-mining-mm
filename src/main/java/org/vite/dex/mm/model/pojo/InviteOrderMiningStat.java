package org.vite.dex.mm.model.pojo;

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
