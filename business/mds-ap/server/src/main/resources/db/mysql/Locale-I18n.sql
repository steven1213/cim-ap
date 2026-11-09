-- ============================================================
-- MDS 基础数据：Locale（语言）与 I18n（翻译）
-- 依赖：无（与 Site/Fab/Area/Bay/Module 同级的基础数据）
-- 主键：id 由应用层雪花算法（IdGenerator）生成，非 DB 自增
-- 租户：不做多租户隔离（全厂全局共享）
-- 软删：deleted_at 时间戳，null=未删；唯一键含 deleted_at 以支持删后重建同编码
-- 说明：本文件含示例种子数据（id 为占位值）；正式环境种子建议走 Java seeder
-- 约定：各 AP 库内独立种子 i18n，绝不跨 AP 读库（见平台 i18n 库隔离约定）
-- ============================================================

-- ----------------------------
-- 1) cim_locale：语言登记
--    记录系统支持的语言（locale）及其是否为默认语言
-- ----------------------------

create table cim_locale (
    id          bigint       not null                comment '主键（雪花算法 IdGenerator 生成，非 DB 自增）',
    locale_code varchar(32)  not null                comment '语言编码（BCP-47），唯一，例：zh-CN、en-US',
    locale_name varchar(64)  not null                comment '语言显示名，例：简体中文、English',
    is_default  tinyint      not null default 0      comment '是否默认语言：1 默认 0 非默认（全表仅一条为 1，由应用保证）',
    is_enabled  tinyint      not null default 1      comment '1 启用 0 停用',
    deleted_at  datetime     null                    comment '删除时间；null=未删（逻辑删除填值，唯一键含该列以支持删后重建同编码）',
    is_occupied tinyint      not null default 0      comment '是否占用（修改锁定/被占用）：1 占用 0 未占用',
    occupied_by varchar(64)  null                    comment '占用人 IAM 账号（is_occupied=1 时有效）',
    created_at  datetime     not null default current_timestamp comment '创建时间',
    created_by  varchar(64)  null                    comment '创建人 IAM 账号',
    updated_at  datetime     not null default current_timestamp on update current_timestamp comment '更新时间',
    updated_by  varchar(64)  null                    comment '更新人 IAM 账号',
    primary key (id),
    unique key uk_locale_code (locale_code, deleted_at)
) engine = InnoDB default charset = utf8mb4 comment = '语言登记（Locale），系统支持的语言及默认语言';

-- 插入默认语言 ---

insert into cim_locale
    (id, locale_code, locale_name, is_default, is_enabled, created_at, created_by, updated_at, updated_by)
values
    (1, 'zh-CN', '简体中文', 1, 1, now(), 'system', now(), 'system'),
    (2, 'en-US', 'English',  0, 1, now(), 'system', now(), 'system');

-- ----------------------------
-- 2) cim_i18n_message：翻译表
--    按 (语言 + 键) 唯一记录一条翻译；同一键在不同语言下各存一行
--    i18n_key 采用点分层命名（如 mds.site.CD-SITE），i18n_group 用于键空间隔离
-- ----------------------------

create table cim_i18n_message (
    id          bigint       not null                comment '主键（雪花算法 IdGenerator 生成，非 DB 自增）',
    locale_code varchar(32)  not null                comment '语言编码，关联 cim_locale.locale_code（逻辑外键，仅索引不建 FK）',
    i18n_group  varchar(64)  null                    comment '业务分组/命名空间，如 site、fab、common；用于隔离不同模块键空间',
    i18n_key    varchar(128) not null                comment '国际化键（点分层），例：mds.site.CD-SITE',
    i18n_value  varchar(1024) not null               comment '该语言下的翻译文本；超长文本可改 text 类型',
    is_enabled  tinyint      not null default 1      comment '1 启用 0 停用',
    deleted_at  datetime     null                    comment '删除时间；null=未删（逻辑删除填值，唯一键含该列以支持删后重建同编码）',
    is_occupied tinyint      not null default 0      comment '是否占用（修改锁定/被占用）：1 占用 0 未占用',
    occupied_by varchar(64)  null                    comment '占用人 IAM 账号（is_occupied=1 时有效）',
    created_at  datetime     not null default current_timestamp comment '创建时间',
    created_by  varchar(64)  null                    comment '创建人 IAM 账号',
    updated_at  datetime     not null default current_timestamp on update current_timestamp comment '更新时间',
    updated_by  varchar(64)  null                    comment '更新人 IAM 账号',
    primary key (id),
    unique key uk_i18n_msg (locale_code, i18n_key, deleted_at),
    key idx_i18n_key (i18n_key)
) engine = InnoDB default charset = utf8mb4 comment = '翻译表（I18n 国际化消息），按语言+键唯一记录翻译文本';

-- 插入示例翻译（Site 名称中英文）---

insert into cim_i18n_message
    (id, locale_code, i18n_group, i18n_key, i18n_value, is_enabled, created_at, created_by, updated_at, updated_by)
values
    (1, 'zh-CN', 'site', 'mds.site.CD-SITE', '成都园区', 1, now(), 'system', now(), 'system'),
    (2, 'en-US', 'site', 'mds.site.CD-SITE', 'Chengdu Campus', 1, now(), 'system', now(), 'system'),
    (3, 'zh-CN', 'site', 'mds.site.SH-SITE', '上海园区', 1, now(), 'system', now(), 'system'),
    (4, 'en-US', 'site', 'mds.site.SH-SITE', 'Shanghai Campus', 1, now(), 'system', now(), 'system'),
    (5, 'zh-CN', 'fab',  'mds.fab.F1', '成都一号晶圆厂', 1, now(), 'system', now(), 'system'),
    (6, 'en-US', 'fab',  'mds.fab.F1', 'Chengdu Fab 1', 1, now(), 'system', now(), 'system');
