-- ============================================================
-- MDS 基础数据：组织（Org）/ 人员（Person）/ 角色（Role）及关联
-- 依赖：无（组织/人员/角色为独立主数据）；预留与 IAM/HR 衔接字段
-- 主键：id 由应用层雪花算法（IdGenerator）生成，非 DB 自增
-- 租户：不做多租户隔离（全厂全局共享）
-- 软删：deleted_at 时间戳，null=未删；唯一键含 deleted_at 以支持删后重建同编码
-- 说明：本文件含示例种子数据（id 为占位值）；正式环境种子建议走 Java seeder
-- ⚠️ 架构提醒：平台约定（身份目录 §8）IAM 管组织、AD/HR 管人、业务 AP 不存用户主数据。
--    本表按「mds-ap 作为主数据基座自持」设计；若定位改为 IAM 只读镜像，需弱化写入并改为引用型。
-- ============================================================

-- ----------------------------
-- 1) cim_org：组织架构（组织节点树）
--    parent_code 自关联建树；source 标记来源（混源隔离，见 §8）
-- ----------------------------

create table cim_org (
    id            bigint       not null                comment '主键（雪花算法 IdGenerator 生成，非 DB 自增）',
    org_code      varchar(64)  not null                comment '组织编码，全局唯一，例：ORG-ROOT、ORG-ETCH',
    org_name      varchar(128) not null                comment '组织名称，例：成都工厂、刻蚀部',
    parent_code   varchar(64)  null                    comment '父组织编码，自关联本表 org_code；顶级组织为 null',
    org_type      varchar(32)  null                    comment '组织类型：COMPANY(公司) / DEPARTMENT(部门) / TEAM(科室) / WORKSHOP(车间)',
    source        varchar(32)  null                    comment '数据来源（混源隔离）：IAM / AD / HR / MANUAL（手工）',
    manager_code  varchar(64)  null                    comment '组织负责人 person_code，逻辑外键关联 cim_person（无物理 FK）',
    sort_no       int          not null default 0      comment '同级排序号，升序显示',
    is_enabled    tinyint      not null default 1      comment '1 启用 0 停用',
    deleted_at    datetime     null                    comment '删除时间；null=未删（逻辑删除填值，唯一键含该列以支持删后重建同编码）',
    is_occupied   tinyint      not null default 0      comment '是否占用（修改锁定/被占用）：1 占用 0 未占用',
    occupied_by   varchar(64)  null                    comment '占用人 IAM 账号（is_occupied=1 时有效）',
    created_at    datetime     not null default current_timestamp comment '创建时间',
    created_by    varchar(64)  null                    comment '创建人 IAM 账号',
    updated_at    datetime     not null default current_timestamp on update current_timestamp comment '更新时间',
    updated_by    varchar(64)  null                    comment '更新人 IAM 账号',
    primary key (id),
    unique key uk_org_code (org_code, deleted_at),
    key idx_org_parent (parent_code),
    key idx_org_source (source)
) engine = InnoDB default charset = utf8mb4 comment = '组织架构（组织节点树），预留与 IAM 混源隔离(source)';

-- ----------------------------
-- 2) cim_person：人员（员工主数据）
--    primary_org_code 关联主属组织；iam_account 关联 IAM 账号（§8：认证归 IAM）
-- ----------------------------

create table cim_person (
    id              bigint       not null                comment '主键（雪花算法 IdGenerator 生成，非 DB 自增）',
    person_code     varchar(64)  not null                comment '人员编码/工号，全局唯一，例：P0001',
    person_name     varchar(128) not null                comment '人员姓名（主数据内联，不入 JWT，见 §8）',
    gender          varchar(8)   null                    comment '性别：M / F',
    email           varchar(128) null                    comment '邮箱',
    phone           varchar(32)  null                    comment '联系电话',
    primary_org_code varchar(64) null                    comment '主属组织编码，逻辑外键关联 cim_org.org_code（无物理 FK）',
    employee_status varchar(32)  null                    comment '在职状态：ACTIVE / INACTIVE / ON_LEAVE',
    hire_date       date         null                    comment '入职日期',
    iam_account     varchar(64)  null                    comment '关联 IAM 账号（认证/授权归 IAM，本表只存映射，见 §8）',
    is_enabled      tinyint      not null default 1      comment '1 启用 0 停用',
    deleted_at      datetime     null                    comment '删除时间；null=未删（逻辑删除填值，唯一键含该列以支持删后重建同编码）',
    is_occupied     tinyint      not null default 0      comment '是否占用（修改锁定/被占用）：1 占用 0 未占用',
    occupied_by     varchar(64)  null                    comment '占用人 IAM 账号（is_occupied=1 时有效）',
    created_at      datetime     not null default current_timestamp comment '创建时间',
    created_by      varchar(64)  null                    comment '创建人 IAM 账号',
    updated_at      datetime     not null default current_timestamp on update current_timestamp comment '更新时间',
    updated_by      varchar(64)  null                    comment '更新人 IAM 账号',
    primary key (id),
    unique key uk_person_code (person_code, deleted_at),
    key idx_person_org (primary_org_code),
    key idx_person_iam (iam_account)
) engine = InnoDB default charset = utf8mb4 comment = '人员（员工主数据），iam_account 关联 IAM，姓名不入 JWT';

-- ----------------------------
-- 3) cim_role：角色
--    role_type 区分系统/业务；iam_bound 标记是否同步绑定 IAM 权限
-- ----------------------------

create table cim_role (
    id           bigint       not null                comment '主键（雪花算法 IdGenerator 生成，非 DB 自增）',
    role_code    varchar(64)  not null                comment '角色编码，全局唯一，例：ROLE_ADMIN、ROLE_OPERATOR',
    role_name    varchar(128) not null                comment '角色名称',
    role_type    varchar(32)  null                    comment '角色类型：SYSTEM(系统) / BUSINESS(业务)',
    iam_bound    tinyint      not null default 0      comment '是否绑定 IAM 权限体系：1 绑定 0 仅本地',
    description  varchar(512) null                    comment '角色说明',
    is_enabled   tinyint      not null default 1      comment '1 启用 0 停用',
    deleted_at   datetime     null                    comment '删除时间；null=未删（逻辑删除填值，唯一键含该列以支持删后重建同编码）',
    is_occupied  tinyint      not null default 0      comment '是否占用（修改锁定/被占用）：1 占用 0 未占用',
    occupied_by  varchar(64)  null                    comment '占用人 IAM 账号（is_occupied=1 时有效）',
    created_at   datetime     not null default current_timestamp comment '创建时间',
    created_by   varchar(64)  null                    comment '创建人 IAM 账号',
    updated_at   datetime     not null default current_timestamp on update current_timestamp comment '更新时间',
    updated_by   varchar(64)  null                    comment '更新人 IAM 账号',
    primary key (id),
    unique key uk_role_code (role_code, deleted_at)
) engine = InnoDB default charset = utf8mb4 comment = '角色定义，iam_bound 标记是否同步 IAM 权限';

-- ----------------------------
-- 4) cim_person_role：人员-角色 关联（多对多）
--    assign_type 区分直接授权(DIRECT) / 继承自动获得(INHERITED)
-- ----------------------------

create table cim_person_role (
    id           bigint       not null                comment '主键（雪花算法 IdGenerator 生成，非 DB 自增）',
    person_code  varchar(64)  not null                comment '人员编码，逻辑外键关联 cim_person.person_code（无物理 FK）',
    role_code    varchar(64)  not null                comment '角色编码，逻辑外键关联 cim_role.role_code（无物理 FK）',
    assign_type  varchar(32)  null                    comment '授权方式：DIRECT(直接) / INHERITED(组织继承)',
    org_code     varchar(64)  null                    comment '授权作用组织（可追溯来源，INHERITED 时指向 cim_org）',
    is_enabled   tinyint      not null default 1      comment '1 启用 0 停用',
    deleted_at   datetime     null                    comment '删除时间；null=未删（逻辑删除填值，唯一键含该列以支持删后重建同编码）',
    is_occupied  tinyint      not null default 0      comment '是否占用（修改锁定/被占用）：1 占用 0 未占用',
    occupied_by  varchar(64)  null                    comment '占用人 IAM 账号（is_occupied=1 时有效）',
    created_at   datetime     not null default current_timestamp comment '创建时间',
    created_by   varchar(64)  null                    comment '创建人 IAM 账号',
    updated_at   datetime     not null default current_timestamp on update current_timestamp comment '更新时间',
    updated_by   varchar(64)  null                    comment '更新人 IAM 账号',
    primary key (id),
    unique key uk_person_role (person_code, role_code, deleted_at),
    key idx_pr_person (person_code),
    key idx_pr_role (role_code),
    key idx_pr_org (org_code)
) engine = InnoDB default charset = utf8mb4 comment = '人员-角色关联（多对多），assign_type 区分直接与继承';

-- ----------------------------
-- 5) cim_org_role：组织-角色 关联（部门级授权，成员自动继承）
--    部门被授予某角色后，该部门下人员经 INHERITED 方式获得（见 cim_person_role）
-- ----------------------------

create table cim_org_role (
    id           bigint       not null                comment '主键（雪花算法 IdGenerator 生成，非 DB 自增）',
    org_code     varchar(64)  not null                comment '组织编码，逻辑外键关联 cim_org.org_code（无物理 FK）',
    role_code    varchar(64)  not null                comment '角色编码，逻辑外键关联 cim_role.role_code（无物理 FK）',
    is_enabled   tinyint      not null default 1      comment '1 启用 0 停用',
    deleted_at   datetime     null                    comment '删除时间；null=未删（逻辑删除填值，唯一键含该列以支持删后重建同编码）',
    is_occupied  tinyint      not null default 0      comment '是否占用（修改锁定/被占用）：1 占用 0 未占用',
    occupied_by  varchar(64)  null                    comment '占用人 IAM 账号（is_occupied=1 时有效）',
    created_at   datetime     not null default current_timestamp comment '创建时间',
    created_by   varchar(64)  null                    comment '创建人 IAM 账号',
    updated_at   datetime     not null default current_timestamp on update current_timestamp comment '更新时间',
    updated_by   varchar(64)  null                    comment '更新人 IAM 账号',
    primary key (id),
    unique key uk_org_role (org_code, role_code, deleted_at),
    key idx_or_org (org_code),
    key idx_or_role (role_code)
) engine = InnoDB default charset = utf8mb4 comment = '组织-角色关联（部门级授权），成员经 INHERITED 继承';

-- ============================================================
-- 示例种子（占位 id；正式走 Java seeder）
-- ============================================================

insert into cim_org
    (id, org_code, org_name, parent_code, org_type, source, manager_code, sort_no, is_enabled, created_at, created_by, updated_at, updated_by)
values
    (1, 'ORG-ROOT', '成都工厂',     null,       'COMPANY',    'MANUAL', null,       1, 1, now(), 'system', now(), 'system'),
    (2, 'ORG-MES',  'MES 部',      'ORG-ROOT', 'DEPARTMENT', 'MANUAL', 'P0001',    1, 1, now(), 'system', now(), 'system'),
    (3, 'ORG-ETCH', '刻蚀部',       'ORG-MES',  'DEPARTMENT', 'MANUAL', null,       2, 1, now(), 'system', now(), 'system');

insert into cim_role
    (id, role_code, role_name, role_type, iam_bound, description, is_enabled, created_at, created_by, updated_at, updated_by)
values
    (1, 'ROLE_ADMIN',    '系统管理员', 'SYSTEM',    1, '平台管理权限',       1, now(), 'system', now(), 'system'),
    (2, 'ROLE_OPERATOR', '产线操作员', 'BUSINESS',  1, '产线设备操作权限',   1, now(), 'system', now(), 'system');

insert into cim_person
    (id, person_code, person_name, gender, email, phone, primary_org_code, employee_status, hire_date, iam_account, is_enabled, created_at, created_by, updated_at, updated_by)
values
    (1, 'P0001', '张三', 'M', 'zhangsan@fab.com', '13800000001', 'ORG-MES', 'ACTIVE', '2024-01-01', 'zhangsan', 1, now(), 'system', now(), 'system'),
    (2, 'P0002', '李四', 'F', 'lisi@fab.com',     '13800000002', 'ORG-ETCH','ACTIVE', '2024-03-15', 'lisi',    1, now(), 'system', now(), 'system');

-- 人员-角色：张三直接获管理员；李四直接获操作员
insert into cim_person_role
    (id, person_code, role_code, assign_type, org_code, is_enabled, created_at, created_by, updated_at, updated_by)
values
    (1, 'P0001', 'ROLE_ADMIN',    'DIRECT',   null,     1, now(), 'system', now(), 'system'),
    (2, 'P0002', 'ROLE_OPERATOR', 'DIRECT',   'ORG-ETCH',1, now(), 'system', now(), 'system');

-- 组织-角色：刻蚀部整体获操作员角色（该部新成员经 INHERITED 继承）
insert into cim_org_role
    (id, org_code, role_code, is_enabled, created_at, created_by, updated_at, updated_by)
values
    (1, 'ORG-ETCH', 'ROLE_OPERATOR', 1, now(), 'system', now(), 'system');
