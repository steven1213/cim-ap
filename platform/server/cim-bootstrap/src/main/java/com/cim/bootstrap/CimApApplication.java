package com.cim.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * CIM-AP 启动入口（本模块是唯一可执行模块，见打包约定）。
 *
 * <p>{@code scanBasePackages="com.cim"} 覆盖所有 cim-* 模块，使引入的 starter 与<b>域模块</b>
 * 的组件都被扫到。注意：组件扫描<b>不</b>覆盖实体/仓储扫描（那由启动类所在包决定），
 * 故域模块自带 {@code @EntityScan/@EnableJpaRepositories("com.cim.system")} 补上这一步——
 * 这正是「加依赖即可用」的成立条件（design.md §2.10）。</p>
 *
 * <p>⚠️ <b>各单元的实体/仓储包必须互不重叠</b>：Spring Data 的仓储注册器<b>不做重名去重</b>，
 * 两个 {@code @EnableJpaRepositories} 包范围重叠会让同一仓储 bean 注册两次 →
 * {@code BeanDefinitionOverrideException}。故 {@code cim-system} 只声明 {@code com.cim.system}、
 * {@code cim-i18n-starter} 只声明 {@code com.cim.i18n}，宿主 ap 声明自己的包。</p>
 *
 * <p>当前装配：web + actuator + cim-jpa-starter（持久化）+ cim-auth-starter（验签/准入/鉴权）
 * + cim-system（系统域：用户/角色/菜单/权限/字典/参数/日志）。数据源默认 H2 内存库，
 * 生产切换见 {@code application.yml.example}。</p>
 */
@SpringBootApplication(scanBasePackages = "com.cim")
public class CimApApplication {

    public static void main(String[] args) {
        SpringApplication.run(CimApApplication.class, args);
    }
}
