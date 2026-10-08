package com.cim.iam.server.token;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:iam-tv;DB_CLOSE_DELAY=-1;MODE=MySQL")
class TokenVersionServiceTest {

    @Autowired
    TokenVersionService service;

    @Test
    void currentVersionCreatesRecordAtOne() {
        long v = service.currentVersion("tv-user-1");
        assertThat(v).isEqualTo(1L);
        // 幂等：再次读取仍为 1
        assertThat(service.currentVersion("tv-user-1")).isEqualTo(1L);
    }

    @Test
    void bumpIncrementsMonotonically() {
        service.currentVersion("tv-user-2");
        assertThat(service.bump("tv-user-2")).isEqualTo(2L);
        assertThat(service.bump("tv-user-2")).isEqualTo(3L);
        assertThat(service.currentVersion("tv-user-2")).isEqualTo(3L);
    }
}
