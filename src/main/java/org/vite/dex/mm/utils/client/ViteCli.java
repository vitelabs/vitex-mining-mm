package org.vite.dex.mm.utils.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vitej.core.protocol.HttpService;
import org.vitej.core.protocol.Vitej;
import org.vitej.core.protocol.methods.Address;
import org.vitej.core.protocol.methods.Hash;
import org.vitej.core.protocol.methods.request.VmLogFilter;
import org.vitej.core.protocol.methods.response.AccountBlock;
import org.vitej.core.protocol.methods.response.AccountBlockResponse;
import org.vitej.core.protocol.methods.response.AccountBlocksResponse;
import org.vitej.core.protocol.methods.response.CommonResponse;
import org.vitej.core.protocol.methods.response.VmLogInfo;
import org.vitej.core.protocol.methods.response.VmlogInfosResponse;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import java.io.IOException;
import java.util.List;

import static org.vite.dex.mm.constant.constants.MMConst.NODE_SERVER_URL;
import static org.vite.dex.mm.constant.constants.MMConst.TRADE_CONTRACT_ADDRESS;

@Resource
public class ViteCli {
    private static final Logger logger = LoggerFactory.getLogger(ViteCli.class);

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
            AccountBlockResponse response = vitej.getLatestAccountBlock(new Address(TRADE_CONTRACT_ADDRESS))
                    .send();
            block = response.getResult();
        } catch (Exception e) {
            logger.error("getHashOfLatestAccountBlock failed,the err:" + e);
            throw e;
        }
        return block;
    }

    // todo page 
    public List<VmLogInfo> getEventsByHeightRange(long startHeight, long endHeight) throws IOException {
        VmlogInfosResponse resp = null;
        try {
            VmLogFilter filter = new VmLogFilter(new Address(TRADE_CONTRACT_ADDRESS),
                    startHeight, endHeight);
            resp = vitej.getVmlogsByFilter(filter).send();
        } catch (Exception e) {
            logger.error("getEventsByHeightRange failed,the err:" + e);
            throw e;
        }
        return resp.getResult();
    }

    public List<AccountBlock> getAccountBlocksBelowCurrentHash(Hash currentHash, int cnt) throws IOException {
        AccountBlocksResponse resp = null;
        List<AccountBlock> result = null;
        try {
            resp = vitej.getAccountBlocks(new Address(TRADE_CONTRACT_ADDRESS), currentHash,
                    null, cnt)
                    .send();
            result = resp.getResult();
        } catch (Exception e) {
            logger.error("getEventsByHeightRange failed,the err:" + e);
            throw e;
        }
        return result;
    }

    public CommonResponse getOrdersFromMarket(String tradeTokenId, String quoteTokenId, boolean side, int startIdx,
            int limit) throws IOException {
        CommonResponse response = null;
        try {
            response = vitej.commonMethod("dextrade_getOrdersFromMarket",
                    tradeTokenId, quoteTokenId, side, startIdx, limit).send();
        } catch (Exception e) {
            logger.error("getEventsByHeightRange failed,the err:" + e);
            throw e;
        }
        return response;

    }
}
