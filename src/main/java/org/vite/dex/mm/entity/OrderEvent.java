package org.vite.dex.mm.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.vite.dex.mm.constant.enums.OrderEventType;
import org.vitej.core.protocol.methods.response.VmLogInfo;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent {
    private VmLogInfo vmLogInfo;

    private long timestamp;

    private OrderEventType type;
}
