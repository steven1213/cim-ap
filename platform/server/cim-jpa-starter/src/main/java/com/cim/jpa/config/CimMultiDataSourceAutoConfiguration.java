package com.cim.jpa.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import java.util.HashMap;
import java.util.Map;

/**
 * 多数据源自动装配（T1.1，见 README §3 / design.md §2.3）。
 *
 * <p>为配置了 {@code cim.jpa.datasources.*} 的每个逻辑库生成独立的
 * {@code {name}DataSource}（Hikari）/ {@code {name}EntityManagerFactory} /
 * {@code {name}TransactionManager}，与 Spring Boot 的主数据源（默认 {@code entityManagerFactory}）
 * 并行，互不干扰。未配置任何额外数据源时注册器为空操作（不产生任何 Bean）。</p>
 *
 * <p>仅负责「每库独立 EMF/TxManager/Hikari」的装配；具体库（MySQL/Oracle/PG）连通性校验
 * 由部署环境提供（多库集成测试见 M7 / T7.2）。</p>
 */
@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)
@ConditionalOnClass({LocalContainerEntityManagerFactoryBean.class, HikariDataSource.class})
@EnableConfigurationProperties(CimJpaProperties.class)
@Import(CimMultiDataSourceAutoConfiguration.CimDataSourceRegistrar.class)
public class CimMultiDataSourceAutoConfiguration {

    /**
     * 动态注册每个逻辑库的 DataSource / EntityManagerFactory / TransactionManager Bean 定义。
     */
    public static class CimDataSourceRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {

        private Environment environment;

        @Override
        public void setEnvironment(Environment environment) {
            this.environment = environment;
        }

        @Override
        public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                            BeanDefinitionRegistry registry) {
            Map<String, CimJpaProperties.Datasource> datasources =
                    Binder.get(environment).bind("cim.jpa.datasources",
                            Bindable.mapOf(String.class, CimJpaProperties.Datasource.class))
                            .orElseGet(Map::of);

            for (Map.Entry<String, CimJpaProperties.Datasource> entry : datasources.entrySet()) {
                String name = entry.getKey();
                CimJpaProperties.Datasource ds = entry.getValue();
                registerDataSource(registry, name, ds);
                registerEntityManagerFactory(registry, name, ds);
                registerTransactionManager(registry, name);
            }
        }

        private void registerDataSource(BeanDefinitionRegistry registry, String name,
                                        CimJpaProperties.Datasource ds) {
            BeanDefinitionBuilder b = BeanDefinitionBuilder.genericBeanDefinition(HikariDataSource.class);
            b.addPropertyValue("jdbcUrl", ds.getUrl());
            b.addPropertyValue("username", ds.getUsername());
            b.addPropertyValue("password", ds.getPassword());
            b.addPropertyValue("maximumPoolSize", ds.getMaximumPoolSize());
            if (ds.getDriverClassName() != null && !ds.getDriverClassName().isBlank()) {
                b.addPropertyValue("driverClassName", ds.getDriverClassName());
            }
            registry.registerBeanDefinition(name + "DataSource", b.getBeanDefinition());
        }

        private void registerEntityManagerFactory(BeanDefinitionRegistry registry, String name,
                                                  CimJpaProperties.Datasource ds) {
            BeanDefinitionBuilder b =
                    BeanDefinitionBuilder.genericBeanDefinition(LocalContainerEntityManagerFactoryBean.class);
            b.addPropertyReference("dataSource", name + "DataSource");
            b.addPropertyValue("jpaVendorAdapter", new HibernateJpaVendorAdapter());
            if (ds.getPackagesToScan() != null && !ds.getPackagesToScan().isBlank()) {
                b.addPropertyValue("packagesToScan", ds.getPackagesToScan().split("\\s*,\\s*"));
            }
            Map<String, Object> props = new HashMap<>();
            props.put("hibernate.hbm2ddl.auto", ds.getDdlAuto());
            if (ds.getDialect() != null && !ds.getDialect().isBlank()) {
                props.put("hibernate.dialect", ds.getDialect());
            }
            props.put("hibernate.physical_naming_strategy", new LowercaseSnakeNamingStrategy(""));
            b.addPropertyValue("jpaProperties", props);
            registry.registerBeanDefinition(name + "EntityManagerFactory", b.getBeanDefinition());
        }

        private void registerTransactionManager(BeanDefinitionRegistry registry, String name) {
            BeanDefinitionBuilder b =
                    BeanDefinitionBuilder.genericBeanDefinition(JpaTransactionManager.class);
            b.addPropertyReference("entityManagerFactory", name + "EntityManagerFactory");
            registry.registerBeanDefinition(name + "TransactionManager", b.getBeanDefinition());
        }
    }
}
