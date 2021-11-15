package org.vite.dex.mm.model.bean;

import lombok.Data;
import org.spongycastle.util.encoders.Hex;
import org.vite.dex.mm.constant.enums.EventType;
import org.vite.dex.mm.model.pojo.OrderTx;
import org.vite.dex.mm.model.proto.DexTradeEvent;
import org.vite.dex.mm.utils.ViteDataDecodeUtils;
import org.vite.dex.mm.utils.decode.BytesUtils;
import org.vitej.core.protocol.methods.response.VmLogInfo;

import java.util.ArrayList;
import java.util.List;

@Data
public class AccBlockVmLogs {
    private String hash;
    private Long height;
    private List<VmLogInfo> vmLogs;
    private List<OrderTx> transactions = new ArrayList<>();

    public void parseTransaction() {
        vmLogs.forEach(vmLogInfo -> {
            try {
                byte[] event = vmLogInfo.getVmlog().getData();
                EventType eventType = ViteDataDecodeUtils.getEventType(vmLogInfo.getVmlog().getTopicsRaw());
                if (eventType == EventType.TX) {
                    DexTradeEvent.Transaction tx = DexTradeEvent.Transaction.parseFrom(event);
                    OrderTx orderTx = new OrderTx();
                    orderTx.setTxId(Hex.toHexString(tx.getId().toByteArray()));
                    orderTx.setTakerOrderId(Hex.toHexString(tx.getTakerId().toByteArray()));
                    orderTx.setMakerOrderId(Hex.toHexString(tx.getMakerId().toByteArray()));
                    orderTx.setPrice(BytesUtils.priceToBigDecimal(tx.getPrice().toByteArray()));
                    orderTx.setQuantity(BytesUtils.quantityToBigDecimal(tx.getQuantity().toByteArray()));
                    orderTx.setAmount(BytesUtils.quantityToBigDecimal(tx.getAmount().toByteArray()));
                    this.transactions.add(orderTx);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public OrderTx getTx(String orderId) {
        for (OrderTx tx : getTransactions()) {
            if (tx.getMakerOrderId().equals(orderId) || tx.getTakerOrderId().equals(orderId)) {
                return tx;
            }
        }
        return null;
    }
}
