package com.cim.rms.server.w10;

import com.cim.rms.server.device.DeviceAdminService;
import com.cim.rms.server.recipe.RecipeService;
import com.cim.rms.server.recipe.RecipeValidationService;
import com.cim.rms.server.template.BodyTemplateService;
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
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W10 集成测试：Body 模板（骨架下发 / 强制键校验拦截 / enforced 机型匹配与通用回退 /
 * 模板 CRUD 冲突守卫 / 保存前预检 previewParse）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:rms-w10-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RmsW10BodyTemplateTest {

    @Autowired
    DeviceAdminService deviceAdmin;
    @Autowired
    RecipeService recipeService;
    @Autowired
    RecipeValidationService validationService;
    @Autowired
    BodyTemplateService templateService;

    private static String typeId;
    private static String templateId;

    static String b64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    static final String SKELETON = """
            {"meta":{"process":"DEP"},"params":{"TEMP":{"value":"70"},"PRESSURE":{"value":"1.2"}},"steps":[]}""";

    static String bodyMissingPressure() {
        return b64("""
                {"meta":{"process":"DEP"},"params":{"TEMP":{"value":"75"}},"steps":[]}""");
    }

    static String bodyComplete() {
        return b64("""
                {"meta":{"process":"DEP"},"params":{"TEMP":{"value":"75"},"PRESSURE":{"value":"1.2"}},"steps":[]}""");
    }

    @Test
    @Order(1)
    void setupTypeAndTemplate() {
        typeId = deviceAdmin.createType("T-W10", "W10 机型", null, null, null).id();
        BodyTemplateService.TemplateSummary t = templateService.create(new BodyTemplateService.TemplateUpsert(
                "TPL-W10-DEP", "W10 沉积模板", "JSON_STRUCTURED", typeId, true, SKELETON,
                List.of(new BodyTemplateService.TemplateItemUpsert("TEMP", true, "70", "C"),
                        new BodyTemplateService.TemplateItemUpsert("PRESSURE", true, "1.2", "Torr"),
                        new BodyTemplateService.TemplateItemUpsert("FLOW", false, null, "sccm"))));
        templateId = t.id();
        assertThat(templateId).isNotBlank();
    }

    @Test
    @Order(2)
    void duplicateCodeRejected() {
        assertThatThrownBy(() -> templateService.create(new BodyTemplateService.TemplateUpsert(
                "TPL-W10-DEP", "重名模板", "JSON_STRUCTURED", null, true, null, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    @Order(3)
    void skeletonReturnsBase64BodyAndItems() {
        BodyTemplateService.Skeleton s = templateService.skeleton(templateId);
        assertThat(s.bodyFormat()).isEqualTo("JSON_STRUCTURED");
        assertThat(new String(Base64.getDecoder().decode(s.bodyBase64()), StandardCharsets.UTF_8))
                .isEqualTo(SKELETON);
        assertThat(s.items()).hasSize(3);
    }

    @Test
    @Order(4)
    void previewValidateFlagsMissingMandatoryKey() {
        RecipeValidationService.PreviewResult r =
                validationService.previewParse(typeId,
                        com.cim.rms.server.recipe.BodyFormat.JSON_STRUCTURED, bodyMissingPressure());
        assertThat(r.ok()).isFalse();
        assertThat(r.templateViolations()).extracting(
                        com.cim.rms.server.template.BodyTemplateService.TemplateViolation::paramKey)
                .containsExactly("PRESSURE");
        assertThat(r.errors()).anyMatch(e -> e.startsWith("TEMPLATE_MISSING_KEY"));
        // 完整 body 通过
        RecipeValidationService.PreviewResult ok =
                validationService.previewParse(typeId,
                        com.cim.rms.server.recipe.BodyFormat.JSON_STRUCTURED, bodyComplete());
        assertThat(ok.ok()).isTrue();
        assertThat(ok.templateViolations()).isEmpty();
    }

    @Test
    @Order(5)
    void versionValidateBlocksMissingKey() {
        String recipeId = recipeService.create("RCP-W10-A", "模板校验配方", typeId, null, null, false,
                null, "JSON_STRUCTURED", bodyMissingPressure(), null).id();
        String v1 = recipeService.versionsOf(recipeId).get(0).getId();
        RecipeValidationService.ValidationResult bad = validationService.validate(recipeId, v1);
        // 良构（Body 本身没坏）不受模板缺键影响；缺键拦截体现在「可执行」
        assertThat(bad.wellFormed()).isTrue();
        assertThat(bad.executable()).isFalse();
        assertThat(bad.errors()).anyMatch(e -> e.startsWith("TEMPLATE_MISSING_KEY"));
        assertThat(bad.templateViolations()).extracting(
                        com.cim.rms.server.template.BodyTemplateService.TemplateViolation::paramKey)
                .containsExactly("PRESSURE");
        // 修复后通过（强制键齐了，无规格 → checks 空）
        recipeService.updateVersionBody(recipeId, v1,
                com.cim.rms.server.recipe.BodyFormat.JSON_STRUCTURED, bodyComplete(),
                bad.parsed() == null ? null : null, "补齐强制键");
        RecipeValidationService.ValidationResult good = validationService.validate(recipeId, v1);
        assertThat(good.wellFormed()).isTrue();
        assertThat(good.executable()).isTrue();
    }

    @Test
    @Order(6)
    void genericScopedTemplateFallback() {
        // 通用模板（scope 空）：其他机型也命中
        templateService.create(new BodyTemplateService.TemplateUpsert(
                "TPL-W10-GEN", "通用模板", "TEXT", null, true, "@meta x", List.of()));
        String otherType = deviceAdmin.createType("T-W10-B", "W10-B 机型", null, null, null).id();
        List<BodyTemplateService.TemplateViolation> none =
                templateService.validateKeys(otherType, Map.of("ANY", "1"));
        assertThat(none).isEmpty(); // 通用模板无条目 → 无违规
    }

    @Test
    @Order(7)
    void deleteTemplate() {
        String tid = templateService.create(new BodyTemplateService.TemplateUpsert(
                "TPL-W10-DEL", "待删模板", "TEXT", null, false, "x", List.of())).id();
        templateService.delete(tid);
        assertThatThrownBy(() -> templateService.get(tid)).isInstanceOf(BizException.class);
    }
}
