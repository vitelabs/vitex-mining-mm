package org.vite.dex.mm.model.bean;

import lombok.Data;
import org.vite.dex.mm.model.pojo.CurrentOrder;

import java.util.List;

@Data
public class OrderBookInfo {
    List<CurrentOrder> currOrders;
    List<OrderModel> orderModels;
    Long currBlockheight;

    public OrderBookInfo(List<CurrentOrder> currOrders, List<OrderModel> orderModels, Long currBlockHeight) {
        this.currOrders = currOrders;
        this.orderModels = orderModels;
        this.currBlockheight = currBlockHeight;
    }

    public static OrderBookInfo fromCurrOrdersAndHeight(List<CurrentOrder> currOrders, Long currBlockheight) {
        return new OrderBookInfo(currOrders, null, currBlockheight);
    }

    public static OrderBookInfo fromOrderModelsAndHeight(List<OrderModel> orderModels, Long currBlockheight) {
        return new OrderBookInfo(null, orderModels, currBlockheight);
    }
}
