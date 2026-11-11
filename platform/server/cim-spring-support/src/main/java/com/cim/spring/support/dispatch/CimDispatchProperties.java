package com.cim.spring.support.dispatch;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息分发模式配置（默认关闭——它是**受控例外**，不是主路径）。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "cim.api.dispatch")
public class CimDispatchProperties {

    /**
     * 是否启用分发入口。默认 {@code false}：启用即意味着应用暴露一个通配 POST 端点，
     * 必须由应用显式开启并配套鉴权（默认走平台过滤链）。
     */
    private boolean enabled = false;

    /** 分发入口前缀。 */
    private String basePath = "/api/v1/messages";

    /** 扫描 {@link CimMessage} 的包；留空时取应用主类所在包。 */
    private List<String> basePackages = new ArrayList<>();
}
