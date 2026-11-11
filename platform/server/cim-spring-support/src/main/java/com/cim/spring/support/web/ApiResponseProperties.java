package com.cim.spring.support.web;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一响应契约的开关（见 {@code docs/platform/server/api-convention.md §6}）。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "cim.api.response")
public class ApiResponseProperties {

    /**
     * 是否校验「控制器返回 Result 或标注 {@link RawResponse}」。
     *
     * <p>默认开启。关闭仅用于「存量系统接入平台、一次改不完」的过渡期，且会打 WARN 提示。</p>
     */
    private boolean verify = true;

    /**
     * 发现违规时是否**启动失败**（{@code true}）还是仅记录 ERROR（{@code false}）。
     *
     * <p>默认 {@code true}：契约问题必须在开发/测试阶段暴露，而不是等前端拿到裸 JSON 才发现。
     * 生产环境如需「先观察一轮」，可临时置 {@code false}。</p>
     */
    private boolean strict = true;

    /**
     * 参与校验的包前缀。默认只校验自有代码（{@code com.cim}）。
     *
     * <p>不限定范围会把框架自带的 {@code BasicErrorController}（返回 {@code ResponseEntity} /
     * {@code ModelAndView}）也判为违规——它是错误页出口，不属于业务契约。</p>
     */
    private List<String> includePackages = new ArrayList<>(List.of("com.cim"));
}
