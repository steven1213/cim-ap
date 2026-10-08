package com.cim.cache;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 集成测试用最小 Spring Boot 应用：加载 cim-cache 自动装配（含 @CacheableCim 切面），
 * 并关闭 Redis 以验证「无 Redis 降级本地」路径。
 */
@SpringBootApplication
public class CacheTestApplication {
}
