-- ============================================================
-- MDS 基础数据：Site（基地）
-- 层级：Site → Fab → Area → Bay → Module
-- 依赖：无（层级根节点）
-- 主键：id 由应用层雪花算法（IdGenerator）生成，非 DB 自增
-- 租户：不做多租户隔离（全厂全局共享）
-- 软删：deleted_at 时间截，null=未删；唯一键含 deleted_at 以支持删后重建同编码
-- 说明：本文件含示例种子数据（id 为占位值）；正式环境种子建议走 Java seeder
-- ============================================================

-- 创建表结构 ---

create table cim_site (
    id             bigint       not null                comment '主键（雪花算法 IdGenerator 生成，非 DB 自增）',
    site_code      varchar(64)  not null                comment '基地编码，唯一，例：CD-SITE、SH-SITE',
    site_name      varchar(128) not null                comment '基地名称，例：成都园区',
    address        varchar(256) null                    comment '详细地址',
    contact_person varchar(64)  null                    comment '基地负责人',
    contact_phone  varchar(32)  null                    comment '联系电话',
    remark         varchar(512) null                    comment '备注',
    is_enabled     tinyint      not null default 1      comment '1 启用 0 停用',
    deleted_at     datetime     null                    comment '删除时间；null=未删（逻辑删除填值，唯一键含该列以支持删后重建同编码）',
    is_occupied    tinyint      not null default 0      comment '是否占用（修改锁定/被占用）：1 占用 0 未占用',
    occupied_by    varchar(64)  null                    comment '占用人 IAM 账号（is_occupied=1 时有效）',
    created_at     datetime     not null default current_timestamp comment '创建时间',
    created_by     varchar(64)  null                    comment '创建人 IAM 账号',
    updated_at     datetime     not null default current_timestamp on update current_timestamp comment '更新时间',
    updated_by     varchar(64)  null                    comment '更新人 IAM 账号',
    primary key (id),
    unique key uk_site_code (site_code, deleted_at)
) engine = InnoDB default charset = utf8mb4 comment = '基地（Site），Site/Fab/Area/Bay/Module 层级根节点';

-- 插入默认数据 ---

insert into cim_site
    (id, site_code, site_name, address, contact_person, contact_phone, remark, is_enabled, created_at, created_by, updated_at, updated_by)
values
    (1, 'CD-SITE', '成都园区', '四川省成都市（详细地址待补充）', null, null, '6英寸晶圆厂 Auto3 升级项目所在基地', 1, now(), 'system', now(), 'system'),
    (2, 'SH-SITE', '上海园区', '上海市（详细地址待补充）',       null, null, null,                               1, now(), 'system', now(), 'system');

-- ============================================================
-- MDS 基础数据：Fab（晶圆厂）
-- 层级：Site → Fab → Area → Bay → Module
-- 依赖：cim_site（site_code 逻辑外键，仅索引不建 FK）
-- ============================================================

-- 创建表结构 ---

create table cim_fab (
    id          bigint       not null                comment '主键（雪花算法 IdGenerator 生成，非 DB 自增）',
    fab_code    varchar(64)  not null                comment 'Fab 编码，唯一，例：F1、F2',
    site_code   varchar(64)  not null                comment '归属 site_code',
    fab_name    varchar(128) not null                comment 'Fab 名称，例：12寸一号晶圆厂',
    fab_type    varchar(32)  null                    comment '工厂类型：6寸 / 8寸 / 12寸 / Assembly 封装',
    amhs_type   varchar(32)  null                    comment 'AMHS 类型：OHT / RGV / NONE(无 AMHS)',
    clean_grade varchar(32)  null                    comment 'Fab 整体洁净等级，例：Class 100',
    remark      varchar(512) null                    comment '备注',
    is_enabled  tinyint      not null default 1      comment '1 启用 0 停用',
    deleted_at  datetime     null                    comment '删除时间；null=未删（逻辑删除填值，唯一键含该列以支持删后重建同编码）',
    is_occupied tinyint      not null default 0      comment '是否占用（修改锁定/被占用）：1 占用 0 未占用',
    occupied_by varchar(64)  null                    comment '占用人 IAM 账号（is_occupied=1 时有效）',
    created_at  datetime     not null default current_timestamp comment '创建时间',
    created_by  varchar(64)  null                    comment '创建人 IAM 账号',
    updated_at  datetime     not null default current_timestamp on update current_timestamp comment '更新时间',
    updated_by  varchar(64)  null                    comment '更新人 IAM 账号',
    primary key (id),
    unique key uk_fab_code (fab_code, deleted_at),
    key idx_fab_site_code (site_code)
) engine = InnoDB default charset = utf8mb4 comment = '晶圆厂（Fab），归属 Site，AMHS 搬运体系挂载层级';

-- 插入默认数据 ---

insert into cim_fab
    (id, fab_code, site_code, fab_name, fab_type, amhs_type, clean_grade, remark, is_enabled, created_at, created_by, updated_at, updated_by)
values
    (1, 'F1', 'CD-SITE', '成都一号晶圆厂', '6寸', 'OHT', 'Class 100', '6英寸晶圆厂 Auto3 升级项目 Fab', 1, now(), 'system', now(), 'system'),
    (2, 'F2', 'CD-SITE', '成都二号晶圆厂', '6寸', 'NONE', 'Class 1000', '示例：无 AMHS 工厂',           1, now(), 'system', now(), 'system');

-- ============================================================
-- MDS 基础数据：Area（区域/工艺区）
-- 层级：Site → Fab → Area → Bay → Module
-- 依赖：cim_fab（fab_code 逻辑外键，仅索引不建 FK）
-- ============================================================

-- 创建表结构 ---

create table cim_area (
    id         bigint       not null                comment '主键（雪花算法 IdGenerator 生成，非 DB 自增）',
    area_code  varchar(64)  not null                comment 'Area 编码，Fab 内唯一，例：ETCH、PHOTO、DIFF',
    fab_code   varchar(64)  not null                comment '归属 fab_code',
    area_name  varchar(128) not null                comment 'Area 名称，例：刻蚀区、光刻区',
    area_type  varchar(32)  null                    comment '工艺大类：Etch / Photo / Depo / Diff',
    remark     varchar(512) null                    comment '备注',
    is_enabled tinyint      not null default 1      comment '1 启用 0 停用',
    deleted_at datetime     null                    comment '删除时间；null=未删（逻辑删除填值，唯一键含该列以支持删后重建同编码）',
    is_occupied tinyint     not null default 0      comment '是否占用（修改锁定/被占用）：1 占用 0 未占用',
    occupied_by varchar(64) null                    comment '占用人 IAM 账号（is_occupied=1 时有效）',
    created_at datetime     not null default current_timestamp comment '创建时间',
    created_by varchar(64)  null                    comment '创建人 IAM 账号',
    updated_at datetime     not null default current_timestamp on update current_timestamp comment '更新时间',
    updated_by varchar(64)  null                    comment '更新人 IAM 账号',
    primary key (id),
    unique key uk_area_fab_code (fab_code, area_code, deleted_at)
) engine = InnoDB default charset = utf8mb4 comment = '工艺区域（Area），归属 Fab，AMHS 区域级搬运/分区管控单元';

-- 插入默认数据 ---

insert into cim_area
    (id, area_code, fab_code, area_name, area_type, remark, is_enabled, created_at, created_by, updated_at, updated_by)
values
    (1, 'DIFF',  'F1', '扩散区',   'Diff',  null, 1, now(), 'system', now(), 'system'),
    (2, 'PHOTO', 'F1', '光刻区',   'Photo', null, 1, now(), 'system', now(), 'system'),
    (3, 'ETCH',  'F1', '刻蚀区',   'Etch',  null, 1, now(), 'system', now(), 'system'),
    (4, 'DEPO',  'F1', '薄膜区',   'Depo',  null, 1, now(), 'system', now(), 'system'),
    (5, 'ETCH',  'F2', '刻蚀区',   'Etch',  '示例：同编码可出现在不同 Fab', 1, now(), 'system', now(), 'system');

-- ============================================================
-- MDS 基础数据：Bay（工段/厂房隔间）
-- 层级：Site → Fab → Area → Bay → Module
-- 依赖：cim_area（area_code 逻辑外键，仅索引不建 FK）
-- ============================================================

-- 创建表结构 ---

create table cim_bay (
    id          bigint       not null                comment '主键（雪花算法 IdGenerator 生成，非 DB 自增）',
    bay_code    varchar(64)  not null                comment 'Bay 编码，Area 内唯一，例：ETCH-B01',
    area_code   varchar(64)  not null                comment '归属 area_code',
    bay_name    varchar(128) not null                comment 'Bay 名称，例：刻蚀 01 工段',
    bay_kind    varchar(32)  null                    comment '搬运范围：Intrabay(区内) / Interbay(跨区)',
    clean_level varchar(32)  null                    comment '洁净等级：Class1 / Class10',
    remark      varchar(512) null                    comment '备注',
    is_enabled  tinyint      not null default 1      comment '1 启用 0 停用',
    deleted_at  datetime     null                    comment '删除时间；null=未删（逻辑删除填值，唯一键含该列以支持删后重建同编码）',
    is_occupied tinyint      not null default 0      comment '是否占用（修改锁定/被占用）：1 占用 0 未占用',
    occupied_by varchar(64)  null                    comment '占用人 IAM 账号（is_occupied=1 时有效）',
    created_at  datetime     not null default current_timestamp comment '创建时间',
    created_by  varchar(64)  null                    comment '创建人 IAM 账号',
    updated_at  datetime     not null default current_timestamp on update current_timestamp comment '更新时间',
    updated_by  varchar(64)  null                    comment '更新人 IAM 账号',
    primary key (id),
    unique key uk_area_bay_code (area_code, bay_code, deleted_at)
) engine = InnoDB default charset = utf8mb4 comment = '工段/Bay，归属 Area，AMHS Intrabay 搬运单元';

-- 插入默认数据 ---

insert into cim_bay
    (id, bay_code, area_code, bay_name, bay_kind, clean_level, remark, is_enabled, created_at, created_by, updated_at, updated_by)
values
    (1, 'ETCH-B01', 'ETCH', '刻蚀 01 工段', 'Intrabay', 'Class1',  null, 1, now(), 'system', now(), 'system'),
    (2, 'ETCH-B02', 'ETCH', '刻蚀 02 工段', 'Intrabay', 'Class1',  null, 1, now(), 'system', now(), 'system'),
    (3, 'PHOTO-B01', 'PHOTO', '光刻 01 工段', 'Intrabay', 'Class10', null, 1, now(), 'system', now(), 'system'),
    (4, 'DIFF-B01', 'DIFF', '扩散 01 工段', 'Intrabay', 'Class10', null, 1, now(), 'system', now(), 'system');

-- ============================================================
-- MDS 基础数据：Module（机台安装单元）
-- 层级：Site → Fab → Area → Bay → Module
-- 依赖：cim_bay（bay_code 逻辑外键，仅索引不建 FK）
-- ============================================================

-- 创建表结构 ---

create table cim_module (
    id          bigint       not null                comment '主键（雪花算法 IdGenerator 生成，非 DB 自增）',
    module_code varchar(64)  not null                comment 'Module 编码，Bay 内唯一，例：ETCH-B01-M01',
    bay_code    varchar(64)  not null                comment '归属 bay_code',
    module_name varchar(128) not null                comment 'Module 名称，例：刻蚀 B01 A 组机台',
    remark      varchar(512) null                    comment '备注',
    is_enabled  tinyint      not null default 1      comment '1 启用 0 停用',
    deleted_at  datetime     null                    comment '删除时间；null=未删（逻辑删除填值，唯一键含该列以支持删后重建同编码）',
    is_occupied tinyint      not null default 0      comment '是否占用（修改锁定/被占用）：1 占用 0 未占用',
    occupied_by varchar(64)  null                    comment '占用人 IAM 账号（is_occupied=1 时有效）',
    created_at  datetime     not null default current_timestamp comment '创建时间',
    created_by  varchar(64)  null                    comment '创建人 IAM 账号',
    updated_at  datetime     not null default current_timestamp on update current_timestamp comment '更新时间',
    updated_by  varchar(64)  null                    comment '更新人 IAM 账号',
    primary key (id),
    unique key uk_bay_module_code (bay_code, module_code, deleted_at)
) engine = InnoDB default charset = utf8mb4 comment = '机台安装单元（Module），归属 Bay，最小物理安装节点';

-- 插入默认数据 ---

insert into cim_module
    (id, module_code, bay_code, module_name, remark, is_enabled, created_at, created_by, updated_at, updated_by)
values
    (1, 'ETCH-B01-M01', 'ETCH-B01', '刻蚀 B01 A 组机台', null, 1, now(), 'system', now(), 'system'),
    (2, 'ETCH-B01-M02', 'ETCH-B01', '刻蚀 B01 B 组机台', null, 1, now(), 'system', now(), 'system'),
    (3, 'PHOTO-B01-M01', 'PHOTO-B01', '光刻 B01 A 组机台', null, 1, now(), 'system', now(), 'system');
