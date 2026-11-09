-- ============================================================
-- MDS 基础数据：Resource（资源：菜单 menu + 按钮 button）
-- 依赖：cim_i18n_message（显示名翻译，通过 i18n_key 关联，仅索引不建 FK）
-- 主键：id 由应用层雪花算法（IdGenerator）生成，非 DB 自增
-- 租户：不做多租户隔离（全厂全局共享）
-- 软删：deleted_at 时间戳，null=未删；唯一键含 deleted_at 以支持删后重建同编码
-- 说明：本文件含示例种子数据（id 为占位值，含关联 cim_i18n_message 的翻译行）；正式环境种子建议走 Java seeder
-- 约定：菜单/按钮显示名不落本表，统一存 cim_i18n_message（i18n_group=menu / button），多语言从此表取
-- ============================================================

-- ----------------------------
-- cim_resource：资源表（menu / button 合一）
--   resource_type 区分：MENU（菜单）/ BUTTON（按钮）
--   parent_code 自关联本表 resource_code，构成菜单树；按钮挂在其所属菜单下
--   显示名：i18n_key 关联 cim_i18n_message.i18n_key（group=menu / button）
--   permission_code：菜单=访问权限，按钮=操作权限
-- ----------------------------

create table cim_resource (
    id              bigint       not null                comment '主键（雪花算法 IdGenerator 生成，非 DB 自增）',
    resource_code   varchar(64)  not null                comment '资源编码，全局唯一，例：menu:system / btn:user:add（建议带类型前缀）',
    resource_type   varchar(32)  not null                comment '资源类型：MENU(菜单) / BUTTON(按钮)',
    parent_code     varchar(64)  null                    comment '父资源编码，自关联本表 resource_code；顶级菜单为 null，按钮挂所属菜单',
    i18n_key        varchar(128) not null                comment '显示名翻译键，关联 cim_i18n_message.i18n_key（menu/button 分组）',
    permission_code varchar(64)  null                    comment '关联权限码，菜单=访问权限、按钮=操作权限；空=无需权限',
    path            varchar(256) null                    comment '菜单路由路径（MENU 用）；BUTTON 为 null',
    icon            varchar(64)  null                    comment '菜单图标标识（MENU 用）；BUTTON 为 null',
    sort_no         int          not null default 0      comment '同级排序号，升序显示',
    is_enabled      tinyint      not null default 1      comment '1 启用 0 停用',
    deleted_at      datetime     null                    comment '删除时间；null=未删（逻辑删除填值，唯一键含该列以支持删后重建同编码）',
    is_occupied     tinyint      not null default 0      comment '是否占用（修改锁定/被占用）：1 占用 0 未占用',
    occupied_by     varchar(64)  null                    comment '占用人 IAM 账号（is_occupied=1 时有效）',
    created_at      datetime     not null default current_timestamp comment '创建时间',
    created_by      varchar(64)  null                    comment '创建人 IAM 账号',
    updated_at      datetime     not null default current_timestamp on update current_timestamp comment '更新时间',
    updated_by      varchar(64)  null                    comment '更新人 IAM 账号',
    primary key (id),
    unique key uk_resource_code (resource_code, deleted_at),
    key idx_resource_parent (parent_code),
    key idx_resource_i18n (i18n_key)
) engine = InnoDB default charset = utf8mb4 comment = '资源表（菜单 menu + 按钮 button 合一），显示名关联 cim_i18n_message 翻译';

-- 插入示例资源（菜单树 + 按钮）---

insert into cim_resource
    (id, resource_code, resource_type, parent_code, i18n_key, permission_code, path, icon, sort_no, is_enabled, created_at, created_by, updated_at, updated_by)
values
    -- 顶级菜单
    (1, 'menu:dashboard', 'MENU',  null,   'mds.menu.dashboard', 'mds:dashboard:view', '/dashboard', 'home', 1, 1, now(), 'system', now(), 'system'),
    (2, 'menu:system',    'MENU',  null,   'mds.menu.system',    'mds:system:view',    '/system',    'gear', 2, 1, now(), 'system', now(), 'system'),
    -- 系统管理下的按钮（parent 指向菜单）
    (3, 'btn:user:add',   'BUTTON', 'menu:system', 'mds.btn.user.add',   'mds:user:add',   null, null, 1, 1, now(), 'system', now(), 'system'),
    (4, 'btn:user:edit',  'BUTTON', 'menu:system', 'mds.btn.user.edit',  'mds:user:edit',  null, null, 2, 1, now(), 'system', now(), 'system'),
    (5, 'btn:user:delete','BUTTON', 'menu:system', 'mds.btn.user.delete','mds:user:delete',null, null, 3, 1, now(), 'system', now(), 'system');

-- 关联翻译：菜单/按钮显示名（i18n_group = menu / button）---
-- 注：cim_i18n_message 表结构见 Locale-I18n.sql

insert into cim_i18n_message
    (id, locale_code, i18n_group, i18n_key, i18n_value, is_enabled, created_at, created_by, updated_at, updated_by)
values
    (101, 'zh-CN', 'menu',   'mds.menu.dashboard', '仪表盘',   1, now(), 'system', now(), 'system'),
    (102, 'en-US', 'menu',   'mds.menu.dashboard', 'Dashboard', 1, now(), 'system', now(), 'system'),
    (103, 'zh-CN', 'menu',   'mds.menu.system',    '系统管理',  1, now(), 'system', now(), 'system'),
    (104, 'en-US', 'menu',   'mds.menu.system',    'System',    1, now(), 'system', now(), 'system'),
    (105, 'zh-CN', 'button', 'mds.btn.user.add',   '新增用户',  1, now(), 'system', now(), 'system'),
    (106, 'en-US', 'button', 'mds.btn.user.add',   'Add User',  1, now(), 'system', now(), 'system'),
    (107, 'zh-CN', 'button', 'mds.btn.user.edit',  '编辑用户',  1, now(), 'system', now(), 'system'),
    (108, 'en-US', 'button', 'mds.btn.user.edit',  'Edit User', 1, now(), 'system', now(), 'system'),
    (109, 'zh-CN', 'button', 'mds.btn.user.delete','删除用户',  1, now(), 'system', now(), 'system'),
    (110, 'en-US', 'button', 'mds.btn.user.delete','Delete User',1, now(), 'system', now(), 'system');
