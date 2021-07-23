package org.vite.dex.mm.utils;


import org.vite.dex.mm.utils.decode.ViteAddress;
import org.vite.dex.mm.utils.decode.ViteToken;

public class ViteDataDecodeUtils {

    public static String getShowToken(byte[] original){
        if (original == null || original.length == 0) {
            return "";
        }
        ViteToken token = new ViteToken();
        token.setBytes(original);
        return token.hex();
    }

    public static String getShowAddress(byte[] original){
        if (original == null || original.length == 0) {
            return "";
        }
        ViteAddress address = new ViteAddress();
        address.setBytes(original);
        return address.hex();
    }

}
