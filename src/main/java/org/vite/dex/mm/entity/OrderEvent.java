package org.vite.dex.mm.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.vite.dex.mm.constant.enums.EventType;
import org.vite.dex.mm.constant.enums.OrderEventType;
import org.vite.dex.mm.constant.enums.OrderUpdateInfoStatus;
import org.vite.dex.mm.model.proto.DexTradeEvent;
import org.vite.dex.mm.utils.EventParserUtils;
import org.vite.dex.mm.utils.ViteDataDecodeUtils;
import org.vitej.core.protocol.methods.response.VmLogInfo;
import org.vitej.core.protocol.methods.response.Vmlog;

import static org.vite.dex.mm.constant.constants.MMConst.UnderscoreStr;

@Data
@NoArgsConstructor
public class OrderEvent {
    private VmLogInfo vmLogInfo;

    private long timestamp;

    private OrderEventType type;

    public OrderEvent(VmLogInfo vmLogInfo, long timestamp, OrderEventType type) {
        this.vmLogInfo = vmLogInfo;
        this.timestamp = timestamp;
        this.type = type;
    }

    public OrderEvent(VmLogInfo vmLogInfo) {
        this.vmLogInfo = vmLogInfo;
    }



    private boolean del = false;

    public String getOrderId() {
        // todo
        return "";
    }

    // todo
    public String getTp() {
        return "";
    }

    public OrderUpdateInfoStatus getStatus() {
        return OrderUpdateInfoStatus.Cancelled;
    }

    public void markTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    // todo from type
    public boolean ignore() {
        return true;
    }

    public String getBlockHash() {
        return this.vmLogInfo.getAccountBlockHashRaw();
    }


    public void parse() {
        try {
            Vmlog vmlog = vmLogInfo.getVmlog();
            byte[] event = vmlog.getData();
            EventType eventType = EventParserUtils.getEventType(vmlog.getTopicsRaw());

            switch (eventType) {
                case NewOrder:
                    DexTradeEvent.NewOrderInfo dexOrder = DexTradeEvent.NewOrderInfo.parseFrom(event);
                    String tradeTokenOfNewOrder =
                            ViteDataDecodeUtils.getShowToken(dexOrder.getTradeToken().toByteArray());
                    String quoteTokenOfNewOrder =
                            ViteDataDecodeUtils.getShowToken(dexOrder.getQuoteToken().toByteArray());
                    String tradePairOfNewOrder = tradeTokenOfNewOrder + UnderscoreStr + quoteTokenOfNewOrder;

                    break;
                // both cancel and fill order will emit the updateEvent
                case UpdateOrder:
                    DexTradeEvent.OrderUpdateInfo orderUpdateInfo = DexTradeEvent.OrderUpdateInfo.parseFrom(event);
                    String tradeTokenOfUpdateOrder =
                            ViteDataDecodeUtils.getShowToken(orderUpdateInfo.getTradeToken().toByteArray());
                    String quoteTokenOfUpdateOrder =
                            ViteDataDecodeUtils.getShowToken(orderUpdateInfo.getQuoteToken().toByteArray());
                    break;
                //why should parse this ones?
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

}
