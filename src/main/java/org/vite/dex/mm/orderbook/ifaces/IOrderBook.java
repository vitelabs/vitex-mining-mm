package org.vite.dex.mm.orderbook.ifaces;

import org.vite.dex.mm.model.bean.OrderModel;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface IOrderBook extends IOrderEventHandler, IBlockEventHandler{
    
    void init(List<OrderModel> orderModels, Long blockHeight);

    List<OrderModel> getBuys();

    List<OrderModel> getSells();

    Map<String, OrderModel> getOrders(); // <OrderId,OrderModel>

    BigDecimal getBuy1Price();

    BigDecimal getSell1Price();

    Long getCurrBlockHeight();

    BigDecimal getAmountSum();

    int getAddCnt();

    int getRemoveCnt();

    String hash();

    void setOrderAware(IOrderEventHandleAware aware);
}
