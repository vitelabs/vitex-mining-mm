package org.vite.dex.mm.utils;

import org.vite.dex.mm.constant.enums.EventType;

import java.util.List;

import static org.vite.dex.mm.constant.constants.MMConst.ORDER_NEW_EVENT_TOPIC;
import static org.vite.dex.mm.constant.constants.MMConst.ORDER_UPDATE_EVENT_TOPIC;
import static org.vite.dex.mm.constant.constants.MMConst.TX_EVENT_TOPIC;

/**
 * Event utils
 */
public class EventParserUtils {
    /**
     * get the event type due to event topic
     * @param topics
     * @return
     */
    public static EventType getEventType(List<String> topics) {
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
