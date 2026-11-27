package com.cim.rms.server.m2;

import com.cim.rms.server.device.DeviceAdminService;
import com.cim.rms.server.integration.IntegrationService;
import com.cim.rms.server.integration.ToolRecipeMapService;
import com.cim.rms.server.recipe.BodyFormat;
import com.cim.rms.server.recipe.RecipeService;
import com.cim.rms.server.recipe.RecipeVersion;
import com.cim.rms.server.recipe.RecipeVersionStatus;
import com.cim.rms.server.qualification.QualificationService;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IF-M2 PPID 解析端点测试（recipe-resolve-design.md §3）：
 * RESOLVED 全字段（ppid/腔体/版本/hash）→ 各 BLOCKED 分支（设备/配方/映射/资格）→ 密钥守门。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:rms-m2-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RmsRecipePpidResolveTest {

    @Autowired IntegrationService integrationService;
    @Autowired DeviceAdminService deviceAdminService;
    @Autowired RecipeService recipeService;
    @Autowired ToolRecipeMapService mapService;
    @Autowired QualificationService qualificationService;
    @Autowired MockMvc mockMvc;

    static final String KEY = "dev-integration-key-change-me";
    static final String BODY = "M2-BODY-1";

    static String recipeCode;
    static String noGrantRecipe;
    static String noMapRecipe;

    @Test
    @Order(1)
    void seedData() {
        var type = deviceAdminService.createType("DT-M2", "机型M2", null, null, null);
        var factory = deviceAdminService.createArea("FAC-M2", "工厂M2", null, null, null);
        var area = deviceAdminService.createArea("AR-M2", "区域M2", factory.id(), null, null);
        var dev = deviceAdminService.createDevice("EQP-M2-01", "机台M2", type.id(), area.id(), null, null);
        var chamber = deviceAdminService.createChamber(dev.id(), "CH-A", "腔体A", 1, null);

        var recipe = recipeService.create("RCP-M2", "配方M2", type.id(), area.id(), null,
                false, null, BodyFormat.TEXT.name(),
                Base64.getEncoder().encodeToString(BODY.getBytes(StandardCharsets.UTF_8)), null);
        RecipeVersion v = recipeService.newVersion(recipe.id(), BodyFormat.TEXT,
                Base64.getEncoder().encodeToString(BODY.getBytes(StandardCharsets.UTF_8)),
                null, null, "init");
        recipeService.activate(recipe.id(), v.getId());
        mapService.register(dev.id(), null, "PPID-M2-A", recipe.id(), chamber.id());
        qualificationService.grant(recipe.id(), null, v.getId(), dev.id(), null, null,
                "M2 测试授权", null, null);
        recipeCode = "RCP-M2";

        // 无资格配方（有版本+映射，未 grant）
        var r2 = recipeService.create("RCP-M2-NOQUAL", "配方M2无资格", type.id(), area.id(), null,
                false, null, BodyFormat.TEXT.name(),
                Base64.getEncoder().encodeToString(BODY.getBytes(StandardCharsets.UTF_8)), null);
        RecipeVersion v2 = recipeService.newVersion(r2.id(), BodyFormat.TEXT,
                Base64.getEncoder().encodeToString(BODY.getBytes(StandardCharsets.UTF_8)),
                null, null, "init");
        recipeService.activate(r2.id(), v2.getId());
        mapService.register(dev.id(), null, "PPID-M2-NOQUAL", r2.id(), null);
        noGrantRecipe = "RCP-M2-NOQUAL";

        // 无映射配方（有版本，无 map）
        var r3 = recipeService.create("RCP-M2-NOMAP", "配方M2无映射", type.id(), area.id(), null,
                false, null, BodyFormat.TEXT.name(),
                Base64.getEncoder().encodeToString(BODY.getBytes(StandardCharsets.UTF_8)), null);
        RecipeVersion v3 = recipeService.newVersion(r3.id(), BodyFormat.TEXT,
                Base64.getEncoder().encodeToString(BODY.getBytes(StandardCharsets.UTF_8)),
                null, null, "init");
        recipeService.activate(r3.id(), v3.getId());
        noMapRecipe = "RCP-M2-NOMAP";
    }

    private IntegrationService.PpidResolveView resolve(String deviceCode, String recipeCode) {
        return integrationService.resolvePpid(new IntegrationService.PpidResolveReq(deviceCode, recipeCode));
    }

    @Test
    @Order(2)
    void resolvedFullSnapshot() {
        var v = resolve("EQP-M2-01", recipeCode);
        assertThat(v.status()).isEqualTo("RESOLVED");
        assertThat(v.ppid()).isEqualTo("PPID-M2-A");
        assertThat(v.chamberCode()).isEqualTo("CH-A");
        assertThat(v.versionNo()).isNotNull().isGreaterThanOrEqualTo(1);
        assertThat(v.versionStatus()).isEqualTo(RecipeVersionStatus.ACTIVE.name());
        assertThat(v.bodyHash()).isNotBlank();
        assertThat(v.reason()).isNull();
    }

    @Test
    @Order(3)
    void blockedBranches() {
        assertThat(resolve("NO-SUCH-TOOL", recipeCode).status()).isEqualTo("DEVICE_NOT_FOUND");
        assertThat(resolve("EQP-M2-01", "NO-SUCH-RECIPE").status()).isEqualTo("RECIPE_NOT_FOUND");
        assertThat(resolve("EQP-M2-01", noMapRecipe).status()).isEqualTo("MAPPING_INACTIVE");
        var q = resolve("EQP-M2-01", noGrantRecipe);
        assertThat(q.status()).isEqualTo("NOT_QUALIFIED");
        assertThat(q.reason()).isNotBlank();
    }

    @Test
    @Order(4)
    void apiKeyGuard() throws Exception {
        mockMvc.perform(get("/api/v1/rms/integration/recipe-ppid")
                        .param("deviceCode", "EQP-M2-01").param("recipeCode", recipeCode))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/rms/integration/recipe-ppid")
                        .param("deviceCode", "EQP-M2-01").param("recipeCode", recipeCode)
                        .header("X-Integration-Key", "wrong-key"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/rms/integration/recipe-ppid")
                        .param("deviceCode", "EQP-M2-01").param("recipeCode", recipeCode)
                        .header("X-Integration-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                .andExpect(jsonPath("$.data.ppid").value("PPID-M2-A"));
    }
}
