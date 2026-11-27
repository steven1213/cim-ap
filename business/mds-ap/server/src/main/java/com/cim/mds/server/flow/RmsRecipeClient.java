package com.cim.mds.server.flow;

/**
 * RMS 配方客户端（解算契约的 EC 层通道，recipe-resolve-design.md §2/§3）。
 *
 * <p>MDS 解算在 recipe_rule=GENERATE / EXACT_MATCH 命中时调 RMS IF-M2
 * {@code GET /api/v1/rms/integration/recipe-ppid}：设备 code（≡ MDS equipment_code）+
 * 配方 code（= MDS logical recipeRef）→ PPID/腔体/生效版本/hash/资格。
 * status 词表与 RMS PpidResolveView 一致（RESOLVED / DEVICE_NOT_FOUND /
 * DEVICE_DISABLED / RECIPE_NOT_FOUND / NO_ACTIVE_VERSION / MAPPING_INACTIVE /
 * NOT_QUALIFIED / RMS_UNAVAILABLE[本地合成]）。</p>
 */
public interface RmsRecipeClient {

    Lookup lookup(String deviceCode, String recipeCode);

    /** 与 RMS IF-M2 返回字段对齐（versionNo/versionStatus/bodyHash 仅 RESOLVED 有值）。 */
    record Lookup(String status, String ppid, String chamberCode, Integer versionNo,
                  String versionStatus, String bodyHash, String reason) {
    }
}
