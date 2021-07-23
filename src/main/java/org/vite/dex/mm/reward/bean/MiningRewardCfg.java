package org.vite.dex.mm.reward.bean;

import lombok.Data;

@Data
public class MiningRewardCfg {
    String tradePairSymbol;
    double effectiveDistance = 0.1;
}
