package org.vite.dex.mm.utils.decode;

import org.spongycastle.util.encoders.Hex;

public class ViteToken {
    private static final String TokenTypeIdPrefix = "tti_";
    private static final int TokenTypeIdSize = 10;
    private static final int tokenTypeIdChecksumSize = 2;
    private static final int tokenTypeIdPrefixLen = TokenTypeIdPrefix.length();
    public static final int hexTokenTypeIdLength = tokenTypeIdPrefixLen + 2 * TokenTypeIdSize + 2 * tokenTypeIdChecksumSize;

    private byte[] tokenTypeId = new byte[TokenTypeIdSize];
    ;


    public void setBytes(byte[] b) {
        if (b.length != TokenTypeIdSize) {
            throw new RuntimeException(String.format("error tokentypeid size error %d", b.length));
        }
        System.arraycopy(b, 0, tokenTypeId, 0, b.length);
    }

    public String hex() {
        return TokenTypeIdPrefix + Hex.toHexString(tokenTypeId) + Hex.toHexString(hash(tokenTypeIdChecksumSize));
    }

    public byte[] hash(int digestLength) {
        final Blake2b blake2b = Blake2b.Digest.newInstance(digestLength);
        blake2b.update(tokenTypeId);
        return blake2b.digest();
    }

    public static String hexToTokenTypeId(String hexStr) {
        byte[] tokenBytes = Hex.decode(hexStr);
        ViteToken token = new ViteToken();
        token.setBytes(tokenBytes);
        return token.hex();
    }
}
