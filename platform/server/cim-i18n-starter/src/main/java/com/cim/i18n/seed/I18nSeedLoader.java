package com.cim.i18n.seed;

import com.cim.i18n.config.I18nProperties;
import com.cim.i18n.service.I18nService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * 启动种子：把内置文案幂等写入 {@code sys_i18n}（scope=SYSTEM，README §7「系统级」）。
 *
 * <p>仅在 {@code cim.i18n.seed-enabled=true} 时执行；已存在的键不覆盖（保留在线修改）。</p>
 */
@Slf4j
public class I18nSeedLoader implements ApplicationRunner {

    private final I18nService i18nService;
    private final I18nProperties properties;

    public I18nSeedLoader(I18nService i18nService, I18nProperties properties) {
        this.i18nService = i18nService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isSeedEnabled()) {
            return;
        }
        try {
            i18nService.seed();
        } catch (Exception e) {
            // 种子失败不阻塞启动（i18n 属增强能力，DB 暂不可用时应降级为内置兜底包）
            log.warn("[cim-i18n] 启动种子失败，将退回内置兜底包：{}", e.getMessage());
        }
    }
}
