package com.cim.i18n.config;

import com.cim.i18n.model.SysI18nRepository;
import com.cim.i18n.seed.I18nSeedLoader;
import com.cim.i18n.service.I18nService;
import com.cim.i18n.source.DatabaseMessageSource;
import com.cim.i18n.source.DbMessageResolver;
import com.cim.i18n.source.I18nCache;
import com.cim.i18n.source.I18nResolver;
import com.cim.i18n.source.MissingI18nReporter;
import com.cim.i18n.web.I18nController;
import com.cim.i18n.web.LocaleResolverCim;
import com.cim.i18n.model.SysLocaleRepository;
import com.cim.spring.support.config.SpringSupportAutoConfiguration;
import com.cim.spring.support.i18n.MessageResolver;
import com.cim.spring.support.i18n.DefaultMessageResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.web.servlet.LocaleResolver;

/**
 * cim-i18n-starter 自动装配（design.md §2.7 / README §7）。
 *
 * <p><b>装配顺序（关键）</b>：本类必须在 {@link SpringSupportAutoConfiguration} 与
 * {@link MessageSourceAutoConfiguration} <b>之前</b>注册——因为这两处都以
 * {@code @ConditionalOnMissingBean} 装配「默认 {@code MessageResolver}」与「默认 {@code MessageSource}」，
 * 先到先得。本 starter 先注册即让它们让位（与 {@code cim-auth-starter} 的 SPI 让位同一模式）。</p>
 *
 * <p>JDK21 + javac 下 Lombok 需显式 {@code -processor}（见各模块 pom 注释），本模块沿用父 POM 约定。</p>
 */
@AutoConfiguration(before = {SpringSupportAutoConfiguration.class, MessageSourceAutoConfiguration.class})
@ConditionalOnProperty(prefix = "cim.i18n", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(I18nProperties.class)
@EntityScan(basePackages = "com.cim.i18n")
@EnableJpaRepositories(basePackages = "com.cim.i18n")
public class I18nAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public I18nCache i18nCache(SysI18nRepository i18nRepository) {
        return new I18nCache(i18nRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public MissingI18nReporter missingI18nReporter() {
        return new MissingI18nReporter();
    }

    @Bean
    @ConditionalOnMissingBean
    public I18nResolver i18nResolver(I18nCache cache, MissingI18nReporter reporter, I18nProperties properties) {
        return new I18nResolver(cache, reporter, properties);
    }

    /** 顶替 {@link DefaultMessageResolver}（默认兜底不翻译）。 */
    @Bean
    @ConditionalOnMissingBean(MessageResolver.class)
    public MessageResolver dbMessageResolver(I18nResolver resolver) {
        return new DbMessageResolver(resolver);
    }

    /**
     * 数据库版 {@code MessageSource}（{@code @Primary}），让 Spring 校验/异常消息零改造走 DB。
     *
     * <p>Bean 名取 {@code cimMessageSource}（非 {@code messageSource}）以避免与 Spring Boot 默认
     * 同名冲突；因本自动配置先于 {@code MessageSourceAutoConfiguration} 注册，后者会因
     * 「已存在 {@code MessageSource}」而跳过装配。</p>
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(MessageSource.class)
    public MessageSource cimMessageSource(I18nResolver resolver) {
        return new DatabaseMessageSource(resolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public I18nService i18nService(SysLocaleRepository localeRepository,
                                   SysI18nRepository i18nRepository,
                                   I18nCache cache,
                                   I18nProperties properties) {
        return new I18nService(localeRepository, i18nRepository, cache, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public I18nSeedLoader i18nSeedLoader(I18nService i18nService, I18nProperties properties) {
        return new I18nSeedLoader(i18nService, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public I18nController i18nController(I18nService i18nService, MissingI18nReporter reporter) {
        return new I18nController(i18nService, reporter);
    }

    /** 语言解析（请求头 {@code Accept-Language}）。 */
    @Bean(name = "localeResolver")
    @ConditionalOnWebApplication
    @ConditionalOnMissingBean(LocaleResolver.class)
    public LocaleResolver localeResolverCim(I18nCache cache, I18nProperties properties) {
        return new LocaleResolverCim(cache, properties);
    }
}
