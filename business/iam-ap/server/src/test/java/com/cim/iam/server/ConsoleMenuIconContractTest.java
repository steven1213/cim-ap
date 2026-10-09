package com.cim.iam.server;

import com.cim.iam.server.console.IamConsoleCatalog;
import com.cim.iam.server.console.IamConsoleCatalog.MenuDef;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 跨端契约：**菜单种子用到的图标标识，必须全部存在于前端图标注册表**。
 *
 * <p>背景：菜单表刻意不硬编码图标组件，只存一个**字符串标识**（`sys_menu.icon`），由前端
 * `lib/iconRegistry.ts` 解析成组件（见 `console-menu-perm-i18n.md` §3.4）。这条「字符串约定」
 * 横跨 Java 与 TypeScript 两侧——后端加菜单时写了一个前端没有的标识，不会编译报错、不会启动失败，
 * 只会在侧栏静默回落成兜底图标（`IconFallback`）。本测试把这条约定变成**可失败的断言**。</p>
 *
 * <p>做法：直接读前端源文件解析出注册表键集，与 {@link IamConsoleCatalog#menus()} 的 `icon` 取值比对。
 * 之所以读源码而不是读构建产物：注册表是「编译期常量表」，源码即契约真相；
 * 读 `dist` 需要先跑前端构建，会把 Java 单测绑到 Node 工具链上，得不偿失。</p>
 *
 * <p>⚠️ 若本测试报「找不到前端注册表文件」，通常是构建工具的工作目录与预期不符——
 * 断言消息里会打印当前工作目录，据此修正 {@link #registryCandidates()} 的候选路径。</p>
 */
class ConsoleMenuIconContractTest {

    /** 前端图标注册表源文件（相对仓库根）。 */
    private static final String REGISTRY_RELATIVE = "business/iam-ap/web/src/lib/iconRegistry.ts";

    /**
     * 注册表条目行：`gauge: IconGauge,` / `'org-grant': IconOrgGrant,`。
     *
     * <p>要求行尾是逗号、且值以 `Icon` 开头——这样能自动排除文件里的
     * `const REGISTRY: Record<string, IconComponent> = {`（以 `= {` 结尾）
     * 与 `export const IconFallback: IconComponent = IconAlert;`（以 `;` 结尾）。</p>
     */
    private static final Pattern REGISTRY_ENTRY =
            Pattern.compile("^\\s*['\"]?([A-Za-z0-9_-]+)['\"]?\\s*:\\s*Icon[A-Za-z0-9_]+\\s*,\\s*$");

    @Test
    void everySeededMenuIconExistsInFrontendRegistry() throws IOException {
        Path registry = locateRegistry();
        Set<String> registryKeys = parseRegistryKeys(Files.readString(registry, StandardCharsets.UTF_8));
        assertThat(registryKeys)
                .as("未能从 %s 解析出任何注册表键（文件结构是否已改？）", registry)
                .isNotEmpty();

        Set<String> seededIcons = IamConsoleCatalog.menus().stream()
                .map(MenuDef::icon)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(seededIcons)
                .as("控制台菜单种子里一个图标都没有，测试将失去意义")
                .isNotEmpty();

        assertThat(registryKeys)
                .as("菜单种子使用的图标标识必须全部存在于前端 %s；缺失者会在侧栏静默回落为兜底图标",
                        REGISTRY_RELATIVE)
                .containsAll(seededIcons);
    }

    /**
     * 注册表本身的两条健全性检查。
     *
     * <p>① <b>键不重复</b>：对象字面量里重复的键是**静默覆盖**（后写胜出），
     * 会把前一个图标悄悄改掉，肉眼看代码极难发现；</p>
     * <p>② <b>解析结果不退化</b>：解析出的条目数不少于种子用到的图标数——
     * 若哪天文件格式变了导致正则全部失配，本断言会先失败，
     * 而不是让上一条测试因「空集 containsAll 空集」而假绿。</p>
     */
    @Test
    void registryKeysAreUniqueAndParsingIsNotDegenerate() throws IOException {
        Path registry = locateRegistry();
        List<String> keysInOrder = parseRegistryKeysInOrder(
                Files.readString(registry, StandardCharsets.UTF_8));

        assertThat(keysInOrder).as("注册表键重复（对象字面量后写覆盖前一个）").doesNotHaveDuplicates();

        long seededDistinct = IamConsoleCatalog.menus().stream()
                .map(MenuDef::icon)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .distinct()
                .count();
        assertThat(keysInOrder.size())
                .as("注册表解析结果疑似退化：解析出的键比种子用到的图标还少")
                .isGreaterThanOrEqualTo((int) seededDistinct);
    }

    // ------------------------------------------------------------------ 工具

    private Set<String> parseRegistryKeys(String source) {
        return new TreeSet<>(parseRegistryKeysInOrder(source));
    }

    /** 保序解析注册表键（用于重复键检测，顺序不可丢）。 */
    private List<String> parseRegistryKeysInOrder(String source) {
        List<String> keys = new ArrayList<>();
        for (String line : source.split("\\R")) {
            if (line.trim().startsWith("//") || line.trim().startsWith("*")) {
                continue;
            }
            Matcher m = REGISTRY_ENTRY.matcher(line);
            if (m.matches()) {
                keys.add(m.group(1));
            }
        }
        return keys;
    }

    /** 依次尝试若干候选工作目录下的注册表路径，返回第一个存在的。 */
    private Path locateRegistry() {
        for (Path candidate : registryCandidates()) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new AssertionError("未找到前端图标注册表 " + REGISTRY_RELATIVE
                + "；当前工作目录 = " + Path.of("").toAbsolutePath()
                + "，已尝试：" + registryCandidates());
    }

    private List<Path> registryCandidates() {
        Path cwd = Path.of("").toAbsolutePath();
        // ① 模块目录（surefire 默认工作目录）：business/iam-ap/server → 回到仓库根
        // ② 仓库根
        // ③ 从 cwd 逐级向上找，容忍 future 的目录调整
        List<Path> direct = List.of(
                cwd.resolve("..").resolve("..").resolve(REGISTRY_RELATIVE).normalize(),
                cwd.resolve(REGISTRY_RELATIVE).normalize());
        List<Path> all = new ArrayList<>(direct);
        Path up = cwd;
        for (int i = 0; i < 5 && up.getParent() != null; i++) {
            up = up.getParent();
            all.add(up.resolve(REGISTRY_RELATIVE).normalize());
        }
        return all;
    }
}
