package org.vite.dex.mm.orderbook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.vite.dex.mm.utils.client.ViteCli;
import org.vitej.core.protocol.methods.Address;
import org.vitej.core.protocol.methods.request.VmLogFilter;
import org.vitej.core.protocol.methods.response.VmLogInfo;
import org.vitej.core.protocol.methods.response.VmlogInfosResponse;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.vite.dex.mm.constant.constants.MMConst.TRADE_CONTRACT_ADDRESS;

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
