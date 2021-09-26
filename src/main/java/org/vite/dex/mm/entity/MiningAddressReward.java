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

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "mining_address")
public class MiningAddressReward {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_key")
    private Integer cycleKey;

    @Column(name = "address")
    private String address;

    @Column(name = "amount", precision = 50, scale = 18)
    private BigDecimal totalAmount;

    @Column(name = "amount_percent", precision = 50, scale = 18)
    private BigDecimal amountPercent;

    @Column(name = "data_page")
    private Integer dataPage;

    @Column(name = "settle_status")
    private Integer settleStatus;

    @Column(name = "ctime")
    private Date ctime;

    @Column(name = "utime")
    private Date utime;

    @Column(name = "remark")
    private String remark;
}
