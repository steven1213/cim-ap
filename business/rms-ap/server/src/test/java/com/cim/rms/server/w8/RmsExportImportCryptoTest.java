package com.cim.rms.server.w8;

import com.cim.rms.server.device.DeviceAdminService;
import com.cim.rms.server.recipe.BodyStore;
import com.cim.rms.server.recipe.RecipePortService;
import com.cim.rms.server.recipe.RecipeService;
import com.cim.rms.server.recipe.RecipeVersion;
import com.cim.rms.server.recipe.RecipeVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.cim.spring.support.web.BizException;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W8 集成测试：D9 Body 加密（库内密文 + 明文兼容 + 解密读路径）与配方导入/导出
 * （文件级备份、SKIP/NEW_VERSION 冲突策略、坏 JSON 拒绝、逐条独立事务部分成功）。
 *
 * <p>密钥固定 32 字节（0x00..0x1F 的 Base64），走 {@code rms.crypto.body-key} 注入。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:rms-w8-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.auth.enabled=false",
        "rms.crypto.body-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RmsExportImportCryptoTest {

    @Autowired
    ObjectMapper om;
    @Autowired
    DeviceAdminService deviceAdminService;
    @Autowired
    RecipeService recipeService;
    @Autowired
    RecipeVersionRepository versionRepository;
    @Autowired
    BodyStore bodyStore;
    @Autowired
    RecipePortService portService;

    private static String typeId;
    private static String recipeId;
    private static String plainB64;

    static String b64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    static String sha256(String bodyB64) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(bodyB64));
        return HexFormat.of().formatHex(digest);
    }

    static final String JSON_BODY = """
            {
              "meta": {"process": "DEP", "author": "w8-test"},
              "params": {"TEMP": {"value": "75", "unit": "C", "min": "60", "max": "90"}},
              "steps": [{"no": 1, "name": "沉积", "params": ["TEMP"]}]
            }""";

    @Test
    @Order(1)
    void setupTypeAndRecipe() throws Exception {
        var t = deviceAdminService.createType("T-W8", "W8 机型", "W8 测试机型", null, null);
        typeId = t.id();
        plainB64 = b64(JSON_BODY);
        RecipeService.RecipeSummary s = recipeService.create("RCP-W8-CRYPTO", "加密测试配方",
                typeId, null, null, false, null,
                "JSON_STRUCTURED", plainB64, null);
        recipeId = s.id();
        assertThat(recipeId).isNotBlank();
    }

    @Test
    @Order(2)
    void bodyStoredEncryptedWithPlaintextHash() throws Exception {
        List<RecipeVersion> versions = recipeService.versionsOf(recipeId);
        assertThat(versions).hasSize(1);
        RecipeVersion v = versions.get(0);
        // 库内密文：body_enc=1 且存储值 != 明文
        assertThat(v.getBodyEnc()).isEqualTo(1);
        assertThat(v.getBodyBase64()).isNotEqualTo(plainB64);
        // IF-COM 语义不变：hash 对明文字节计算
        assertThat(v.getBodyHash()).isEqualTo(sha256(plainB64));
        // 读路径解密还原
        assertThat(bodyStore.readPlain(v)).isEqualTo(plainB64);
    }

    @Test
    @Order(3)
    void plaintextLegacyRowsStillReadable() {
        // 存量兼容：body_enc=0 的行直接读原值
        RecipeVersion legacy = new RecipeVersion();
        legacy.setRecipeId(recipeId);
        legacy.setVersionNo(99);
        legacy.setStatus(com.cim.rms.server.recipe.RecipeVersionStatus.OBSOLETE);
        legacy.setBodyFormat(com.cim.rms.server.recipe.BodyFormat.TEXT);
        legacy.setBodyBase64(plainB64);
        legacy.setBodyEnc(0);
        legacy.setBodyHash(sha256OrNull(plainB64));
        RecipeVersion saved = versionRepository.save(legacy);
        assertThat(bodyStore.readPlain(saved)).isEqualTo(plainB64);
    }

    private static String sha256OrNull(String b64) {
        try {
            return sha256(b64);
        } catch (Exception e) {
            return null;
        }
    }

    @Test
    @Order(4)
    void parsedGoesThroughDecryption() {
        // 取加密版本（Order(3) 插入过 v99 明文兼容行，倒序首个可能是它）
        RecipeVersion v = recipeService.versionsOf(recipeId).stream()
                .filter(x -> x.getBodyEnc() != null && x.getBodyEnc() == 1)
                .findFirst().orElseThrow();
        RecipeService.ParsedView view = recipeService.parsed(recipeId, v.getId());
        assertThat(view.ok()).isTrue();
        assertThat(view.parsed()).isNotNull();
        assertThat(view.parsed().params()).extracting(p -> p.key()).contains("TEMP");
    }

    @Test
    @Order(5)
    void exportReturnsPlaintextBody() throws Exception {
        RecipePortService.ExportFile f = portService.export(List.of(recipeId));
        assertThat(f.recipes()).hasSize(1);
        var er = f.recipes().get(0);
        assertThat(er.code()).isEqualTo("RCP-W8-CRYPTO");
        assertThat(er.deviceTypeCode()).isEqualTo("T-W8");
        assertThat(er.versions()).isNotEmpty();
        // 导出的是解密后的明文
        assertThat(er.versions().get(er.versions().size() - 1).bodyBase64()).isEqualTo(plainB64);
        assertThat(er.specs()).isEmpty();
    }

    @Test
    @Order(6)
    void importSkipStrategySkipsExistingCode() throws Exception {
        RecipePortService.ExportFile f = portService.export(List.of(recipeId));
        String json = om.writeValueAsString(f);
        RecipePortService.ImportReport r = portService.importFile(json, "SKIP");
        assertThat(r.created()).isZero();
        assertThat(r.updated()).isZero();
        assertThat(r.skipped()).isEqualTo(1);
        assertThat(r.errors()).isEmpty();
    }

    @Test
    @Order(7)
    void importNewVersionAppendsDraftWithoutActivation() throws Exception {
        RecipePortService.ExportFile f = portService.export(List.of(recipeId));
        String json = om.writeValueAsString(f);
        int before = recipeService.versionsOf(recipeId).size();
        RecipePortService.ImportReport r = portService.importFile(json, "NEW_VERSION");
        assertThat(r.updated()).isEqualTo(1);
        assertThat(r.errors()).isEmpty();
        List<RecipeVersion> versions = recipeService.versionsOf(recipeId);
        assertThat(versions).hasSize(before + 1);
        RecipeVersion nv = versions.get(0); // 版本号倒序，首个即新版本
        assertThat(nv.getStatus()).isEqualTo(com.cim.rms.server.recipe.RecipeVersionStatus.DRAFT);
        assertThat(nv.getBodyEnc()).isEqualTo(1);
        assertThat(nv.getBodyHash()).isEqualTo(sha256(plainB64));
        assertThat(nv.getChangeSummary()).contains("导入自文件");
        // 不会自动激活（备份恢复必须走签核链）
        assertThat(nv.getStatus()).isNotEqualTo(com.cim.rms.server.recipe.RecipeVersionStatus.ACTIVE);
    }

    @Test
    @Order(8)
    void importRejectsInvalidJson() {
        assertThatThrownBy(() -> portService.importFile("not-a-json{{{", "SKIP"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("JSON");
    }

    @Test
    @Order(9)
    void importUnknownDeviceTypeIsCollectedAsError() throws Exception {
        RecipePortService.ExportFile f = portService.export(List.of(recipeId));
        JsonNode root = om.readTree(om.writeValueAsString(f));
        ObjectNode rec = (ObjectNode) root.get("recipes").get(0);
        rec.put("code", "RCP-W8-NEW");
        rec.put("deviceTypeCode", "NO-SUCH-TYPE");
        rec.putNull("deviceTypeId");
        String json = om.writeValueAsString(root);
        RecipePortService.ImportReport r = portService.importFile(json, "SKIP");
        assertThat(r.created()).isZero();
        assertThat(r.errors()).hasSize(1);
        assertThat(r.errors().get(0)).contains("RCP-W8-NEW").contains("机型不存在");
    }
}
