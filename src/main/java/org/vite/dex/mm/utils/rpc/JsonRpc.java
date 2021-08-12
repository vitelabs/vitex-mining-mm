package org.vite.dex.mm.utils.rpc;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;
import org.vite.dex.mm.utils.HttpUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class JsonRpc {
    private static final Logger logger = LoggerFactory.getLogger(JsonRpc.class);

    String rpcUrl;

    public JsonRpc(String rpcUrl) {
        this.rpcUrl = rpcUrl;
    }

    static String encodePrivateKeyString(String s) {
        return Hex.toHexString(s.getBytes());
    }

    static String decodePrivateKeyString(String s) {
        return new String(Hex.decode(s));
    }

    public <RespType extends JsonRpcResponse> RespType sendJson(
            JsonRpcRequest req, Class<RespType> respClazz) throws Exception {
        String in = null;
        try {
            in = sendPost(req);
            logger.info("response:{}", in);
            RespType resp = JSONObject.parseObject(in, respClazz);
            resp.throwIfError();
            return resp;
        } catch (Exception e) {
            throw new Exception("Error processing JSON (Received: " + in + ")", e);
        }
    }

    public String sendPost(JsonRpcRequest jsonRpcRequest) throws RuntimeException {
        String requestParam = JSONObject.toJSONString(jsonRpcRequest);
        return HttpUtils.postJson(rpcUrl, requestParam);
    }


    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class JsonRpcRequest<ParamT> {
        private static AtomicInteger idCount = new AtomicInteger(0);

        public final String jsonrpc = "2.0";
        public int id = idCount.incrementAndGet();
        public String method;
        public List<ParamT> params;

        public JsonRpcRequest(String method, List<ParamT> params) {
            this.method = method;
            this.params = params;
        }
    }

    public static class JsonRpcResponse {
        public int id;
        public String jsonrpc;
        public Error error;

        public void throwIfError() {
            if (error != null) {
                throw new RuntimeException(
                        "JSON RPC returned error (" + error.code + "): " + error.message);
            }
        }

        public static class Error {
            public int code;
            public String message;
        }
    }

    @Setter
    @Getter
    @ToString
    public static class SimpleResponse extends JsonRpcResponse {
        public String result;
    }

}
