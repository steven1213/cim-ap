package com.cim.jpa.flywayit;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * T1.5 迁移测试启动类：包根即实体扫描根（仅 {@link ProbeEntity}），
 * 避免其他集成测试实体参与 {@code ddl-auto=validate} 校验。
 */
@SpringBootApplication
public class FlywayTestApplication {
}
