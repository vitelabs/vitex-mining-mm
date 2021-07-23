package org.vite.dex.mm.utils.decode;

import org.spongycastle.util.encoders.Hex;

public class ViteAddress {

    private static String AddressPrefix = "vite_";
    private static Integer AddressSize = 21;
    private static Integer AddressCoreSize = 20;
    private static Integer addressChecksumSize = 5;
    private static Integer addressPrefixLen = AddressPrefix.length();
    private static Integer hexAddrCoreLen = 2 * AddressCoreSize;
    private static Integer hexAddrChecksumLen = 2 * addressChecksumSize;
    private static Integer hexAddressLength = addressPrefixLen + hexAddrCoreLen + hexAddrChecksumLen;

    private static byte UserAddrByte = 0;
    private static byte ContractAddrByte = 1;

    private byte[] address = new byte[AddressSize];

    public void setBytes(byte[] b) {
        if (b.length != AddressSize) {
            throw new RuntimeException(String.format("error address size error %d", b.length));
        }
        System.arraycopy(b, 0, address, 0, b.length);
    }

    public String hex() {
        byte[] coreAdrr = new byte[AddressCoreSize];
        System.arraycopy(address, 0, coreAdrr, 0, AddressCoreSize);
        if (address[AddressCoreSize] == UserAddrByte) {
            return AddressPrefix
                    + Hex.toHexString(coreAdrr)
                    + Hex.toHexString(hash(addressChecksumSize, coreAdrr));
        } else if (address[AddressCoreSize] == ContractAddrByte) {
            byte[] hash = hash(addressChecksumSize, coreAdrr);
            return AddressPrefix + Hex.toHexString(coreAdrr) + Hex.toHexString(LDI(hash));
        } else {
            throw new RuntimeException("error data");
        }
    }

    public byte[] hash(int digestLength, byte[]... data) {
        final Blake2b blake2b = Blake2b.Digest.newInstance(digestLength);
        for (byte[] item : data) {
            blake2b.update(item);
        }
        return blake2b.digest();
    }

    public byte[] LDI(byte[] slice) {
        byte[] result = new byte[slice.length];

        for (int i = 0; i < slice.length; i++) {
            byte temp = slice[i];
            result[i] = (byte) ~temp;
        }
        return result;
    }
}
