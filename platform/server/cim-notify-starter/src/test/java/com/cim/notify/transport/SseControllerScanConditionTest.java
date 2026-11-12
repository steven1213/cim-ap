package com.cim.notify.transport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.mock.env.MockEnvironment;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SseController} 必须跟着 {@code cim.notify.enabled} 一起被组件扫描跳过。
 *
 * <p>它带 {@code @RestController}（= {@code @Component}），宿主 {@code scanBasePackages="com.cim"}
 * 会绕过自动装配的总开关直接注册它。而它依赖 {@code SseRegistry} / {@code NotifyProperties}——
 * 开关一关这些 Bean 也不存在，启动就会以「缺某个依赖」失败，报错完全看不出是关了通知开关。
 * 同类问题在 {@code SqlLogAdminController} 上已实测（见 {@code cim-bootstrap} 的
 * {@code LibraryEndpointScanTest}）。</p>
 *
 * <p>这里用扫描器的条件求值（不启容器）直接钉住「候选与否」，毫秒级。</p>
 */
class SseControllerScanConditionTest {

    @Test
    @DisplayName("cim.notify.enabled=false → 不进入扫描候选（否则开关一关就启动失败）")
    void notCandidateWhenNotifyDisabled() {
        assertThat(scanCandidates("cim.notify.enabled", "false"))
                .as("关闭通知时不该扫到 SseController")
                .doesNotContain(SseController.class.getName());
    }

    @Test
    @DisplayName("未配置开关（默认开）→ 仍是候选")
    void candidateByDefault() {
        assertThat(scanCandidates())
                .as("默认开关为开，控制器应照常装配——条件不能把正常路径也关掉")
                .contains(SseController.class.getName());
    }

    private static Set<String> scanCandidates(String... kv) {
        MockEnvironment env = new MockEnvironment();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            env.setProperty(kv[i], kv[i + 1]);
        }
        ClassPathScanningCandidateComponentProvider provider =
                new ClassPathScanningCandidateComponentProvider(true);
        provider.setEnvironment(env);
        return provider.findCandidateComponents("com.cim.notify.transport").stream()
                .map(bd -> bd.getBeanClassName())
                .collect(Collectors.toSet());
    }
}
