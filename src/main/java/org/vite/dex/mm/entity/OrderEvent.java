package org.vite.dex.mm.entity;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class OrderEvent {
    private OrderLog orderLog;

    private long timestamp; // emited timestamp

    private EventType type;

    private String blockHash;

    public OrderEvent(OrderLog orderLog, long timestamp, EventType type) {
        this.orderLog = orderLog;
        this.timestamp = timestamp;
        this.type = type;
    }

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
            byte[] eventData = vmlog.getData();
            EventType eventType = getEventType(vmlog.getTopicsRaw());
            switch (eventType) {
                case NewOrder:
                    DexTradeEvent.NewOrderInfo dexOrder = DexTradeEvent.NewOrderInfo.parseFrom(eventData);
                    this.setType(NewOrder);
                    this.orderLog = OrderLog.fromNewOrder(dexOrder, vmlog);
                    break;
                case UpdateOrder:
                    DexTradeEvent.OrderUpdateInfo orderUpdateInfo = DexTradeEvent.OrderUpdateInfo.parseFrom(eventData);
                    OrderTx tx = vmlogs.getTx(Hex.toHexString(orderUpdateInfo.getId().toByteArray()));
                    this.orderLog = OrderLog.fromUpdateOrder(orderUpdateInfo, vmlog, tx, tokens);
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
            log.error("parse vmlog failed, the err:" + e);
            throw new RuntimeException(e);
        }
    }
}
