package org.vite.dex.mm.model.bean;

import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * each mining order whoes address has invitationship should has
 * InviteOrderMiningReward
 */
@Data
public class InviteOrderMiningReward {
    // for invitee, the orderId is the invitee`s orderId
    // for inviter, the orderId is the invitee`s orderId
    private String orderId;

    // for invitee, the address is the invitee`s address
    // for inviter, the address is the inviter`s address
    private String address;

    private boolean side;

    private Integer market;

    private String tradePair;

    private BigDecimal factor = BigDecimal.ZERO;

    private BigDecimal inviteRewardVX = BigDecimal.ZERO;

    /**
     * calculate the amount of VX obtained for the invitate mining order
     * 
     * @param sellSharedVxPerFactor
     * @param buyShardVxPerFactor
     */
    public void applyInviteReward(BigDecimal sellSharedVxPerFactor, BigDecimal buyShardVxPerFactor) {
        if (isSide()) {
            this.inviteRewardVX = this.factor.multiply(sellSharedVxPerFactor).setScale(18, RoundingMode.DOWN);
        } else {
            this.inviteRewardVX = this.factor.multiply(buyShardVxPerFactor).setScale(18, RoundingMode.DOWN);
        }
    }
}
