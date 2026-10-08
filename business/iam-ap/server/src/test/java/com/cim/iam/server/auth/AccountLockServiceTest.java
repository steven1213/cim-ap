package com.cim.iam.server.auth;

import com.cim.iam.server.config.IamAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

// 独立上下文 + 独立 H2（避免与其它 IAM 测试共享库导致数据串扰）
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:iam-acclock;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.iam.auth.lockout.max-attempts=3",
        "cim.iam.auth.lockout.lock-minutes=15",
        "cim.iam.auth.lockout.window-minutes=15"
})
class AccountLockServiceTest {

    @Autowired
    private AccountLockService lockService;
    @Autowired
    private AccountLockRepository repository;
    @Autowired
    private IamAuthProperties authProps;

    @Test
    void locksAfterMaxFailures() {
        String u = "lockme";
        for (int i = 0; i < authProps.getLockout().getMaxAttempts(); i++) {
            lockService.onFailure(u);
        }
        assertThat(lockService.isLocked(u)).isTrue();
        assertThat(lockService.remainingAttempts(u)).isZero();
    }

    @Test
    void countsWithinWindowNotYetLocked() {
        String u = "almost";
        lockService.onFailure(u);
        lockService.onFailure(u);
        assertThat(lockService.isLocked(u)).isFalse();
        assertThat(lockService.remainingAttempts(u)).isEqualTo(1);
    }

    @Test
    void onSuccessResetsCounter() {
        String u = "resety";
        lockService.onFailure(u);
        lockService.onFailure(u);
        assertThat(lockService.remainingAttempts(u)).isEqualTo(1);
        lockService.onSuccess(u);
        assertThat(lockService.isLocked(u)).isFalse();
        assertThat(lockService.remainingAttempts(u)).isEqualTo(authProps.getLockout().getMaxAttempts());
    }

    @Test
    void slidingWindowResetsStaleFailures() {
        // 预置一个「首败已超过窗口」且计数很高的历史行
        AccountLock stale = new AccountLock();
        stale.setId("seed-" + System.nanoTime());
        stale.setUsername("oldie");
        stale.setFailCount(99);
        stale.setFirstFailAt(Instant.now().minus(Duration.ofMinutes(authProps.getLockout().getWindowMinutes() + 1)));
        repository.save(stale);

        lockService.onFailure("oldie");

        // 窗口外历史失败应被重置：当前计数回到 1，未达阈值不锁定
        assertThat(lockService.isLocked("oldie")).isFalse();
        assertThat(lockService.remainingAttempts("oldie"))
                .isEqualTo(authProps.getLockout().getMaxAttempts() - 1);
    }
}
