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
@Table(name = "address_estimate_reward")
public class AddressEstimateReward {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_key")
    private Integer cycleKey;

    @Column(name = "address")
    private String address;

    @Column(name = "order_mining_total", precision = 50, scale = 18)
    private BigDecimal orderMiningTotal;

    @Column(name = "vite_market_reward", precision = 50, scale = 18)
    private BigDecimal viteMarketReward;

    @Column(name = "eth_market_reward", precision = 50, scale = 18)
    private BigDecimal ethMarketReward;

    @Column(name = "btc_market_reward", precision = 50, scale = 18)
    private BigDecimal btcMarketReward;

    @Column(name = "usdt_market_reward", precision = 50, scale = 18)
    private BigDecimal usdtMarketReward;

    @Column(name = "ctime")
    private Date ctime;

    @Column(name = "utime")
    private Date utime;
}
