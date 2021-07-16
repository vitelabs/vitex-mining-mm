package org.vite.data.dex.service;

import org.vite.data.dex.bean.Event;
import org.vite.data.dex.bean.OrderBook;

import java.util.ArrayList;
import java.util.List;

/**
 * the corresponding order book to each trade-pair
 */
public class OrderBookService {
    OrderBook orderBook;

    // 1.support getting events of specific time-interval
    public List<Event> getEvents(){
        return new ArrayList<>();
    }

    // 2.revert order book status according to the event list
    public OrderBook revertStatus(List<Event> events){
        for(Event e:events){
            orderBook = orderBook.revert(e);
        }
        return orderBook;
    }

    // 3.forward order book according to the event list
    public OrderBook forwardStatus(List<Event> events){
        for(Event e:events){
            orderBook = orderBook.doEvent(e);
        }
        return orderBook;
    }
}
