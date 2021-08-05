package org.vite.dex.mm.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.vite.dex.mm.constant.enums.EventType;
import org.vite.dex.mm.constant.enums.OrderUpdateInfoStatus;
import org.vite.dex.mm.model.proto.DexTradeEvent;
import org.vitej.core.protocol.methods.response.VmLogInfo;
import org.vitej.core.protocol.methods.response.Vmlog;

import java.util.List;

import static org.vite.dex.mm.constant.constants.MMConst.*;
import static org.vite.dex.mm.constant.enums.EventType.Unknown;
import static org.vite.dex.mm.constant.enums.EventType.*;
import static org.vite.dex.mm.constant.enums.OrderUpdateInfoStatus.*;

@Data
@NoArgsConstructor
public class OrderEvent {
    private VmLogInfo vmLogInfo;

    private OrderLog orderLog;

    private long timestamp;

    private EventType type;

    private boolean del = false;


    public OrderEvent(VmLogInfo vmLogInfo, long timestamp, EventType type) {
        this.vmLogInfo = vmLogInfo;
        this.timestamp = timestamp;
        this.type = type;
    }

    public OrderEvent(VmLogInfo vmLogInfo) {
        this.vmLogInfo = vmLogInfo;
        this.orderLog = new OrderLog();
    }

    public String getOrderId() {
        return orderLog.getOrderId();
    }

    public String getTradePairSymbol() {
        return orderLog.getTradePair();
    }

    public OrderUpdateInfoStatus getStatus() {
        if (getType() == UpdateOrder) {
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
                    return OrderUpdateInfoStatus.Unknown;
            }
        }
        return OrderUpdateInfoStatus.Unknown;
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
                    this.setType(NewOrder);
                    this.orderLog = OrderLog.fromNewOrder(dexOrder);
                    break;
                case UpdateOrder:
                    // both cancel and filled order will emit the updateEvent
                    DexTradeEvent.OrderUpdateInfo orderUpdateInfo = DexTradeEvent.OrderUpdateInfo.parseFrom(event);
                    this.setType(UpdateOrder);
                    this.orderLog = OrderLog.fromUpdateOrder(orderUpdateInfo);
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

    public EventType getEventType(List<String> topics) {
        for (String topic : topics) {
            if (TX_EVENT_TOPIC.equals(topic)) {
                return TX;
            }
            if (ORDER_NEW_EVENT_TOPIC.equals(topic)) {
                return NewOrder;
            }
            if (ORDER_UPDATE_EVENT_TOPIC.equals(topic)) {
                return UpdateOrder;
            }
        }
        return Unknown;
    }
}
