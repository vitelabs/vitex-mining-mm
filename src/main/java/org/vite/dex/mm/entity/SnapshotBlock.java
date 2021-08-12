package org.vite.dex.mm.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SnapshotBlock {
    private String hash;

    private String prevHash;

    private String height;

    private Map<String, HashHeight> snapshotContent;

    @Data
    public static class HashHeight {
        private Long height;
        private String hash;
    }
}