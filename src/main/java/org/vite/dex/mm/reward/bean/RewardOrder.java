package org.vite.dex.mm.reward.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RewardOrder {
    int market;
    private BigDecimal factor;
}
