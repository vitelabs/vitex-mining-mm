package org.vite.dex.mm.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import java.math.BigDecimal;
import java.util.Date;

/**
 * the orderMining invitation stastics for each each address
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "invite_order_mining_stat")
public class InviteOrderMiningStat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_key")
    private Integer cycleKey;

    @Column(name = "address")
    private String address;

    @Column(name = "ratio")
    private BigDecimal ratio; // ratio = amount / totalReleasedVX

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "ctime")
    private Date ctime;

    @Column(name = "utime")
    private Date utime;
}
