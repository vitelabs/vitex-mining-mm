package org.vite.dex.mm.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.vite.dex.mm.constant.enums.EventType;
import org.vite.dex.mm.constant.enums.OrderEventType;
import org.vite.dex.mm.constant.enums.OrderUpdateInfoStatus;
import org.vite.dex.mm.model.proto.DexTradeEvent;
import org.vitej.core.protocol.methods.response.VmLogInfo;
import org.vitej.core.protocol.methods.response.Vmlog;

import java.util.List;

import static org.vite.dex.mm.constant.constants.MMConst.*;
import static org.vite.dex.mm.constant.enums.OrderEventType.OrderNew;
import static org.vite.dex.mm.constant.enums.OrderEventType.OrderUpdate;
import static org.vite.dex.mm.constant.enums.OrderUpdateInfoStatus.*;

@Data
@NoArgsConstructor
public class OrderEvent {
    private VmLogInfo vmLogInfo;

    private OrderLog orderLog;

    private long timestamp;

    private OrderEventType type;

    private boolean del = false;


    public OrderEvent(VmLogInfo vmLogInfo, long timestamp, OrderEventType type) {
        this.vmLogInfo = vmLogInfo;
        this.timestamp = timestamp;
        this.type = type;
    }

    public OrderEvent(VmLogInfo vmLogInfo) {
        this.vmLogInfo = vmLogInfo;
    }

    public String getOrderId() {
        return orderLog.getOrderId();
    }

    public String getTradePairSymbol() {
        return orderLog.getTradePair();
    }

    public OrderUpdateInfoStatus getStatus() {
        if (getType() == OrderUpdate) {
            int status = orderLog.getStatus();
            switch (status) {
                case 1:
                    return Pending;
                case 2:
                    return PartialExecuted;
                case 3:
                    return FullyExecuted;
                case 4:
                    return Cancelled;
                default:
                    return Unknown;
            }
        }
        return OrderUpdateInfoStatus.Unknown;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean ignore() {
        if (!(getType() == OrderNew || getType() == OrderUpdate)) {
            return false;
        }
        return true;
    }

    public String getBlockHash() {
        return this.vmLogInfo.getAccountBlockHashRaw();
    }

    public void parse() {
        try {
            Vmlog vmlog = vmLogInfo.getVmlog();
            byte[] event = vmlog.getData();
            EventType eventType = getEventType(vmlog.getTopicsRaw());

            switch (eventType) {
                case NewOrder:
                    DexTradeEvent.NewOrderInfo dexOrder = DexTradeEvent.NewOrderInfo.parseFrom(event);
                    this.setType(OrderNew);
                    this.orderLog = OrderLog.fromNewOrder(dexOrder);
                    break;
                case UpdateOrder:
                    // both cancel and filled order will emit the updateEvent
                    DexTradeEvent.OrderUpdateInfo orderUpdateInfo = DexTradeEvent.OrderUpdateInfo.parseFrom(event);
                    this.setType(OrderUpdate);
                    this.orderLog = OrderLog.fromUpdateOrder(orderUpdateInfo);
                    break;
                case TX:
                case Unknown:
                    break;
                default:
                    throw new AssertionError(eventType.name());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public EventType getEventType(List<String> topics) {
        for (String topic : topics) {
            if (TX_EVENT_TOPIC.equals(topic)) {
                return EventType.TX;
            }
            if (ORDER_NEW_EVENT_TOPIC.equals(topic)) {
                return EventType.NewOrder;
            }
            if (ORDER_UPDATE_EVENT_TOPIC.equals(topic)) {
                return EventType.UpdateOrder;
            }
        }
        return EventType.Unknown;
    }
}
