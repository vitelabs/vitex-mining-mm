package org.vite.dex.mm.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.vite.dex.mm.constant.enums.SettleStatus;

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
public class MiningAddress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_key")
    private Integer cycleKey;

    @Column(name = "address")
    private String address;

    @Column(name = "order_mining_amount", precision = 50, scale = 18)
    private BigDecimal orderMiningAmount;

    @Column(name = "order_mining_percent", precision = 50, scale = 18)
    private BigDecimal orderMiningPercent;

    @Column(name = "invite_mining_amount", precision = 50, scale = 18)
    private BigDecimal inviteMiningAmount;

    @Column(name = "invite_mining_percent", precision = 50, scale = 18)
    private BigDecimal inviteMiningPercent;

    @Column(name = "total_reward", precision = 50, scale = 18)
    private BigDecimal totalReward;

    @Column(name = "data_page")
    private Integer dataPage;

    @Column(name = "settle_status")
    private SettleStatus settleStatus;

    @Column(name = "ctime")
    private Date ctime;

    @Column(name = "utime")
    private Date utime;
}
