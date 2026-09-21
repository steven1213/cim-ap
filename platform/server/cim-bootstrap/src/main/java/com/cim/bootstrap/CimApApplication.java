package com.cim.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * CIM-AP 启动入口。
 *
 * <p>scanBasePackages 覆盖 {@code com.cim} 以扫描所有 cim-* 模块中的 Spring Bean。
 * 当前为 P0 骨架：仅装配 Web + Actuator，认证、持久化等能力在对应 P 阶段接入。
 */
@SpringBootApplication(scanBasePackages = "com.cim")
public class CimApApplication {

    public static void main(String[] args) {
        SpringApplication.run(CimApApplication.class, args);
    }
}
