package com.cim.approval.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 上下文加载冒烟：验证 Flowable 7 + cim 平台 starter + H2 可共存启动。
 */
@SpringBootTest
class ApprovalApApplicationTests {

    @Test
    void contextLoads() {
        // 仅验证 Spring 上下文成功加载（含 Flowable 引擎自动装配）
    }
}
