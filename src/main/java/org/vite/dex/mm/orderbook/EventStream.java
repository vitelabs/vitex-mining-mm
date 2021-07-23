package org.vite.dex.mm.orderbook;

import org.vitej.core.protocol.methods.response.VmLogInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * EventStream from the trade contract of the chain
 */
public class EventStream {

    private List<VmLogInfo> events = new ArrayList<>();

    public List<VmLogInfo> getEvents() {
        return events;
    }

    public void addEvent(VmLogInfo event) {
        events.add(event);
    }

}
