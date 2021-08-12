package org.vite.dex.mm.utils;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.vite.dex.mm.entity.SnapshotBlock;
import org.vite.dex.mm.utils.rpc.JsonRpc;

import java.util.Arrays;

import static org.vite.dex.mm.constant.constants.MMConst.BACKUP_NODE_SERVER_URL;

public class ApiCollectionUtils {

    //查询离当前时间最近的快照块
    public static SnapshotBlock getSnapshotBlockBeforeTime(Long beforeTime) throws Exception {
        JsonRpc jsonRpc = new JsonRpc(BACKUP_NODE_SERVER_URL);
        JRGetBlock response = jsonRpc.sendJson(new JsonRpc.JsonRpcRequest<Long>("ledger_getSnapshotBlockBeforeTime", Arrays.asList(beforeTime)),
                JRGetBlock.class);
        System.out.println(response);
        return response.getResult();
    }
    
    @Getter
    @Setter
    @ToString
    public static class JRGetBlock extends JsonRpc.JsonRpcResponse {
        public SnapshotBlock result;
    }
}
