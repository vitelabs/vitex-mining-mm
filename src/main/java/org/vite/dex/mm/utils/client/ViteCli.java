package org.vite.dex.mm.utils.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;
import org.vite.dex.mm.entity.AccBlockVmLogs;
import org.vite.dex.mm.entity.CurrentOrder;
import org.vite.dex.mm.entity.OrderBookInfo;
import org.vite.dex.mm.entity.OrderModel;
import org.vitej.core.protocol.HttpService;
import org.vitej.core.protocol.Vitej;
import org.vitej.core.protocol.methods.Address;
import org.vitej.core.protocol.methods.Hash;
import org.vitej.core.protocol.methods.TokenId;
import org.vitej.core.protocol.methods.request.VmLogFilter;
import org.vitej.core.protocol.methods.response.AccountBlock;
import org.vitej.core.protocol.methods.response.AccountBlockResponse;
import org.vitej.core.protocol.methods.response.AccountBlocksResponse;
import org.vitej.core.protocol.methods.response.CommonResponse;
import org.vitej.core.protocol.methods.response.SnapshotBlock;
import org.vitej.core.protocol.methods.response.SnapshotBlocksResponse;
import org.vitej.core.protocol.methods.response.TokenInfo;
import org.vitej.core.protocol.methods.response.TokenInfoListWithTotalResponse;
import org.vitej.core.protocol.methods.response.TokenInfoResponse;
import org.vitej.core.protocol.methods.response.VmLogInfo;
import org.vitej.core.protocol.methods.response.VmlogInfosResponse;

import javax.annotation.PostConstruct;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.vite.dex.mm.constant.constants.MarketMiningConst.NODE_SERVER_URL;
import static org.vite.dex.mm.constant.constants.MarketMiningConst.TRADE_CONTRACT_ADDRESS;

@Component
@Slf4j
public class ViteCli {

    private static Vitej vitej;

    public static Vitej getViteClient() {
        return vitej;
    }

    @PostConstruct
    public void init() {
        vitej = new Vitej(new HttpService(NODE_SERVER_URL));
    }

    public AccountBlock getLatestAccountBlock() throws IOException {
        AccountBlock block = null;
        try {
            AccountBlockResponse response = vitej.getLatestAccountBlock(new Address(TRADE_CONTRACT_ADDRESS)).send();
            block = response.getResult();
        } catch (Exception e) {
            log.error("getHashOfLatestAccountBlock failed,the err:" + e);
            throw e;
        }
        return block;
    }


    public Long getLatestAccountHeight() throws IOException {
        AccountBlock block = null;
        try {
            AccountBlockResponse response = vitej.getLatestAccountBlock(new Address(TRADE_CONTRACT_ADDRESS)).send();
            block = response.getResult();
        } catch (Exception e) {
            log.error("getHashOfLatestAccountBlock failed,the err:" + e);
            throw e;
        }
        if (block == null) {
            throw new IOException("get contract height fail");
        }
        return block.getHeight();
    }

    /**
     * get events by height range,pay attention to the event sequence
     *
     * @param startHeight
     * @param endHeight
     * @param pageSize
     * @return
     * @throws IOException
     */
    public List<VmLogInfo> getEventsByHeightRange(long startHeight, long endHeight, int pageSize) throws IOException {
        if (startHeight > endHeight) {
            return Collections.emptyList();
        }

        List<VmLogInfo> events = new ArrayList<>();
        // Paging fetch
        int round = 0;
        while (true) {
            long from = startHeight + round * pageSize;
            long to = startHeight + (round + 1) * pageSize;
            if (from > endHeight) {
                return events;
            }
            if (to > endHeight + 1) {
                to = endHeight + 1;
            }

            // [from,to)
            VmLogFilter filter = new VmLogFilter(new Address(TRADE_CONTRACT_ADDRESS), from, to - 1);
            VmlogInfosResponse resp = vitej.getVmlogsByFilter(filter).send();
            List<VmLogInfo> eventsByPage = resp.getResult();
            if (eventsByPage == null || eventsByPage.isEmpty()) {
                break;
            }
            events.addAll(eventsByPage);
            round++;
        }

        return events;
    }

    public List<AccBlockVmLogs> getAccBlocksByHeightRange(long startHeight, long endHeight, int pageSize)
            throws IOException {
        List<VmLogInfo> vmlogs = getEventsByHeightRange(startHeight, endHeight, pageSize);
        if (CollectionUtils.isEmpty(vmlogs)) {
            return Collections.emptyList();
        }

        List<AccBlockVmLogs> result = Lists.newArrayList();
        Map<Long, List<VmLogInfo>> height2Vmlogs = vmlogs.stream()
                .collect(Collectors.groupingBy(VmLogInfo::getAccountBlockHeight));

        height2Vmlogs.forEach((height, logs) -> {
            AccBlockVmLogs accBlockVmLogs = new AccBlockVmLogs();
            accBlockVmLogs.setHeight(height);
            accBlockVmLogs.setHash(logs.get(0).getAccountBlockHashRaw());
            accBlockVmLogs.setVmLogs(logs);
            result.add(accBlockVmLogs);
        });

        result.sort((block0, block1) -> block0.getHeight().compareTo(block1.getHeight()));
        return result;
    }

    public List<AccountBlock> getAccountBlocksBelowCurrentHash(Hash currentHash, int cnt) throws IOException {
        AccountBlocksResponse resp = null;
        List<AccountBlock> result = null;
        try {
            resp = vitej.getAccountBlocks(new Address(TRADE_CONTRACT_ADDRESS), currentHash, null, cnt).send();
            result = resp.getResult();
        } catch (Exception e) {
            log.error("getAccountBlocksBelowCurrentHash failed,the err:" + e);
            throw e;
        }
        return result;
    }

    public List<TokenInfo> getTokenInfoList(int pageIdx, int pageSize) throws IOException {
        TokenInfoListWithTotalResponse resp = null;
        List<TokenInfo> result = null;
        try {
            resp = vitej.getTokenInfoList(pageIdx, pageSize).send();
            result = resp.getResult().getTokenInfoList();
        } catch (Exception e) {
            log.error("getTokenInfoList failed,the err:" + e);
            throw e;
        }
        return result;
    }

    public Long getContractChainHeight(long time) throws IOException {
        SnapshotBlock snapshotBlock = this.getSnapshotBlockBeforeTime(time);
        Long endHeight = snapshotBlock.getHeight();

        while (true) {
            Map<String, SnapshotBlock.HashHeight> snapshotContent = snapshotBlock.getSnapshotDataRaw();
            if (snapshotContent != null && snapshotContent.containsKey(TRADE_CONTRACT_ADDRESS)) {
                SnapshotBlock.HashHeight hashHeight = snapshotContent.get(TRADE_CONTRACT_ADDRESS);
                endHeight = hashHeight.getHeight();
                break;
            }
            endHeight--;
            snapshotBlock = this.getSnapshotBlockByHeight(endHeight);
        }
        return endHeight;
    }


    // TODOthe vitej interface has big bug!!!use alter
    public SnapshotBlock getSnapshotBlockByHeight(long height) throws IOException {
        SnapshotBlocksResponse resp = null;
        SnapshotBlock result = null;
        try {
            resp = vitej.getSnapshotBlocks(height, 1).send();
            // resp = vitej.getSnapshotBlockByHeight(height).send();
            result = resp.getResult().get(0);
        } catch (Exception e) {
            log.error("getSnapshotBlockByHeight failed,the err:" + e);
            throw e;
        }
        return result;
    }

    public TokenInfo getTokenInfo(String tokenId) throws IOException {
        TokenInfoResponse resp = null;
        TokenInfo result = null;
        try {
            resp = vitej.getTokenInfoById(TokenId.stringToTokenId(tokenId)).send();
            result = resp.getResult();
        } catch (Exception e) {
            log.error("getTokenInfo failed,the err:" + e);
            throw e;
        }
        return result;
    }

    public SnapshotBlock getSnapshotBlockBeforeTime(long beforeTime) throws IOException {
        SnapshotBlock snapshotBlock = null;
        try {
            CommonResponse response = vitej.commonMethod("ledger_getSnapshotBlockBeforeTime", beforeTime).send();
            snapshotBlock = JSONObject.parseObject(JSON.toJSONString(response.getResult()), SnapshotBlock.class);
        } catch (Exception e) {
            log.error("getSnapshotBlockBeforeTime failed,the err:" + e);
            throw e;
        }
        return snapshotBlock;
    }

    public OrderBookInfo getOrdersFromMarket(String tradeTokenId, String quoteTokenId, int sellStartIdx, int sellLimit,
            int buyStartIdx, int buyLimit) throws IOException {
        OrderBookInfo orderBookInfo = null;
        List<CurrentOrder> orders = null;
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("tradeToken", tradeTokenId);
            params.put("quoteToken", quoteTokenId);
            params.put("sellBegin", sellStartIdx);
            params.put("sellEnd", sellLimit);
            params.put("buyBegin", buyStartIdx);
            params.put("buyEnd", buyLimit);

            CommonResponse response = vitej.commonMethod("dextrade_getMarketOrders", params).send();
            if (response.getResult() == null) {
                return orderBookInfo;
            }
            JSONArray jsonArr = JSONObject.parseObject(JSON.toJSONString(response.getResult())).getJSONArray("orders");
            Long queryEndHeight = JSONObject.parseObject(JSON.toJSONString(response.getResult()))
                    .getJSONObject("queryEnd").getLong("height");
            if (jsonArr != null) {
                orders = jsonArr.toJavaList(CurrentOrder.class);
            }

            orderBookInfo = OrderBookInfo.fromCurrOrdersAndHeight(orders, queryEndHeight);
        } catch (Exception e) {
            log.error("getOrderBook failed,the err:" + e);
            throw e;
        }
        return orderBookInfo;
    }

    /**
    * get orders from specified order book of trade-pair
    * 
    * @param tradeTokenId
    * @param quoteTokenId
    * @param pageCnt
    * @return
    * @throws IOException
    */
    public OrderBookInfo getOrdersFromMarket(String tradeTokenId, String quoteTokenId, int pageCnt) throws IOException {
        List<OrderModel> orderModels = Lists.newLinkedList();
        List<Long> heights = Lists.newLinkedList();

        int idx = 0;
        while (true) {
            OrderBookInfo orderBookInfo = getOrdersFromMarket(tradeTokenId, quoteTokenId, pageCnt * idx,
                    pageCnt * (idx + 1), pageCnt * idx, pageCnt * (idx + 1));
            if (orderBookInfo == null || CollectionUtils.isEmpty(orderBookInfo.getCurrOrders())) {
                break;
            }

            orderBookInfo.getCurrOrders().forEach(currOrder -> {
                orderModels.add(OrderModel.fromCurrentOrder(currOrder, tradeTokenId, quoteTokenId));
            });

            heights.add(orderBookInfo.getCurrBlockheight());

            if (orderBookInfo.getCurrOrders().size() < pageCnt) {
                break;
            }

            idx++;
        }
        // System.out.println(heights);
        Long maxHeight = heights.stream().max(Long::compareTo).get();
        return OrderBookInfo.fromOrderModelsAndHeight(orderModels, maxHeight);
    }
}
