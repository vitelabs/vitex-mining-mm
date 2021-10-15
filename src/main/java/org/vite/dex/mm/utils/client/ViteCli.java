package org.vite.dex.mm.utils.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;
import org.vite.dex.mm.entity.AccBlockVmLogs;
import org.vite.dex.mm.entity.CurrentOrder;
import org.vite.dex.mm.entity.OrderBookInfo;
import org.vite.dex.mm.entity.OrderModel;
import org.vite.dex.mm.orderbook.Tokens;
import org.vitej.core.protocol.HttpService;
import org.vitej.core.protocol.Vitej;
import org.vitej.core.protocol.methods.Address;
import org.vitej.core.protocol.methods.Hash;
import org.vitej.core.protocol.methods.TokenId;
import org.vitej.core.protocol.methods.enums.EBlockType;
import org.vitej.core.protocol.methods.request.Request;
import org.vitej.core.protocol.methods.request.TransactionParams;
import org.vitej.core.protocol.methods.request.VmLogFilter;
import org.vitej.core.protocol.methods.response.AccountBlock;
import org.vitej.core.protocol.methods.response.AccountBlockResponse;
import org.vitej.core.protocol.methods.response.AccountBlocksResponse;
import org.vitej.core.protocol.methods.response.CommonResponse;
import org.vitej.core.protocol.methods.response.EmptyResponse;
import org.vitej.core.protocol.methods.response.SnapshotBlock;
import org.vitej.core.protocol.methods.response.SnapshotBlocksResponse;
import org.vitej.core.protocol.methods.response.TokenInfo;
import org.vitej.core.protocol.methods.response.TokenInfoListWithTotalResponse;
import org.vitej.core.protocol.methods.response.TokenInfoResponse;
import org.vitej.core.protocol.methods.response.VmLogInfo;
import org.vitej.core.protocol.methods.response.VmlogInfosResponse;
import org.vitej.core.utils.abi.Abi;
import org.vitej.core.wallet.KeyPair;
import org.vitej.core.wallet.Wallet;

import javax.annotation.PostConstruct;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.vite.dex.mm.constant.constants.MarketMiningConst.NODE_SERVER_URL;
import static org.vite.dex.mm.constant.constants.MarketMiningConst.TRADE_CONTRACT_ADDRESS;

@Component
@Slf4j
public class ViteCli {

    private Vitej vitej;

    @PostConstruct
    public void init() {
        vitej = new Vitej(new HttpService(NODE_SERVER_URL));

    }

    /**
     * get keyPair of wallet from mnemonic
     * 
     * @param mnemonic
     * @return
     * @throws IOException
     */
    public KeyPair getKeyPair(String mnemonic, int idx) throws IOException {
        Wallet wallet = new Wallet(mnemonic);
        return wallet.deriveKeyPair(idx);
    }

    /**
     * call contract`s specified method
     * 
     * @param keyPair
     * @param fromAddress
     * @param toAddress
     * @param tokenId
     * @param amount
     * @param abiStr
     * @param methodName
     * @param methodParams
     * @return
     * @throws IOException
     */
    public String callContract(KeyPair keyPair, String fromAddress, String toAddress, String tokenId, String amount,
            String abiStr, String methodName, List<Object> methodParams) throws IOException {

        Abi abi = Abi.fromJson(abiStr);
        byte[] callContractData = abi.encodeFunction(methodName, methodParams.toArray());
        Request<?, EmptyResponse> request = vitej.sendTransaction(keyPair,
                new TransactionParams().setBlockType(EBlockType.SEND_CALL.getValue())
                        // smart contract address
                        .setToAddress(new Address(toAddress))
                        // transfer amount
                        .setAmount(new BigInteger(amount))
                        // transfer token id
                        .setTokenId(TokenId.stringToTokenId(tokenId)).setData(callContractData),
                true);

        Hash sendBlockHash = ((TransactionParams) request.getParams().get(0)).getHashRaw();
        EmptyResponse response = request.send();
        if (response.getError() != null) {
            log.error("settle reward failed, toAddress:{},error:{} ", toAddress, response.getError().getMessage());
            throw new RuntimeException(response.getError().getMessage());
        }

        return sendBlockHash.toString();
    }

    public int getCurrentCycleKey() throws IOException {
        int cycleKey = 0;
        try {
            CommonResponse response = vitej.commonMethod("sbpstats_time2Index", null, 2).send();
            cycleKey = (Integer) response.getResult();
        } catch (Exception e) {
            log.error("getCurrentCycleKey failed,the err:" + e);
            throw e;
        }
        return cycleKey;
    }

    public AccountBlock getLatestAccountBlock() throws IOException {
        AccountBlock block = null;
        try {
            AccountBlockResponse response = vitej.getLatestAccountBlock(new Address(TRADE_CONTRACT_ADDRESS)).send();
            block = response.getResult();
        } catch (Exception e) {
            log.error("getLatestAccountBlock failed,the err:" + e);
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
     * get events by height range [start,end]
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

    public List<AccountBlock> getAccoutBlocksByHeightRangePaging(long startHeight, long endHeight, int pageSize)
            throws IOException {
        if (startHeight > endHeight) {
            return Collections.emptyList();
        }

        List<AccountBlock> res = new ArrayList<>();
        // Paging fetch
        int round = 0;
        while (true) {
            long from = startHeight + round * pageSize;
            long to = startHeight + (round + 1) * pageSize - 1;
            if (from > endHeight) {
                return res;
            }
            if (to > endHeight) {
                to = endHeight;
            }

            // [from,to]
            List<AccountBlock> blocks = getAccountBlocksByHeightRange(from, to);
            if (blocks == null || blocks.isEmpty()) {
                break;
            }
            res.addAll(blocks);
            round++;
        }

        return res;
    }

    /**
     * get AccBlockVmLogs between startHeight and endHeight [start, end]
     * 
     * @param startHeight
     * @param endHeight
     * @param pageSize
     * @return
     * @throws IOException
     */
    public List<AccBlockVmLogs> getAccBlockVmLogsByHeightRange(long startHeight, long endHeight, int pageSize)
            throws IOException {
        List<VmLogInfo> vmlogs = getEventsByHeightRange(startHeight, endHeight, pageSize);
        if (CollectionUtils.isEmpty(vmlogs)) {
            return Collections.emptyList();
        }

        List<AccBlockVmLogs> result = Lists.newArrayList();
        Map<Long, List<VmLogInfo>> blockHeight2Vmlogs = vmlogs.stream()
                .collect(Collectors.groupingBy(VmLogInfo::getAccountBlockHeight));

        blockHeight2Vmlogs.forEach((height, logs) -> {
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

    public List<AccountBlock> getAccountBlocksByHeightRange(long startHeight, long endHeight) throws IOException {
        List<AccountBlock> result = null;
        try {
            CommonResponse response = vitej.commonMethod("ledger_getAccountBlocksByHeightRange", TRADE_CONTRACT_ADDRESS,
                    startHeight, endHeight).send();
            result = JSON.parseArray(JSON.toJSONString(response.getResult()), AccountBlock.class);
        } catch (Exception e) {
            log.error("getAccountBlocksByHeightRange failed,the err:" + e);
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

    public Tokens getAllTokenInfos() throws IOException {
        List<TokenInfo> allTokenInfos = new ArrayList<>();
        int pageIdx = 0;
        int pageSize = 100;

        while (true) {
            List<TokenInfo> tokenInfos = getTokenInfoList(pageIdx, pageSize);
            if (CollectionUtils.isEmpty(tokenInfos)) {
                break;
            }
            allTokenInfos.addAll(tokenInfos);
            pageIdx++;
        }

        Map<String, TokenInfo> tokenId2TokenInfoMap = allTokenInfos.stream()
                .collect(Collectors.toMap(TokenInfo::getTokenIdRaw, tokenInfo -> tokenInfo, (k1, k2) -> k1));
        return new Tokens(tokenId2TokenInfoMap);
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

    // TODOthe vitej api has a bug!use the alternative ones
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
     * get orders from order book in specified trade-pair
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
        Long maxHeight = 0l;
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

        if (heights.size() > 0) {
            maxHeight = heights.stream().max(Long::compareTo).get();
        }
        return OrderBookInfo.fromOrderModelsAndHeight(orderModels, maxHeight);
    }

    /**
     * get all account blocks whose created time is greater than the startTime and
     * its hash is lower than endHash
     * 
     * @param startTime
     * @param endHash
     * @return
     * @throws IOException
     */
    private List<AccountBlock> getAccountBlocks(long startTime, Hash endHash) throws IOException {
        List<AccountBlock> blocks = Lists.newArrayList();
        while (true) {
            // the result contains the endHash block [startHash, endHash]
            List<AccountBlock> result = getAccountBlocksBelowCurrentHash(endHash, 1000);
            if (CollectionUtils.isEmpty(result)) {
                break;
            }

            // sort blocks in descending order of height
            result.sort((block0, block1) -> block1.getHeight().compareTo(block0.getHeight()));
            for (AccountBlock aBlock : result) {
                if (aBlock.getTimestampRaw() >= startTime) {
                    blocks.add(aBlock);
                    endHash = aBlock.getHash();
                } else {
                    // ignore
                    return blocks;
                }
            }
        }
        return blocks;
    }

    /**
     * get the blocks from previous timepoint of hash to currentHash
     * 
     * @param startTime
     * @param currentHash
     * @return
     * @throws IOException
     */
    public Map<String, AccountBlock> getAccountBlockMap(long startTime, Hash currentHash) throws IOException {
        if (currentHash == null) {
            return null;
        }

        Map<String, AccountBlock> accountBlockMap = Maps.newHashMap();
        List<AccountBlock> blocks = getAccountBlocks(startTime, currentHash);
        if (CollectionUtils.isEmpty(blocks)) {
            return null;
        }
        blocks = blocks.stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(o -> o.getHeight()))),
                        ArrayList::new));
        accountBlockMap = blocks.stream()
                .collect(Collectors.toMap(AccountBlock::getHashRaw, block -> block, (b0, b1) -> b0));
        return accountBlockMap;
    }

    public Map<String, AccountBlock> getAccountBlockMap(long startHeight, long endHeight) throws IOException {
        List<AccountBlock> blocks = getAccoutBlocksByHeightRangePaging(startHeight, endHeight, 2000);
        if (CollectionUtils.isEmpty(blocks)) {
            return null;
        }

        Map<String, AccountBlock> accountBlockMap = Maps.newHashMap();
        accountBlockMap = blocks.stream()
                .collect(Collectors.toMap(AccountBlock::getHashRaw, block -> block, (b0, b1) -> b0));

        return accountBlockMap;
    }

    public Map<String, String> getInvitee2InviterMap(List<String> invitees) throws IOException {
        Map<String, String> invitee2InviterMap = new HashMap<>();
        try {
            CommonResponse response = vitej.commonMethod("dex_getInviter", invitees).send();
            invitee2InviterMap = JSON.parseObject(JSON.toJSONString(response.getResult()), Map.class);
        } catch (Exception e) {
            log.error("getInvitee2InviterMap failed,the err:" + e);
            throw e;
        }
        return invitee2InviterMap;
    }

}
