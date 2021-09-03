package org.vite.dex.mm.entity;

import lombok.Data;
import org.spongycastle.util.encoders.Hex;
import org.vite.dex.mm.constant.enums.EventType;
import org.vite.dex.mm.constant.enums.OrderStatus;
import org.vite.dex.mm.model.proto.DexTradeEvent;
import org.vite.dex.mm.orderbook.Tokens;
import org.vitej.core.protocol.methods.response.Vmlog;

import static org.vite.dex.mm.constant.enums.EventType.NewOrder;
import static org.vite.dex.mm.constant.enums.EventType.TX;
import static org.vite.dex.mm.constant.enums.EventType.Unknown;
import static org.vite.dex.mm.constant.enums.EventType.UpdateOrder;
import static org.vite.dex.mm.utils.ViteDataDecodeUtils.getEventType;

@Data
public class OrderEvent {
    private OrderLog orderLog;

    // the order event emit timestamp
    private long timestamp;

    private EventType type;

    private String blockHash;

    public OrderEvent() {
        this.orderLog = new OrderLog();
    }

    public String getOrderId() {
        return orderLog.getOrderId();
    }

    public String tradePair() {
        return orderLog.getTradePair();
    }

    public OrderStatus getStatus() {

        return orderLog.getStatus();
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean ignore() {
        if (getType() == NewOrder || getType() == UpdateOrder) {
            return false;
        }
        return true;
    }

    public void parse(Vmlog vmlog, AccBlockVmLogs vmlogs, Tokens tokens) {
        try {
            byte[] event = vmlog.getData();
            EventType eventType = getEventType(vmlog.getTopicsRaw());
            switch (eventType) {
                case NewOrder:
                    DexTradeEvent.NewOrderInfo dexOrder = DexTradeEvent.NewOrderInfo.parseFrom(event);
                    this.setType(NewOrder);
                    this.orderLog = OrderLog.fromNewOrder(vmlog, dexOrder);
                    break;
                case UpdateOrder:
                    // both cancel and filled order will emit the updateEvent
                    DexTradeEvent.OrderUpdateInfo orderUpdateInfo = DexTradeEvent.OrderUpdateInfo.parseFrom(event);
                    String orderId = Hex.toHexString(orderUpdateInfo.getId().toByteArray());
                    OrderTx tx = vmlogs.getTx(orderId);
                    this.orderLog = OrderLog.fromUpdateOrder(vmlog, orderUpdateInfo, tx, tokens);
                    this.setType(UpdateOrder);
                    break;
                case TX:
                    this.setType(TX);
                    break;
                case Unknown:
                    this.setType(Unknown);
                    break;
                default:
                    throw new AssertionError(eventType.name());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
