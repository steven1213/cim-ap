package com.cim.iam.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.cim.iam.server.console.IamConsoleCatalog;
import com.cim.iam.server.console.IamConsoleCatalog.TextDef;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * 控制台文案目录（W4b）的数据质量门禁。
 *
 * <p>前端切 en-US 无中文残留的前提是：每个 {@link TextDef} 都有**双语成对**的 zh/en，
 * 且 en 文本本身不得含 CJK（否则 en-US 包里会混入中文）。本测试从数据源兜底这道约束。</p>
 */
class ConsoleTextCatalogTest {

    private static final Pattern CJK = Pattern.compile("[一-鿿]");

    @Test
    void everyTextDefHasBothLanguagesAndUniqueCode() {
        List<TextDef> texts = IamConsoleCatalog.texts();
        assertThat(texts).isNotEmpty();

        Set<String> seen = new HashSet<>();
        for (TextDef t : texts) {
            assertThat(t.code()).as("code 非空: %s", t).isNotBlank();
            assertThat(t.zh()).as("zh 非空: %s", t.code()).isNotBlank();
            assertThat(t.en()).as("en 非空: %s", t.code()).isNotBlank();
            assertThat(seen).as("code 唯一: %s", t.code()).doesNotContain(t.code());
            seen.add(t.code());
        }
    }

    @Test
    void englishTextContainsNoCjk() {
        for (TextDef t : IamConsoleCatalog.texts()) {
            assertThat(t.en())
                    .as("en 文本不得含中文（code=%s, en=%s）", t.code(), t.en())
                    .doesNotMatch(CJK);
        }
    }

    @Test
    void w4bStockPageNamespacesArePresent() {
        // 12 个存量业务页的命名空间必须都已落地，缺任一会导致对应页切 en-US 时回落到 humanize(key)
        String[] required = {
                "iam.users.", "iam.orgs.", "iam.dashboard.", "iam.settings.",
                "iam.orgGrants.", "iam.profile.", "iam.appMgmt.", "iam.sessions.",
                "iam.admissions.", "iam.lockouts.", "iam.audit.", "iam.rolesView.",
        };
        Set<String> codes = new HashSet<>();
        IamConsoleCatalog.texts().forEach(t -> codes.add(t.code()));

        for (String ns : required) {
            boolean has = codes.stream().anyMatch(c -> c.startsWith(ns));
            assertThat(has).as("命名空间 %s 必须有对应文案", ns).isTrue();
        }
    }
}
