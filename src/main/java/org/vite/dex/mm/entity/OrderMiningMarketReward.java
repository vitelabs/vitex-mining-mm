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
@Table(name = "order_mining_market_reward")
public class OrderMiningMarketReward {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_key")
    private Integer cycleKey;

    @Column(name = "quote_token_type")
    private Integer quoteTokenType;

    @Column(name = "address")
    private String address;

    @Column(name = "factor_ratio", precision = 50, scale = 18)
    private BigDecimal factorRatio;

    @Column(name = "amount", precision = 50, scale = 18)
    private BigDecimal amount;

    @Column(name = "ctime")
    private Date ctime;

    @Column(name = "utime")
    private Date utime;
}
