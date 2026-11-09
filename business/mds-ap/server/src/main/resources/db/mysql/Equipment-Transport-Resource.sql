-- ============================================================
-- MDS 设备域主数据：Equipment（工艺机台）/ Transport Equipment（AMHS 搬运设备）/ Resource（端口·工艺资源·缓冲）
-- 设计依据：行业惯例（SEMI E10/E30/E84；工艺设备与物料搬运设备分离；按 Capability 派工）
--           + 参考商业 MES 设备模型 + 本项目既有范式（Site→Fab→Area→Bay→Module 物理层级、RMS 已有 Device/Chamber）
-- 主键：id 由应用层雪花算法（IdGenerator）生成，非 DB 自增
-- 租户：不做多租户隔离（全厂全局共享）
-- 软删：deleted_at 时间截，null=未删；唯一键含 deleted_at 以支持删后重建同编码
-- 占控：实体表含 is_occupied/occupied_by（修改占用锁）；关联/桥表不含（占控作用于被编辑的主实体）
-- 逻辑外键：仅索引不建 FK（与 Site/Fab/Area/Bay/Module 一致）
-- 范围边界：本文件只承载资产「身份与分类维度」；运行时状态(IDLE/RUN/recipe/报警)留给 RMS/MES。
--           配置层(EDC / Interval Q-Time / Process Batch / Maintenance / Dispatch Logic / Reticle / Sort)
--           属 MES 配置，不在本主数据层建表（划线排除，后续单独设计）。
-- 状态建模：统一三维框架 cim_state_def / cim_state_transition（entity_type × state_model × state_code）。
--           E10 设备=单状态机(层级树, 任一时刻一态)；E87 载具(含FOUP)/端口=多并发状态模型(各维度并存)；E84 物理握手不进字典(留运行时)。
--
-- 术语对齐：本文件 equipment = 工艺机台（即用户所述 Machine）；transport_equipment = AMHS 搬运设备
--           （OHT / OHS / Stocker / OHB / NTB，即用户 AMHS 子系统）；process_resource = 工艺资源（含 Chamber 腔体）。
-- ============================================================

-- ============================================================
-- 1. 设备类型树（Equipment Type，三层：Kind → Category → Type）
--    单表自关联树：tree_level=1 顶层 Kind(PROCESS/METROLOGY/CLEAN…)、=2 中层 Category(LITHO/ETCH…)、=3 叶子 Type(具体型号族)
--    Equipment 只存叶子 type_code；按 Kind/Category 聚合时沿树回滚。
-- ============================================================

create table cim_equipment_type (
    id           bigint       not null                comment '主键（雪花算法 IdGenerator 生成，非 DB 自增）',
    type_code    varchar(64)  not null                comment '类型编码，全局唯一，例：PROCESS / LITHO / SCANNER_193I',
    type_name    varchar(128) not null                comment '类型名称',
    parent_code  varchar(64)  null                    comment '父类型编码，自关联 cim_equipment_type；顶层为 null',
    tree_level   tinyint      not null                comment '层级：1=Kind 顶层 / 2=Category 中层 / 3=Type 叶子',
    i18n_key     varchar(128) null                    comment '名称翻译键，关联 cim_i18n_message.i18n_key',
    description  varchar(512) null                    comment '说明',
    sort_no      int          not null default 0      comment '同级排序',
    is_enabled   tinyint      not null default 1      comment '1 启用 0 停用',
    deleted_at   datetime     null                    comment '删除时间；null=未删（唯一键含该列以支持删后重建同编码）',
    is_occupied  tinyint      not null default 0      comment '是否占用（修改锁定）：1 占用 0 未占用',
    occupied_by  varchar(64)  null                    comment '占用人 IAM 账号（is_occupied=1 时有效）',
    created_at   datetime     not null default current_timestamp comment '创建时间',
    created_by   varchar(64)  null                    comment '创建人 IAM 账号',
    updated_at   datetime     not null default current_timestamp on update current_timestamp comment '更新时间',
    updated_by   varchar(64)  null                    comment '更新人 IAM 账号',
    primary key (id),
    unique key uk_type_code (type_code, deleted_at),
    key idx_type_parent (parent_code)
) engine = InnoDB default charset = utf8mb4 comment = '设备类型树（Kind→Category→Type 三级自关联）';

-- ============================================================
-- 2. 技术节点目录（Technology）
--    工艺技术节点/工艺族，例：6inch、某具体 node。Capability 可归属某一 tech。
-- ============================================================

create table cim_tech (
    id          bigint       not null                comment '主键（雪花算法 IdGenerator 生成，非 DB 自增）',
    tech_code   varchar(64)  not null                comment '技术节点编码，全局唯一，例：NODE_6INCH',
    tech_name   varchar(128) not null                comment '技术节点名称',
    tech_node   varchar(32)  null                    comment '节点规格：6inch / 8inch / 12inch 等',
    process_family varchar(64) null                  comment '工艺族，例：FEOL / BEOL',
    description varchar(512) null                    comment '说明',
    is_enabled  tinyint      not null default 1      comment '1 启用 0 停用',
    deleted_at  datetime     null                    comment '删除时间；null=未删（唯一键含该列以支持删后重建同编码）',
    is_occupied tinyint      not null default 0      comment '是否占用（修改锁定）：1 占用 0 未占用',
    occupied_by varchar(64)  null                    comment '占用人 IAM 账号（is_occupied=1 时有效）',
    created_at  datetime     not null default current_timestamp comment '创建时间',
    created_by  varchar(64)  null                    comment '创建人 IAM 账号',
    updated_at  datetime     not null default current_timestamp on update current_timestamp comment '更新时间',
    updated_by  varchar(64)  null                    comment '更新人 IAM 账号',
    primary key (id),
    unique key uk_tech_code (tech_code, deleted_at)
) engine = InnoDB default charset = utf8mb4 comment = '工艺技术节点目录';

-- ============================================================
-- 3. 能力目录（Capability）
--    「设备能干什么」的派工单元，独立于设备类型。Equipment 经桥表挂载多个 Capability。
-- ============================================================

create table cim_capability (
    id              bigint       not null                comment '主键（雪花算法 IdGenerator 生成，非 DB 自增）',
    capability_code varchar(64)  not null                comment '能力编码，全局唯一，例：LITHO_TRACK_193',
    capability_name varchar(128) not null                comment '能力名称',
    tech_code       varchar(64)  null                    comment '归属技术节点，→ cim_tech（可空）',
    description     varchar(512) null                    comment '说明',
    i18n_key        varchar(128) null                    comment '名称翻译键，关联 cim_i18n_message.i18n_key',
    is_enabled      tinyint      not null default 1      comment '1 启用 0 停用',
    deleted_at      datetime     null                    comment '删除时间；null=未删（唯一键含该列以支持删后重建同编码）',
    is_occupied     tinyint      not null default 0      comment '是否占用（修改锁定）：1 占用 0 未占用',
    occupied_by     varchar(64)  null                    comment '占用人 IAM 账号（is_occupied=1 时有效）',
    created_at      datetime     not null default current_timestamp comment '创建时间',
    created_by      varchar(64)  null                    comment '创建人 IAM 账号',
    updated_at      datetime     not null default current_timestamp on update current_timestamp comment '更新时间',
    updated_by      varchar(64)  null                    comment '更新人 IAM 账号',
    primary key (id),
    unique key uk_capability_code (capability_code, deleted_at),
    key idx_capability_tech (tech_code)
) engine = InnoDB default charset = utf8mb4 comment = '设备能力目录（派工单元，独立于设备类型）';

-- ============================================================
-- 4. 工艺机台主数据（Equipment = 用户所述 Machine）
--    参考 Categorization：type_code / control_mode / capable_recipe_mode / contamination_level / carrier_separate_flag
--    + Overview：initial_state_code(引用 cim_state_def, entity_type=EQUIPMENT,state_model=MAIN) / owner / supplier / fab_code / module_code / standard_wph
-- ============================================================

create table cim_equipment (
    id                    bigint       not null                comment '主键（雪花算法 IdGenerator 生成，非 DB 自增）',
    equipment_code        varchar(64)  not null                comment '机台编码，全局唯一，例：E-TCH-01',
    equipment_name        varchar(128) not null                comment '机台名称',
    type_code             varchar(64)  not null                comment '设备类型（叶子），→ cim_equipment_type',
    control_mode          varchar(32)  null                    comment '设备控制模式：AUTO / MANUAL / SEMIAUTO',
    capable_recipe_mode   varchar(32)  null                    comment '配方模式：ONLINE / OFFLINE / SEMIAUTO',
    contamination_level   varchar(32)  null                    comment '洁净等级（污染等级）：ISO3 / ISO4 / ISO5 等',
    carrier_separate_flag tinyint      null                    comment '载具是否按产品隔离：1 是 0 否',
    initial_state_code    varchar(32)  null                    comment '初始设备状态（引用 cim_state_def, entity_type=EQUIPMENT,state_model=MAIN 的 state_code），须为 is_initial_allowed=1 的状态',
    owner                 varchar(64)  null                    comment '机台负责人 IAM 账号',
    supplier_name         varchar(64)  null                    comment '制造商名称',
    model_no              varchar(64)  null                    comment '型号',
    serial_no             varchar(64)  null                    comment '出厂序列号',
    asset_status          varchar(32)  null                    comment '资产状态：INSTALLED / DEC / NOTED / TRANSFER',
    fab_code              varchar(64)  null                    comment '归属 Fab，→ mds.cim_fab（参考 Fab ID）',
    module_code           varchar(64)  null                    comment '安装位置，→ mds.cim_module（最小物理安装单元）',
    standard_wph          decimal(10,2) null                  comment '标准 WPH（片/小时）',
    source                varchar(32)  null                    comment '数据来源：MANUAL / IAM / SYN（混源隔离）',
    install_date          date         null                    comment '安装日期',
    remark                varchar(512) null                    comment '备注',
    is_enabled            tinyint      not null default 1      comment '1 启用 0 停用',
    deleted_at            datetime     null                    comment '删除时间；null=未删（唯一键含该列以支持删后重建同编码）',
    is_occupied           tinyint      not null default 0      comment '是否占用（修改锁定）：1 占用 0 未占用',
    occupied_by           varchar(64)  null                    comment '占用人 IAM 账号（is_occupied=1 时有效）',
    created_at            datetime     not null default current_timestamp comment '创建时间',
    created_by            varchar(64)  null                    comment '创建人 IAM 账号',
    updated_at            datetime     not null default current_timestamp on update current_timestamp comment '更新时间',
    updated_by            varchar(64)  null                    comment '更新人 IAM 账号',
    primary key (id),
    unique key uk_equipment_code (equipment_code, deleted_at),
    key idx_equipment_type (type_code),
    key idx_equipment_fab (fab_code),
    key idx_equipment_module (module_code)
) engine = InnoDB default charset = utf8mb4 comment = '工艺机台主数据（Equipment）';

-- ============================================================
-- 5. 工艺资源（Process Resource = 腔体 Chamber / 量测 / 缓冲）
--    参考 Process Resources（Serial/Parallel 关系）。Chamber 即 type=CHAMBER 的工艺资源；
--    单腔体机台也建一条 resource_code=equipment_code 的占位腔，保持「recipe/资格都挂资源」一致。
-- ============================================================

create table cim_process_resource (
    id            bigint       not null                comment '主键（雪花算法 IdGenerator 生成，非 DB 自增）',
    resource_code varchar(64)  not null                comment '资源编码，机台内唯一，例：CH-A / PM1',
    equipment_code varchar(64) not null                comment '归属机台，→ cim_equipment',
    resource_type varchar(32)  not null                comment '资源类型：CHAMBER / MEASURE / BUFFER',
    resource_name varchar(128) null                    comment '资源名称',
    run_mode      varchar(32)  null                    comment '运行关系：SERIAL(串行) / PARALLEL(并行)',
    parallel_group varchar(32) null                    comment '并行分组标识（同组并行资源归一组）',
    stage_seq     int          null                    comment '串行阶段序号（SERIAL 时生效）',
    sort_no       int          not null default 0      comment '同级排序',
    remark        varchar(512) null                    comment '备注',
    is_enabled    tinyint      not null default 1      comment '1 启用 0 停用',
    deleted_at    datetime     null                    comment '删除时间；null=未删（唯一键含该列以支持删后重建同编码）',
    is_occupied   tinyint      not null default 0      comment '是否占用（修改锁定）：1 占用 0 未占用',
    occupied_by   varchar(64)  null                    comment '占用人 IAM 账号（is_occupied=1 时有效）',
    created_at    datetime     not null default current_timestamp comment '创建时间',
    created_by    varchar(64)  null                    comment '创建人 IAM 账号',
    updated_at    datetime     not null default current_timestamp on update current_timestamp comment '更新时间',
    updated_by    varchar(64)  null                    comment '更新人 IAM 账号',
    primary key (id),
    unique key uk_equipment_resource (equipment_code, resource_code, deleted_at),
    key idx_resource_equipment (equipment_code)
) engine = InnoDB default charset = utf8mb4 comment = '工艺资源（含 Chamber 腔体/量测/缓冲，承载 Serial/Parallel 关系）';

-- ============================================================
-- 6. 输入输出端口（Input-Output Port / E84 交接点）
--    参考 Port List + Control Model。E84 握手落点，机台与 AMHS 载具交接处。
-- ============================================================

create table cim_port (
    id            bigint       not null                comment '主键（雪花算法 IdGenerator 生成，非 DB 自增）',
    port_code     varchar(64)  not null                comment '端口编码，机台内唯一，例：P1 / LOAD_1',
    equipment_code varchar(64) not null                comment '归属机台，→ cim_equipment',
    port_no       int          null                    comment '端口序号，例：1 / 2',
    port_type     varchar(32)  null                    comment '端口类型：LOAD / UNLOAD / DUAL',
    control_model varchar(32)  null                    comment 'E84 控制模式',
    e84_enabled   tinyint      null                    comment '是否启用 E84 握手：1 是 0 否',
    handoff_mode  varchar(32)  null                    comment '交接模式：PUSH / PULL',
    carrier_type  varchar(32)  null                    comment '载具类型：FOUP / FRM',
    remark        varchar(512) null                    comment '备注',
    is_enabled    tinyint      not null default 1      comment '1 启用 0 停用',
    deleted_at    datetime     null                    comment '删除时间；null=未删（唯一键含该列以支持删后重建同编码）',
    is_occupied   tinyint      not null default 0      comment '是否占用（修改锁定）：1 占用 0 未占用',
    occupied_by   varchar(64)  null                    comment '占用人 IAM 账号（is_occupied=1 时有效）',
    created_at    datetime     not null default current_timestamp comment '创建时间',
    created_by    varchar(64)  null                    comment '创建人 IAM 账号',
    updated_at    datetime     not null default current_timestamp on update current_timestamp comment '更新时间',
    updated_by    varchar(64)  null                    comment '更新人 IAM 账号',
    primary key (id),
    unique key uk_equipment_port (equipment_code, port_code, deleted_at),
    key idx_port_equipment (equipment_code)
) engine = InnoDB default charset = utf8mb4 comment = '机台输入输出端口（E84 载具交接点）';

-- ============================================================
-- 7. 搬运设备类别（Transport Equipment Category）
--    参考 Transport Equipment Category ID。OHT / STOCKER / OHB / NTB / OHS 等。
-- ============================================================

create table cim_transport_equipment_category (
    id            bigint       not null                comment '主键（雪花算法 IdGenerator 生成，非 DB 自增）',
    category_code varchar(64)  not null                comment '类别编码，全局唯一，例：OHT / STOCKER / OHB / NTB / OHS',
    category_name varchar(128) not null                comment '类别名称',
    description   varchar(512) null                    comment '说明',
    i18n_key      varchar(128) null                    comment '名称翻译键，关联 cim_i18n_message.i18n_key',
    is_enabled    tinyint      not null default 1      comment '1 启用 0 停用',
    deleted_at    datetime     null                    comment '删除时间；null=未删（唯一键含该列以支持删后重建同编码）',
    is_occupied   tinyint      not null default 0      comment '是否占用（修改锁定）：1 占用 0 未占用',
    occupied_by   varchar(64)  null                    comment '占用人 IAM 账号（is_occupied=1 时有效）',
    created_at    datetime     not null default current_timestamp comment '创建时间',
    created_by    varchar(64)  null                    comment '创建人 IAM 账号',
    updated_at    datetime     not null default current_timestamp on update current_timestamp comment '更新时间',
    updated_by    varchar(64)  null                    comment '更新人 IAM 账号',
    primary key (id),
    unique key uk_te_category_code (category_code, deleted_at)
) engine = InnoDB default charset = utf8mb4 comment = 'AMHS 搬运设备类别（OHT/Stocker/OHB/NTB/OHS）';

-- ============================================================
-- 8. 搬运设备主数据（Transport Equipment = AMHS 子系统）
--    参考 Transport Equipment Overview：Fab ID / initial_state_code(引用 cim_state_def, entity_type=EQUIPMENT,state_model=MAIN) / owner / supplier / purge_control /
--    near_tool_control / max_capacity / eap_host。与工艺机台并列、独立建模（非机台子表）。
-- ============================================================

create table cim_transport_equipment (
    id                        bigint       not null                comment '主键（雪花算法 IdGenerator 生成，非 DB 自增）',
    transport_equipment_code  varchar(64)  not null                comment '搬运设备编码，全局唯一，例：OHT-01 / STK-01',
    transport_equipment_name  varchar(128) not null                comment '搬运设备名称',
    category_code             varchar(64)  not null                comment '搬运设备类别，→ cim_transport_equipment_category',
    fab_code                  varchar(64)  null                    comment '归属 Fab，→ mds.cim_fab（参考 Fab ID）',
    module_code               varchar(64)  null                    comment '安装位置，→ mds.cim_module（Stocker/OHB 常用，OHT 可空）',
    initial_state_code        varchar(32)  null                    comment '初始状态（引用 cim_state_def, entity_type=EQUIPMENT,state_model=MAIN 的 state_code），须为 is_initial_allowed=1 的状态',
    owner                     varchar(64)  null                    comment '负责人 IAM 账号',
    supplier_name             varchar(64)  null                    comment '制造商名称',
    model_no                  varchar(64)  null                    comment '型号',
    serial_no                 varchar(64)  null                    comment '出厂序列号',
    purge_control             tinyint      null                    comment '吹扫控制：1 启用 0 否',
    near_tool_control         tinyint      null                    comment '近机台控制（服务近机台）：1 启用 0 否',
    max_capacity              int          null                    comment '最大载具容量（设计值，静态）：OHT=在途承载数(通常1)；Stocker/OHB/NTB=存储槽位数。当前可用容量属运行时衍生值，不落主数据',
    eap_host                  varchar(64)  null                    comment 'EAP 主机标识',
    asset_status              varchar(32)  null                    comment '资产状态：INSTALLED / DEC / NOTED / TRANSFER',
    source                    varchar(32)  null                    comment '数据来源：MANUAL / IAM / SYN',
    remark                    varchar(512) null                    comment '备注',
    is_enabled                tinyint      not null default 1      comment '1 启用 0 停用',
    deleted_at                datetime     null                    comment '删除时间；null=未删（唯一键含该列以支持删后重建同编码）',
    is_occupied               tinyint      not null default 0      comment '是否占用（修改锁定）：1 占用 0 未占用',
    occupied_by               varchar(64)  null                    comment '占用人 IAM 账号（is_occupied=1 时有效）',
    created_at                datetime     not null default current_timestamp comment '创建时间',
    created_by                varchar(64)  null                    comment '创建人 IAM 账号',
    updated_at                datetime     not null default current_timestamp on update current_timestamp comment '更新时间',
    updated_by                varchar(64)  null                    comment '更新人 IAM 账号',
    primary key (id),
    unique key uk_transport_equipment_code (transport_equipment_code, deleted_at),
    key idx_te_category (category_code),
    key idx_te_fab (fab_code)
) engine = InnoDB default charset = utf8mb4 comment = 'AMHS 搬运设备主数据（OHT/Stocker/OHB/NTB/OHS）';

-- ============================================================
-- 9. 机台 ↔ 搬运设备 近邻映射（Equipment ↔ Transport，含优先级）
--    参考 Near Tool OHBs / Near Tool Priority。替代简陋的 port-amhs，承载「机台由哪些 OHB/Stocker 服务、优先级」。
--    关联表：不含 is_occupied（占控作用于主实体）。
-- ============================================================

create table cim_equipment_transport (
    id                        bigint       not null                comment '主键（雪花算法 IdGenerator 生成，非 DB 自增）',
    equipment_code            varchar(64)  not null                comment '工艺机台，→ cim_equipment',
    transport_equipment_code  varchar(64)  not null                comment '搬运设备，→ cim_transport_equipment',
    near_tool_priority        int          not null default 0      comment '近机台服务优先级，越小越优先',
    service_role              varchar(32)  null                    comment '服务角色：PRIMARY / SECONDARY',
    deleted_at                datetime     null                    comment '删除时间；null=未删（唯一键含该列以支持删后重建同编码）',
    created_at                datetime     not null default current_timestamp comment '创建时间',
    created_by                varchar(64)  null                    comment '创建人 IAM 账号',
    updated_at                datetime     not null default current_timestamp on update current_timestamp comment '更新时间',
    updated_by                varchar(64)  null                    comment '更新人 IAM 账号',
    primary key (id),
    unique key uk_eq_transport (equipment_code, transport_equipment_code, deleted_at),
    key idx_et_equipment (equipment_code),
    key idx_et_transport (transport_equipment_code)
) engine = InnoDB default charset = utf8mb4 comment = '机台与搬运设备近邻服务映射（含优先级）';

-- ============================================================
-- 10. 机组目录（Machine Group，资产组/调度组）
--     group_kind 区分 ASSET(资产归类) 与 DISPATCH(调度分组)；parent_code 预留组嵌套。
-- ============================================================

create table cim_machine_group (
    id           bigint       not null                comment '主键（雪花算法 IdGenerator 生成，非 DB 自增）',
    group_code   varchar(64)  not null                comment '机组编码，全局唯一，例：GRP_LITHO_A',
    group_name   varchar(128) not null                comment '机组名称',
    group_kind   varchar(32)  not null                comment '机组种类：ASSET(资产组) / DISPATCH(调度组)',
    parent_code  varchar(64)  null                    comment '父机组编码，自关联（预留组嵌套，可空）',
    description  varchar(512) null                    comment '说明',
    is_enabled   tinyint      not null default 1      comment '1 启用 0 停用',
    deleted_at   datetime     null                    comment '删除时间；null=未删（唯一键含该列以支持删后重建同编码）',
    is_occupied  tinyint      not null default 0      comment '是否占用（修改锁定）：1 占用 0 未占用',
    occupied_by  varchar(64)  null                    comment '占用人 IAM 账号（is_occupied=1 时有效）',
    created_at   datetime     not null default current_timestamp comment '创建时间',
    created_by   varchar(64)  null                    comment '创建人 IAM 账号',
    updated_at   datetime     not null default current_timestamp on update current_timestamp comment '更新时间',
    updated_by   varchar(64)  null                    comment '更新人 IAM 账号',
    primary key (id),
    unique key uk_group_code (group_code, deleted_at),
    key idx_group_parent (parent_code)
) engine = InnoDB default charset = utf8mb4 comment = '机组目录（资产组/调度组）';

-- ============================================================
-- 11. 机组成员（半动态）
--     成员随 PM/排产增减；priority 调度权重，member_state 区分 ACTIVE/STANDBY 备用机。
--     关联表：不含 is_occupied。
-- ============================================================

create table cim_machine_group_member (
    id            bigint       not null                comment '主键（雪花算法 IdGenerator 生成，非 DB 自增）',
    group_code    varchar(64)  not null                comment '机组，→ cim_machine_group',
    equipment_code varchar(64) not null                comment '机台，→ cim_equipment',
    priority      int          not null default 0      comment '同组调度优先级/权重，越小越优先',
    member_state  varchar(32)  not null default 'ACTIVE' comment '成员状态：ACTIVE / STANDBY',
    deleted_at    datetime     null                    comment '删除时间；null=未删（唯一键含该列以支持删后重建同编码）',
    created_at    datetime     not null default current_timestamp comment '创建时间',
    created_by    varchar(64)  null                    comment '创建人 IAM 账号',
    updated_at    datetime     not null default current_timestamp on update current_timestamp comment '更新时间',
    updated_by    varchar(64)  null                    comment '更新人 IAM 账号',
    primary key (id),
    unique key uk_group_member (group_code, equipment_code, deleted_at),
    key idx_gm_group (group_code),
    key idx_gm_equipment (equipment_code)
) engine = InnoDB default charset = utf8mb4 comment = '机组成员（半动态，随 PM/排产增减）';

-- ============================================================
-- 12. 设备能力资格（半动态）
--     Equipment ↔ Capability 多对多 + 资格状态/有效期。资格默认在机台级（多腔体精细到腔体可后续扩列）。
--     关联表：不含 is_occupied。
-- ============================================================

create table cim_equipment_capability (
    id              bigint       not null                comment '主键（雪花算法 IdGenerator 生成，非 DB 自增）',
    equipment_code  varchar(64)  not null                comment '机台，→ cim_equipment',
    capability_code varchar(64)  not null                comment '能力，→ cim_capability',
    qual_state      varchar(32)  not null default 'PENDING' comment '资格状态：QUALIFIED / UNQUALIFIED / PENDING',
    qual_date       date         null                    comment '资格评定日期',
    expire_date     date         null                    comment '资格有效期止（可空=长期）',
    deleted_at      datetime     null                    comment '删除时间；null=未删（唯一键含该列以支持删后重建同编码）',
    created_at      datetime     not null default current_timestamp comment '创建时间',
    created_by      varchar(64)  null                    comment '创建人 IAM 账号',
    updated_at      datetime     not null default current_timestamp on update current_timestamp comment '更新时间',
    updated_by      varchar(64)  null                    comment '更新人 IAM 账号',
    primary key (id),
    unique key uk_eq_capability (equipment_code, capability_code, deleted_at),
    key idx_ec_equipment (equipment_code),
    key idx_ec_capability (capability_code)
) engine = InnoDB default charset = utf8mb4 comment = '设备能力资格（半动态，可复评/取消）';

-- ============================================================
-- 13. 设备自动化开关（Control Setting，配置类）
--     参考 Control Setting 的 ~12 个自动化开关（Recipe Body Manage / Carrier Change Required /
--     EAP Precheck / Wafer ID Readable …）。每行一个开关，便于审计/扩展/权限过滤，无需改 DDL。
--     关联表：不含 is_occupied。
-- ============================================================

create table cim_equipment_control_setting (
    id              bigint       not null                comment '主键（雪花算法 IdGenerator 生成，非 DB 自增）',
    equipment_code  varchar(64)  not null                comment '机台，→ cim_equipment',
    setting_key     varchar(64)  not null                comment '开关键，例：RECIPE_BODY_MANAGE / EAP_PRECHECK_REQUIRED',
    setting_value   varchar(64)  null                    comment '开关值，例：Y / N 或具体值',
    description     varchar(256) null                    comment '开关说明',
    deleted_at      datetime     null                    comment '删除时间；null=未删（唯一键含该列以支持删后重建同编码）',
    created_at      datetime     not null default current_timestamp comment '创建时间',
    created_by      varchar(64)  null                    comment '创建人 IAM 账号',
    updated_at      datetime     not null default current_timestamp on update current_timestamp comment '更新时间',
    updated_by      varchar(64)  null                    comment '更新人 IAM 账号',
    primary key (id),
    unique key uk_eq_setting (equipment_code, setting_key, deleted_at),
    key idx_cs_equipment (equipment_code)
) engine = InnoDB default charset = utf8mb4 comment = '设备自动化控制开关（配置类，逐键存储）';

-- ============================================================
-- 14. 状态字典（统一三维框架：entity_type × state_model × state_code）
--     E10 设备=单状态机(层级树, 任一时刻占一个叶子态)；E87 载具/端口=多并发状态模型(各维度并存)。
--     EQUIPMENT/MAIN：availability/is_productive/is_initial_allowed/applicable_scope 有意义；
--     CARRIER/PORT：这些列恒 null（并发模型无"产出/可用性"语义）。
--     来源：SEMI E10（设备状态模型）+ SEMI E87（Carrier/Port 状态模型）；E84 物理握手不进字典。
-- ============================================================

create table cim_state_def (
    id                 bigint       not null                comment '主键（雪花算法 IdGenerator 生成，非 DB 自增）',
    entity_type        varchar(32)  not null                comment '状态对象类型：EQUIPMENT(工艺机台+搬运设备) / CARRIER(载具,含FOUP) / PORT(装卸口)',
    state_model        varchar(32)  not null                comment '状态模型维度：EQUIPMENT=MAIN(单树); CARRIER=ID/SLOTMAP/ACCESS/CRTU(并发四模型); PORT=TRANSFER/ACCESS_MODE/RESERVATION/ASSOCIATION(并发四模型)',
    state_code         varchar(32)  not null                comment '状态编码，在(entity_type,state_model)内唯一，例：PRD / IDL / ID_OK / READY_TO_LOAD',
    state_name         varchar(128) not null                comment '状态名称',
    parent_code        varchar(32)  null                    comment '父状态编码，自关联本表（同 entity_type+state_model）；顶层为 null，体现 E10 层级树',
    state_level        tinyint      not null default 1      comment '状态层级：1=顶层主状态 / 2=子状态（如 E10 SDT→PM、UDT→REPAIR）',
    state_category     varchar(32)  null                    comment 'E10 状态大类（仅 EQUIPMENT/MAIN）：PRD / IDL / SDT / UDT / NST',
    availability       varchar(16)  null                    comment '可用性（仅 EQUIPMENT/MAIN）：UP / DOWN；Carrier/Port 为 null',
    is_productive      tinyint      null                    comment '是否产出（仅 EQUIPMENT/MAIN，计入 WPH/利用率）：1 是 0 否',
    is_initial_allowed tinyint      null                    comment '是否可作初始状态（设备启动初值，仅 EQUIPMENT/MAIN 约束）：1 是 0 否',
    applicable_scope   varchar(32)  null                    comment '适用设备范围（仅 EQUIPMENT/MAIN）：ALL / PROCESS / TRANSPORT；Carrier/Port 填 ALL',
    i18n_key           varchar(128) null                    comment '名称翻译键，关联 cim_i18n_message.i18n_key',
    description        varchar(512) null                    comment '说明',
    is_enabled         tinyint      not null default 1      comment '1 启用 0 停用',
    deleted_at         datetime     null                    comment '删除时间；null=未删（唯一键含该列以支持删后重建同编码）',
    is_occupied        tinyint      not null default 0      comment '是否占用（修改锁定）：1 占用 0 未占用',
    occupied_by        varchar(64)  null                    comment '占用人 IAM 账号（is_occupied=1 时有效）',
    created_at         datetime     not null default current_timestamp comment '创建时间',
    created_by         varchar(64)  null                    comment '创建人 IAM 账号',
    updated_at         datetime     not null default current_timestamp on update current_timestamp comment '更新时间',
    updated_by         varchar(64)  null                    comment '更新人 IAM 账号',
    primary key (id),
    unique key uk_state_def (entity_type, state_model, state_code, deleted_at)
) engine = InnoDB default charset = utf8mb4 comment = '状态字典（统一框架：E10 单树 + E87 多并发模型）';

-- ============================================================
-- 15. 状态转换矩阵（白名单，统一三维）
--     仅登记「允许」的转换（from_state → to_state）；运行时按 (entity_type,state_model) 过滤白名单，
--     目标态不在列则拒绝状态变更。关联/规则表：不含 is_occupied（规则集，非被编辑主实体）。
-- ============================================================

create table cim_state_transition (
    id           bigint       not null                comment '主键（雪花算法 IdGenerator 生成，非 DB 自增）',
    entity_type  varchar(32)  not null                comment '状态对象类型：EQUIPMENT / CARRIER / PORT（与 cim_state_def 同维）',
    state_model  varchar(32)  not null                comment '状态模型维度（与 cim_state_def 同维）',
    from_state   varchar(32)  not null                comment '源状态，→ cim_state_def.state_code',
    to_state     varchar(32)  not null                comment '目标状态，→ cim_state_def.state_code',
    event_code   varchar(32)  null                    comment '触发事件，例：START / STOP / FAULT / MAINT_START',
    description  varchar(256) null                    comment '说明',
    deleted_at   datetime     null                    comment '删除时间；null=未删（唯一键含该列以支持删后重建同编码）',
    created_at   datetime     not null default current_timestamp comment '创建时间',
    created_by   varchar(64)  null                    comment '创建人 IAM 账号',
    updated_at   datetime     not null default current_timestamp on update current_timestamp comment '更新时间',
    updated_by   varchar(64)  null                    comment '更新人 IAM 账号',
    primary key (id),
    unique key uk_state_transition (entity_type, state_model, from_state, to_state, deleted_at),
    key idx_transition_to (entity_type, state_model, to_state)
) engine = InnoDB default charset = utf8mb4 comment = '状态转换矩阵（白名单，仅登记允许转换）';

-- ============================================================
-- 默认数据（占位 id；正式环境建议走 Java seeder）
-- 覆盖：类型树 3 层、tech、capability、1 台刻蚀机(含腔体+端口)、1 OHT+1 Stocker、近邻映射、1 调度机组+成员、1 资格、关键控制开关、E10/E87 状态字典与转换矩阵（统一三维框架）
-- ============================================================

insert into cim_equipment_type
    (id, type_code, type_name, parent_code, tree_level, sort_no, is_enabled, created_at, created_by, updated_at, updated_by)
values
    (1, 'PROCESS', '工艺设备',     null,     1, 10, 1, now(), 'system', now(), 'system'),
    (2, 'LITHO',   '光刻',         'PROCESS', 2, 10, 1, now(), 'system', now(), 'system'),
    (3, 'ETCH',    '刻蚀',         'PROCESS', 2, 20, 1, now(), 'system', now(), 'system'),
    (4, 'SCANNER', '光刻扫描机',   'LITHO',  3, 10, 1, now(), 'system', now(), 'system'),
    (5, 'DRY_ETCH','干法刻蚀机',   'ETCH',   3, 10, 1, now(), 'system', now(), 'system');

insert into cim_tech
    (id, tech_code, tech_name, tech_node, process_family, is_enabled, created_at, created_by, updated_at, updated_by)
values
    (1, 'NODE_6INCH', '6 英寸节点', '6inch', 'FEOL', 1, now(), 'system', now(), 'system');

insert into cim_capability
    (id, capability_code, capability_name, tech_code, is_enabled, created_at, created_by, updated_at, updated_by)
values
    (1, 'LITHO_EXPOSE_193', '光刻曝光 193nm', 'NODE_6INCH', 1, now(), 'system', now(), 'system'),
    (2, 'ETCH_POLY',        '多晶硅刻蚀',      'NODE_6INCH', 1, now(), 'system', now(), 'system');

insert into cim_equipment
    (id, equipment_code, equipment_name, type_code, control_mode, capable_recipe_mode, contamination_level,
     carrier_separate_flag, initial_state_code, owner, supplier_name, model_no, serial_no, asset_status,
     fab_code, module_code, standard_wph, source, install_date, is_enabled, created_at, created_by, updated_at, updated_by)
values
    (1, 'E-ETCH-01', '干法刻蚀机 01', 'DRY_ETCH', 'AUTO', 'ONLINE', 'ISO4', 0, 'IDL', 'eng_lead',
     'LAM', 'Kiyo', 'SN-ETCH-0001', 'INSTALLED', 'F1', 'ETCH-B01-M01', 60.00, 'MANUAL', '2025-01-15',
     1, now(), 'system', now(), 'system'),
    (2, 'E-LITHO-01', '光刻扫描机 01', 'SCANNER', 'AUTO', 'ONLINE', 'ISO3', 1, 'IDL', 'eng_lead',
     'ASML', 'NXE', 'SN-LITHO-0001', 'INSTALLED', 'F1', 'PHOTO-B01-M01', 120.00, 'MANUAL', '2025-02-20',
     1, now(), 'system', now(), 'system');

insert into cim_process_resource
    (id, resource_code, equipment_code, resource_type, resource_name, run_mode, parallel_group, stage_seq, sort_no, is_enabled, created_at, created_by, updated_at, updated_by)
values
    (1, 'CH-A', 'E-ETCH-01', 'CHAMBER', '刻蚀腔 A', 'PARALLEL', 'G1', null, 10, 1, now(), 'system', now(), 'system'),
    (2, 'CH-B', 'E-ETCH-01', 'CHAMBER', '刻蚀腔 B', 'PARALLEL', 'G1', null, 20, 1, now(), 'system', now(), 'system'),
    (3, 'CH-A', 'E-LITHO-01', 'CHAMBER', '曝光台 A', 'SERIAL',  null, 1,   10, 1, now(), 'system', now(), 'system');

insert into cim_port
    (id, port_code, equipment_code, port_no, port_type, control_model, e84_enabled, handoff_mode, carrier_type, is_enabled, created_at, created_by, updated_at, updated_by)
values
    (1, 'P1', 'E-ETCH-01', 1, 'LOAD',  'E84', 1, 'PULL', 'FOUP', 1, now(), 'system', now(), 'system'),
    (2, 'P2', 'E-ETCH-01', 2, 'UNLOAD', 'E84', 1, 'PUSH', 'FOUP', 1, now(), 'system', now(), 'system');

insert into cim_transport_equipment_category
    (id, category_code, category_name, is_enabled, created_at, created_by, updated_at, updated_by)
values
    (1, 'OHT',     '空中走行式搬送车', 1, now(), 'system', now(), 'system'),
    (2, 'STOCKER', '存储式缓冲站',     1, now(), 'system', now(), 'system'),
    (3, 'OHB',     '高空缓冲站',       1, now(), 'system', now(), 'system');

insert into cim_transport_equipment
    (id, transport_equipment_code, transport_equipment_name, category_code, fab_code, module_code,
     initial_state_code, owner, supplier_name, model_no, serial_no, purge_control, near_tool_control,
     max_capacity, eap_host, asset_status, source, is_enabled, created_at, created_by, updated_at, updated_by)
values
    (1, 'OHT-01', '空中搬送车 01', 'OHT', 'F1', null, 'IDL', 'amhs_lead', 'DAIFUKU', 'OHV', 'SN-OHT-0001', 0, 0, null, 'EAP-OHT', 'INSTALLED', 'MANUAL', 1, now(), 'system', now(), 'system'),
    (2, 'STK-01', '光刻区存储站 01', 'STOCKER', 'F1', 'PHOTO-B01', 'IDL', 'amhs_lead', 'MURATEC', 'STK', 'SN-STK-0001', 1, 1, 25, 'EAP-STK', 'INSTALLED', 'MANUAL', 1, now(), 'system', now(), 'system');

insert into cim_equipment_transport
    (id, equipment_code, transport_equipment_code, near_tool_priority, service_role, created_at, created_by, updated_at, updated_by)
values
    (1, 'E-ETCH-01', 'STK-01', 10, 'PRIMARY', now(), 'system', now(), 'system'),
    (2, 'E-LITHO-01', 'STK-01', 10, 'PRIMARY', now(), 'system', now(), 'system'),
    (3, 'E-LITHO-01', 'OHT-01', 20, 'SECONDARY', now(), 'system', now(), 'system');

insert into cim_machine_group
    (id, group_code, group_name, group_kind, is_enabled, created_at, created_by, updated_at, updated_by)
values
    (1, 'GRP_ETCH_F1', 'F1 刻蚀调度组', 'DISPATCH', 1, now(), 'system', now(), 'system');

insert into cim_machine_group_member
    (id, group_code, equipment_code, priority, member_state, created_at, created_by, updated_at, updated_by)
values
    (1, 'GRP_ETCH_F1', 'E-ETCH-01', 10, 'ACTIVE', now(), 'system', now(), 'system');

insert into cim_equipment_capability
    (id, equipment_code, capability_code, qual_state, qual_date, expire_date, created_at, created_by, updated_at, updated_by)
values
    (1, 'E-ETCH-01', 'ETCH_POLY', 'QUALIFIED', '2025-03-01', null, now(), 'system', now(), 'system'),
    (2, 'E-LITHO-01', 'LITHO_EXPOSE_193', 'QUALIFIED', '2025-03-05', null, now(), 'system', now(), 'system');

insert into cim_equipment_control_setting
    (id, equipment_code, setting_key, setting_value, description, created_at, created_by, updated_at, updated_by)
values
    (1, 'E-ETCH-01', 'RECIPE_BODY_MANAGE',         'Y', '配方体管理',        now(), 'system', now(), 'system'),
    (2, 'E-ETCH-01', 'CARRIER_CHANGE_REQUIRED',    'Y', '载具更换需确认',    now(), 'system', now(), 'system'),
    (3, 'E-ETCH-01', 'EAP_PRECHECK_REQUIRED',      'Y', 'EAP 预检必选',      now(), 'system', now(), 'system'),
    (4, 'E-ETCH-01', 'WAFER_ID_READABLE',          'Y', '可读晶圆 ID',       now(), 'system', now(), 'system'),
    (5, 'E-ETCH-01', 'CARRIER_ID_READABLE',        'Y', '可读载具 ID',       now(), 'system', now(), 'system'),
    (6, 'E-ETCH-01', 'AUTO_SETUP',                 'Y', '自动 setup',        now(), 'system', now(), 'system'),
    (7, 'E-ETCH-01', 'PR_CONTROL',                 'N', 'PR 控制',           now(), 'system', now(), 'system'),
    (8, 'E-ETCH-01', 'RESIST_FLAG',                'N', '光刻胶标志',        now(), 'system', now(), 'system'),
    (9, 'E-ETCH-01', 'EARLY_EMPTY_CARRIER_OUT',    'N', '空载具提前退出',    now(), 'system', now(), 'system'),
    (10,'E-ETCH-01', 'TAKE_IN_TRANSFER',           'Y', '搬入转移',          now(), 'system', now(), 'system'),
    (11,'E-ETCH-01', 'EQUIPMENT_TO_EQUIPMENT_TRANSFER', 'N', '设备间转移',    now(), 'system', now(), 'system'),
    (12,'E-ETCH-01', 'PROCESS_JOB_LEVEL_CONTROL',  'Y', 'Process Job 级控制', now(), 'system', now(), 'system');

-- 状态字典（统一三维框架：entity_type × state_model × state_code；E10 单树 + E87 多并发模型）
insert into cim_state_def
    (id, entity_type, state_model, state_code, state_name, parent_code, state_level,
     state_category, availability, is_productive, is_initial_allowed, applicable_scope,
     is_enabled, created_at, created_by, updated_at, updated_by)
values
    -- ===== EQUIPMENT / MAIN（SEMI E10 单状态机，6 主状态 + 子状态层级树）=====
    -- 主状态（level 1）
    (1,  'EQUIPMENT','MAIN','PRD','Productive 运行',          null,1,'PRD','UP',  1,0,'ALL', 1, now(),'system',now(),'system'),
    (2,  'EQUIPMENT','MAIN','SBY','Standby 待机',             null,1,'IDL','UP',  0,1,'ALL', 1, now(),'system',now(),'system'),
    (3,  'EQUIPMENT','MAIN','ENG','Engineering 工程设定',      null,1,'IDL','UP',  0,0,'ALL', 1, now(),'system',now(),'system'),
    (4,  'EQUIPMENT','MAIN','IDL','Idle 空闲',                null,1,'IDL','UP',  0,1,'ALL', 1, now(),'system',now(),'system'),
    (5,  'EQUIPMENT','MAIN','SDT','Scheduled Down 计划停机',  null,1,'SDT','DOWN',0,0,'ALL', 1, now(),'system',now(),'system'),
    (6,  'EQUIPMENT','MAIN','UDT','Unscheduled Down 非计划停机',null,1,'UDT','DOWN',0,0,'ALL',1, now(),'system',now(),'system'),
    (7,  'EQUIPMENT','MAIN','NST','Non-Scheduled Time 非计划时段',null,1,'NST','DOWN',0,0,'ALL',1, now(),'system',now(),'system'),
    -- 子状态（level 2，体现 E10 层级树；parent_code 指向主状态）
    (8,  'EQUIPMENT','MAIN','PM','Preventive Maint 预防性维护','SDT',2,'SDT','DOWN',0,0,'ALL',1, now(),'system',now(),'system'),
    (9,  'EQUIPMENT','MAIN','SETUP','Setup 设定',            'SDT',2,'SDT','DOWN',0,0,'ALL',1, now(),'system',now(),'system'),
    (10, 'EQUIPMENT','MAIN','REPAIR','Repair 维修',          'UDT',2,'UDT','DOWN',0,0,'ALL',1, now(),'system',now(),'system'),
    -- ===== CARRIER / FOUP（SEMI E87，4 个并发状态模型，可同时各处于一态）=====
    -- ID 模型：载具 ID 识别
    (11, 'CARRIER','ID','ID_NOT_READ','ID 未读取',  null,1,null,null,null,null,'ALL',1, now(),'system',now(),'system'),
    (12, 'CARRIER','ID','WAIT_HOST','等待主机确认',  null,1,null,null,null,null,'ALL',1, now(),'system',now(),'system'),
    (13, 'CARRIER','ID','ID_OK','ID 校验通过',       null,1,null,null,null,null,'ALL',1, now(),'system',now(),'system'),
    (14, 'CARRIER','ID','ID_FAIL','ID 校验失败',     null,1,null,null,null,null,'ALL',1, now(),'system',now(),'system'),
    -- SLOT_MAP 模型：槽位图
    (15, 'CARRIER','SLOTMAP','SM_NOT_READ','槽位图未读取',  null,1,null,null,null,null,'ALL',1, now(),'system',now(),'system'),
    (16, 'CARRIER','SLOTMAP','SM_OK','槽位图校验通过',      null,1,null,null,null,null,'ALL',1, now(),'system',now(),'system'),
    (17, 'CARRIER','SLOTMAP','SM_FAIL','槽位图校验失败',    null,1,null,null,null,null,'ALL',1, now(),'system',now(),'system'),
    -- ACCESS 模型：载具访问
    (18, 'CARRIER','ACCESS','NOT_ACCESSED','未访问',   null,1,null,null,null,null,'ALL',1, now(),'system',now(),'system'),
    (19, 'CARRIER','ACCESS','IN_ACCESS','访问中',     null,1,null,null,null,null,'ALL',1, now(),'system',now(),'system'),
    (20, 'CARRIER','ACCESS','CARRIER_COMPLETE','载具完成',null,1,null,null,null,null,'ALL',1, now(),'system',now(),'system'),
    -- CRTU 模型：卸载预测
    (21, 'CARRIER','CRTU','WAIT_CARRIER','等待载具',  null,1,null,null,null,null,'ALL',1, now(),'system',now(),'system'),
    (22, 'CARRIER','CRTU','PREDICTED','预测可卸',    null,1,null,null,null,null,'ALL',1, now(),'system',now(),'system'),
    (23, 'CARRIER','CRTU','ABORTED','预测中止',      null,1,null,null,null,null,'ALL',1, now(),'system',now(),'system'),
    (24, 'CARRIER','CRTU','CMPLT','预测完成',        null,1,null,null,null,null,'ALL',1, now(),'system',now(),'system'),
    -- ===== PORT / Load Port（SEMI E87，4 个并发状态模型）=====
    -- TRANSFER 模型：交接
    (25, 'PORT','TRANSFER','OOS','Out Of Service',          null,1,null,null,null,null,'ALL',1, now(),'system',now(),'system'),
    (26, 'PORT','TRANSFER','IN_SVC','In Service',           null,1,null,null,null,null,'ALL',1, now(),'system',now(),'system'),
    (27, 'PORT','TRANSFER','READY_LOAD','Ready To Load',    null,1,null,null,null,null,'ALL',1, now(),'system',now(),'system'),
    (28, 'PORT','TRANSFER','READY_UNLOAD','Ready To Unload',null,1,null,null,null,null,'ALL',1, now(),'system',now(),'system'),
    (29, 'PORT','TRANSFER','XFER_BLOCKED','Transfer Blocked',null,1,null,null,null,null,'ALL',1, now(),'system',now(),'system'),
    -- ACCESS_MODE 模型
    (30, 'PORT','ACCESS_MODE','MANUAL','Manual 手动',  null,1,null,null,null,null,'ALL',1, now(),'system',now(),'system'),
    (31, 'PORT','ACCESS_MODE','AUTO','Auto 自动',     null,1,null,null,null,null,'ALL',1, now(),'system',now(),'system'),
    -- RESERVATION 模型
    (32, 'PORT','RESERVATION','NOT_RESERVED','未预约', null,1,null,null,null,null,'ALL',1, now(),'system',now(),'system'),
    (33, 'PORT','RESERVATION','RESERVED','已预约',    null,1,null,null,null,null,'ALL',1, now(),'system',now(),'system'),
    -- ASSOCIATION 模型
    (34, 'PORT','ASSOCIATION','NOT_ASSOC','未关联载具', null,1,null,null,null,null,'ALL',1, now(),'system',now(),'system'),
    (35, 'PORT','ASSOCIATION','ASSOC','已关联载具',    null,1,null,null,null,null,'ALL',1, now(),'system',now(),'system');

-- 状态转换矩阵（白名单：仅登记允许转换；运行时按 (entity_type,state_model) 过滤校验）
insert into cim_state_transition
    (id, entity_type, state_model, from_state, to_state, event_code, description, created_at, created_by, updated_at, updated_by)
values
    -- EQUIPMENT / MAIN（E10 主状态流转）
    (1,  'EQUIPMENT','MAIN','IDL','PRD','START','空闲→运行',now(),'system',now(),'system'),
    (2,  'EQUIPMENT','MAIN','PRD','IDL','STOP','运行→空闲',now(),'system',now(),'system'),
    (3,  'EQUIPMENT','MAIN','IDL','SBY','SLEEP','空闲→待机',now(),'system',now(),'system'),
    (4,  'EQUIPMENT','MAIN','SBY','IDL','WAKE','待机→空闲',now(),'system',now(),'system'),
    (5,  'EQUIPMENT','MAIN','IDL','ENG','SETUP','空闲→工程设定',now(),'system',now(),'system'),
    (6,  'EQUIPMENT','MAIN','ENG','IDL','SETUP_DONE','工程设定→空闲',now(),'system',now(),'system'),
    (7,  'EQUIPMENT','MAIN','ENG','PRD','SETUP_RUN','工程设定→运行',now(),'system',now(),'system'),
    (8,  'EQUIPMENT','MAIN','SBY','PRD','START','待机→运行',now(),'system',now(),'system'),
    (9,  'EQUIPMENT','MAIN','IDL','SDT','SCHED_DOWN','空闲→计划停机',now(),'system',now(),'system'),
    (10, 'EQUIPMENT','MAIN','SDT','IDL','SCHED_UP','计划停机→空闲',now(),'system',now(),'system'),
    (11, 'EQUIPMENT','MAIN','IDL','UDT','FAULT','空闲→非计划停机',now(),'system',now(),'system'),
    (12, 'EQUIPMENT','MAIN','PRD','UDT','FAULT','运行→非计划停机',now(),'system',now(),'system'),
    (13, 'EQUIPMENT','MAIN','ENG','UDT','FAULT','工程设定→非计划停机',now(),'system',now(),'system'),
    (14, 'EQUIPMENT','MAIN','UDT','IDL','RECOVER','非计划停机→空闲',now(),'system',now(),'system'),
    (15, 'EQUIPMENT','MAIN','UDT','PM','RECOVER_MAINT','非计划停机→维护',now(),'system',now(),'system'),
    (16, 'EQUIPMENT','MAIN','IDL','PM','MAINT_START','空闲→维护',now(),'system',now(),'system'),
    (17, 'EQUIPMENT','MAIN','PM','IDL','MAINT_END','维护→空闲',now(),'system',now(),'system'),
    (18, 'EQUIPMENT','MAIN','IDL','NST','NST_START','空闲→非计划时段',now(),'system',now(),'system'),
    (19, 'EQUIPMENT','MAIN','NST','IDL','NST_END','非计划时段→空闲',now(),'system',now(),'system'),
    (20, 'EQUIPMENT','MAIN','SDT','PM','MAINT_START','计划停机→维护',now(),'system',now(),'system'),
    -- 子状态层级流转（E10 主状态↔子状态）
    (21, 'EQUIPMENT','MAIN','SDT','SETUP','ENTER_SETUP','计划停机→设定',now(),'system',now(),'system'),
    (22, 'EQUIPMENT','MAIN','SETUP','SDT','EXIT_SETUP','设定→计划停机',now(),'system',now(),'system'),
    (23, 'EQUIPMENT','MAIN','UDT','REPAIR','ENTER_REPAIR','非计划停机→维修',now(),'system',now(),'system'),
    (24, 'EQUIPMENT','MAIN','REPAIR','UDT','EXIT_REPAIR','维修→非计划停机',now(),'system',now(),'system'),
    -- CARRIER / ID（E87 ID 模型流转）
    (25, 'CARRIER','ID','ID_NOT_READ','WAIT_HOST','CARRIER_PRESENT','载具到位→等待确认',now(),'system',now(),'system'),
    (26, 'CARRIER','ID','WAIT_HOST','ID_OK','ID_VERIFIED','ID 校验通过',now(),'system',now(),'system'),
    (27, 'CARRIER','ID','WAIT_HOST','ID_FAIL','ID_FAILED','ID 校验失败',now(),'system',now(),'system'),
    (28, 'CARRIER','ID','ID_FAIL','ID_NOT_READ','RETRY','重试读取',now(),'system',now(),'system'),
    -- CARRIER / ACCESS（E87 ACCESS 模型流转）
    (29, 'CARRIER','ACCESS','NOT_ACCESSED','IN_ACCESS','ACCESS_START','开始访问',now(),'system',now(),'system'),
    (30, 'CARRIER','ACCESS','IN_ACCESS','CARRIER_COMPLETE','ACC_COMPLETE','访问完成',now(),'system',now(),'system'),
    (31, 'CARRIER','ACCESS','CARRIER_COMPLETE','NOT_ACCESSED','REMOVED','载具移出',now(),'system',now(),'system'),
    -- PORT / TRANSFER（E87 Load Port 交接流转）
    (32, 'PORT','TRANSFER','OOS','IN_SVC','PORT_ONLINE','端口上线',now(),'system',now(),'system'),
    (33, 'PORT','TRANSFER','IN_SVC','READY_LOAD','LOAD_REQ','请求装载',now(),'system',now(),'system'),
    (34, 'PORT','TRANSFER','READY_LOAD','IN_SVC','LOAD_DONE','装载完成',now(),'system',now(),'system'),
    (35, 'PORT','TRANSFER','IN_SVC','READY_UNLOAD','UNLOAD_REQ','请求卸载',now(),'system',now(),'system'),
    (36, 'PORT','TRANSFER','READY_UNLOAD','IN_SVC','UNLOAD_DONE','卸载完成',now(),'system',now(),'system'),
    (37, 'PORT','TRANSFER','IN_SVC','XFER_BLOCKED','BLOCK','交接阻塞',now(),'system',now(),'system'),
    (38, 'PORT','TRANSFER','XFER_BLOCKED','IN_SVC','UNBLOCK','解除阻塞',now(),'system',now(),'system'),
    -- PORT / RESERVATION（E87 预约流转）
    (39, 'PORT','RESERVATION','NOT_RESERVED','RESERVED','RESERVE','预约端口',now(),'system',now(),'system'),
    (40, 'PORT','RESERVATION','RESERVED','NOT_RESERVED','RELEASE','释放端口',now(),'system',now(),'system');
