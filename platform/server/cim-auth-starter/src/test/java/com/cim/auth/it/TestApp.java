package com.cim.auth.it;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 集成测试应用入口（仅扫描测试包，避免重复注册 autoconfig）。 */
@SpringBootApplication(scanBasePackages = "com.cim.auth.it")
public class TestApp {
}
