package com.cim.system;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 集成测试启动类。
 *
 * <p>类位于 {@code com.cim.system}，故 {@code @SpringBootApplication} 的组件扫描
 * 覆盖整个模块（含 {@code autoconfigure} 下的 {@code CimSystemConfiguration}）；
 * 实体与仓储的扫描根由该配置类声明的
 * {@code @EntityScan/@EnableJpaRepositories("com.cim.system")} 提供——这正是
 * 「域模块自带扫描根、宿主只需补自己的包」的验证点。</p>
 */
@SpringBootApplication
public class TestApplication {
}
