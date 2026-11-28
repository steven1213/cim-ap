package com.cim.mds.server.console;

import java.util.List;

import com.cim.system.dict.SysDict;
import com.cim.system.dict.SysDictItem;
import com.cim.system.dict.SysDictItemRepository;
import com.cim.system.dict.SysDictRepository;
import com.cim.system.support.EnableStatus;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MDS 数据字典种子服务：把 MDS 业务字典（产品分组 / 晶圆类型）幂等写入平台
 * {@code sys_dict}/{@code sys_dict_item}。
 *
 * <p><b>为什么用 Java 而非 Flyway 种子 SQL</b>：sys_dict 表由 cim-system 的迁移
 * （V1000，独立 location）创建，MDS 自己的迁移（V1–999）排在它<b>之前</b>执行，
 * SQL 种子引用 sys_dict 会报「表不存在」。Java 种子随 ApplicationRunner 在全部
 * 迁移之后运行，天然规避时序问题。</p>
 *
 * <p><b>幂等口径：已存在即跳过</b>——字典按 code 查重、明细按 (dictId, itemKey) 查重，
 * 管理端在线维护过的字典不被重启冲掉。同时把 V6 产品种子里的中文晶圆类型
 * 对齐为字典 itemKey（一次性数据迁移，幂等）。</p>
 */
@Slf4j
@Service
public class MdsDictSeedService {

    /** 字典定义（itemKey=落库值，itemValue=显示文案；双语待平台 useDict 支持 i18n_code 后再补）。 */
    public record DictDef(String code, String name, List<ItemDef> items) {
    }

    public record ItemDef(String itemKey, String itemValue, int sortNo) {
    }

    /** MDS 业务字典目录。 */
    static final List<DictDef> DICTS = List.of(
            new DictDef("mds.substrate_type", "衬底材料", List.of(
                    new ItemDef("P_SUB", "P 型衬底", 10),
                    new ItemDef("N_SUB", "N 型衬底", 20),
                    new ItemDef("EPI", "外延片", 30),
                    new ItemDef("SOI", "SOI 片", 40),
                    new ItemDef("SAPPHIRE", "蓝宝石", 50),
                    new ItemDef("OTHER", "其他", 90))),
            // 晶圆尺寸（V35 多尺寸厂口径：英寸数字串，产品/载具/光罩/能力资格同词表）
            new DictDef("mds.wafer_size", "晶圆尺寸", List.of(
                    new ItemDef("6", "6 英寸", 10),
                    new ItemDef("8", "8 英寸", 20),
                    new ItemDef("12", "12 英寸", 30))),
            new DictDef("mds.product_group", "产品分组", List.of(
                    new ItemDef("CIS", "CIS（图像传感器）", 10),
                    new ItemDef("PMIC", "PMIC（电源管理）", 20),
                    new ItemDef("LOGIC", "逻辑", 30),
                    new ItemDef("MEMS", "MEMS（传感器）", 40),
                    new ItemDef("NPW", "陪片", 50),
                    new ItemDef("OTHER", "其他", 90))),
            // 设备大类：行业口径定稿 8 类（SEMI SPE / SEAJ 设备索引 + MES 实操校准：
            // UTILITY+环境监控合并为 FACILITY；TOOL 行业属 Durable/Tooling 域，本期台账占位）
            new DictDef("mds.equip_kind", "设备大类", List.of(
                    new ItemDef("PROCESS", "工艺设备（核心机台）", 10),
                    new ItemDef("METRO", "量测检测", 20),
                    new ItemDef("TEST", "测试（中测/FT）", 30),
                    new ItemDef("ASSY", "封装", 40),
                    new ItemDef("MATERIAL", "物料搬运/仓储", 50),
                    new ItemDef("FACILITY", "厂务/公用工程", 60),
                    new ItemDef("TOOL", "工装/治具", 70),
                    new ItemDef("OTHER", "其他", 90))),
            // 设备细类：大类的二级分类（值域与 EquipmentClassAdminService.KIND_CLASSES 守门一致；
            // MATERIAL 组含本项目 AMHS 核心子系统 OHS/OHB/NTB）
            new DictDef("mds.equip_class", "设备细类", List.of(
                    new ItemDef("ETCH", "刻蚀机（工艺）", 10),
                    new ItemDef("DEP", "薄膜沉积 CVD/PVD/ALD/EPI（工艺）", 20),
                    new ItemDef("IMP", "离子注入（工艺）", 30),
                    new ItemDef("LITHO", "光刻 Scanner/Coater/Developer（工艺）", 40),
                    new ItemDef("CLEAN", "清洗机（工艺）", 50),
                    new ItemDef("THERMAL", "热工艺 氧化/扩散/退火（工艺）", 60),
                    new ItemDef("CMP", "化学机械研磨（工艺）", 70),
                    new ItemDef("PLATING", "电镀（工艺）", 80),
                    new ItemDef("RTP", "快速热处理（工艺）", 90),
                    new ItemDef("CDSEM", "关键尺寸量测（量测）", 110),
                    new ItemDef("AFM", "原子力显微镜（量测）", 120),
                    new ItemDef("EL", "椭偏（量测）", 130),
                    new ItemDef("WAT", "晶圆电性测试（量测）", 140),
                    new ItemDef("INSPECT", "光学缺陷检测（检测）", 150),
                    new ItemDef("REVIEW", "缺陷复检（检测）", 160),
                    new ItemDef("PARTICLE", "粒子检测（检测）", 170),
                    new ItemDef("PROBER", "探针台（中测）", 210),
                    new ItemDef("TESTER", "测试机 ATE（测试）", 220),
                    new ItemDef("HANDLER", "分选机（FT）", 230),
                    new ItemDef("BURNIN", "老化测试（测试）", 240),
                    new ItemDef("GRIND", "减薄（封装）", 310),
                    new ItemDef("SAW", "划片（封装）", 320),
                    new ItemDef("DA", "固晶（封装）", 330),
                    new ItemDef("WB", "焊线（封装）", 340),
                    new ItemDef("MOLD", "塑封（封装）", 350),
                    new ItemDef("MARK", "激光打标（封装）", 360),
                    new ItemDef("SING", "切单（封装）", 370),
                    new ItemDef("STOCKER", "晶圆库（搬运）", 410),
                    new ItemDef("OHT", "空中轨道小车（搬运）", 420),
                    new ItemDef("OHS", "OHS 轨道（搬运）", 430),
                    new ItemDef("OHB", "OHB 缓存（搬运）", 440),
                    new ItemDef("NTB", "NTB 缓存（搬运）", 450),
                    new ItemDef("AGV", "自动导引车（搬运）", 460),
                    new ItemDef("ROBOT", "机械手（搬运）", 470),
                    new ItemDef("LOADER", "上下料机（搬运）", 480),
                    new ItemDef("CHILLER", "冷水机（厂务）", 510),
                    new ItemDef("PUMP", "真空泵组（厂务）", 520),
                    new ItemDef("GASBOX", "气体柜（厂务）", 530),
                    new ItemDef("WETSCRUBBER", "洗涤塔（厂务）", 540),
                    new ItemDef("FAN", "空调 FFU（厂务）", 550),
                    new ItemDef("ENV", "环境采集 温湿度/压差（监控）", 560),
                    new ItemDef("POWER", "电力监控（监控）", 570),
                    new ItemDef("GASMON", "气体监测（监控）", 580),
                    new ItemDef("CHUCK", "卡盘（治具）", 610),
                    new ItemDef("JIG", "载具治具（治具）", 620),
                    new ItemDef("FIXTURE", "夹具（治具）", 630),
                    new ItemDef("UNCLASS", "未分类（其他）", 910))),
            new DictDef("mds.equip_state", "设备状态", List.of(
                    new ItemDef("PRD", "生产（Productive）", 10),
                    new ItemDef("SBY", "待机（Standby）", 20),
                    new ItemDef("ENG", "工程（Engineering）", 30),
                    new ItemDef("SDT", "计划停机（Scheduled Down）", 40),
                    new ItemDef("UDT", "非计划停机（Unscheduled Down）", 50),
                    new ItemDef("NST", "未排程（Non-Scheduled）", 60))),
            // 通信控制模式（v2，SEMI E30 口径）：主数据声明——EAP 能否下发指令的契约态
            new DictDef("mds.equip_control_mode", "通信控制模式", List.of(
                    new ItemDef("ONLINE_REMOTE", "在线远程（EAP 可下发）", 10),
                    new ItemDef("ONLINE_LOCAL", "在线本地（EAP 监视，操作员控）", 20),
                    new ItemDef("LOCAL", "本地（未联网）", 30),
                    new ItemDef("OFFLINE", "离线", 40))),
            // 生产/工程模式（v2）：工程机台收试做批，派工侧排除
            new DictDef("mds.equip_production_mode", "生产模式", List.of(
                    new ItemDef("PRODUCTION", "生产", 10),
                    new ItemDef("ENGINEERING", "工程（试做）", 20))),
            new DictDef("mds.unit_state", "子单元状态", List.of(
                    new ItemDef("READY_TO_LOAD", "可放载具（E87）", 10),
                    new ItemDef("READY_TO_UNLOAD", "可取载具（E87）", 20),
                    new ItemDef("TRANSFER_BLOCKED", "交接阻塞（E87）", 30),
                    new ItemDef("OUT_OF_SERVICE", "停用（E87）", 40),
                    new ItemDef("IDLE", "空闲（E116）", 50),
                    new ItemDef("EXECUTING", "加工中（E157）", 60),
                    new ItemDef("BUSY", "忙碌（E116）", 70),
                    new ItemDef("DOWN", "故障", 80),
                    new ItemDef("PM", "保养中", 90))),
            new DictDef("mds.stocker_bank_type", "存储区类型", List.of(
                    new ItemDef("RAW", "原料库", 10),
                    new ItemDef("WIP", "WIP 在制品库", 20),
                    new ItemDef("FG", "成品库（Die Bank）", 30),
                    new ItemDef("SCRAP", "不良品库", 40),
                    new ItemDef("CONTROL", "控片库", 50),
                    new ItemDef("RECYCLE", "回收库", 60),
                    new ItemDef("GENERAL", "普通暂存", 90))),
            // 原因码归属部门（V16 → V36 改版）：部门真源已迁移到 IAM 组织架构
            // （nodeType=DEPT），静态字典 mds.owning_dept 废弃删除（不留双轨）；
            // 业务取数走 DirectoryClient.orgOptions()。
            // 载具类型（V31，SEMI E87 Carrier 口径）：AMHS 全链物理载体分类
            new DictDef("mds.carrier_type", "载具类型", List.of(
                    new ItemDef("CASSETTE", "花篮（Cassette）", 10),
                    new ItemDef("FOUP", "FOUP（300mm 真空载具）", 20),
                    new ItemDef("RETICLE_BOX", "光罩盒", 30),
                    new ItemDef("WAFER_BOX", "晶圆盒", 40),
                    new ItemDef("OTHER", "其他", 90))),
            // 光罩版尺寸（V35：曝光机兼容性判断依据；6 寸线常用 5/6 寸版，12 寸线 6/9 寸版）
            new DictDef("mds.reticle_plate_size", "光罩版尺寸", List.of(
                    new ItemDef("5", "5 英寸版", 10),
                    new ItemDef("6", "6 英寸版", 20),
                    new ItemDef("9", "9 英寸版", 30))),
            // 载具台账状态（V31）：运行时状态机归 MCS，主数据只存台账级状态
            new DictDef("mds.carrier_status", "载具状态", List.of(
                    new ItemDef("IN_STOCK", "空闲在库", 10),
                    new ItemDef("IN_USE", "使用中", 20),
                    new ItemDef("IN_CLEANING", "清洗中", 30),
                    new ItemDef("MAINTENANCE", "维护中", 40),
                    new ItemDef("SCRAPPED", "报废", 90))),
            // 光罩状态（V32）：寿命双轨（次数/有效期），判定归运行域
            new DictDef("mds.reticle_status", "光罩状态", List.of(
                    new ItemDef("IN_STOCK", "在库", 10),
                    new ItemDef("ACTIVE", "在用", 20),
                    new ItemDef("DAMAGED", "损伤", 30),
                    new ItemDef("EXPIRED", "过期", 40),
                    new ItemDef("SCRAPPED", "报废", 90))),
            // 抽样方案（V33，检验标准头）
            new DictDef("mds.sampling_type", "抽样方案", List.of(
                    new ItemDef("FULL", "全检", 10),
                    new ItemDef("SAMPLING", "抽检", 20),
                    new ItemDef("FIRST_ARTICLE", "首件检验", 30),
                    new ItemDef("PERIODIC", "周期抽检", 40))),
            // AQL 允收水准（V33，GB/T 2828.1 / ANSI/ASQ Z1.4 特殊检验水准常用值）
            new DictDef("mds.aql_level", "AQL 允收水准", List.of(
                    new ItemDef("0.065", "AQL 0.065", 10),
                    new ItemDef("0.10", "AQL 0.10", 20),
                    new ItemDef("0.15", "AQL 0.15", 30),
                    new ItemDef("0.25", "AQL 0.25", 40),
                    new ItemDef("0.40", "AQL 0.40", 50),
                    new ItemDef("0.65", "AQL 0.65", 60),
                    new ItemDef("1.0", "AQL 1.0", 70),
                    new ItemDef("1.5", "AQL 1.5", 80),
                    new ItemDef("2.5", "AQL 2.5", 90))),
            // 缺陷等级（V33，检验项明细）
            new DictDef("mds.defect_level", "缺陷等级", List.of(
                    new ItemDef("CRITICAL", "致命缺陷（CRITICAL）", 10),
                    new ItemDef("MAJOR", "主要缺陷（MAJOR）", 20),
                    new ItemDef("MINOR", "次要缺陷（MINOR）", 30))),
            // 耗材类别（V34/V37：TOOL 移除——耐用工具归 equipment 域）
            new DictDef("mds.consumable_type", "耗材类别", List.of(
                    new ItemDef("CHEMICAL", "化学品", 10),
                    new ItemDef("SLURRY", "研磨液", 20),
                    new ItemDef("GAS", "特气", 30),
                    new ItemDef("PART", "备件耗材", 40),
                    new ItemDef("TARGET", "靶材", 50),
                    new ItemDef("PACKAGING", "封装材料", 60),
                    new ItemDef("OTHER", "其他", 90))),
            // 计量单位（V34，耗材）
            new DictDef("mds.measure_unit", "计量单位", List.of(
                    new ItemDef("EA", "个（EA）", 10),
                    new ItemDef("BOX", "盒（BOX）", 20),
                    new ItemDef("SET", "套（SET）", 30),
                    new ItemDef("PACK", "包（PACK）", 40),
                    new ItemDef("BOTTLE", "瓶（BOTTLE）", 50),
                    new ItemDef("CAN", "罐（CAN）", 60),
                    new ItemDef("ROLL", "卷（ROLL）", 70))),
            // 耗材寿命管理方式（V34）：到期判定与预警触发归运行域
            new DictDef("mds.consumable_life_type", "寿命管理方式", List.of(
                    new ItemDef("CYCLE", "按使用次数", 10),
                    new ItemDef("DAYS", "按天数", 20),
                    new ItemDef("QTY", "按产量", 30),
                    new ItemDef("NONE", "无寿命管理", 90))),
            // BOM 消耗类型（V38）：FIXED 固定用量 / VARIABLE 变量消耗（参数×单位用量，公式计算归运行时）
            new DictDef("mds.cons_consume_type", "消耗类型", List.of(
                    new ItemDef("FIXED", "固定用量", 10),
                    new ItemDef("VARIABLE", "变量消耗", 20))),
            // BOM 用量基准（V38）：标准用量配合基准解释
            new DictDef("mds.cons_basis", "用量基准", List.of(
                    new ItemDef("PER_WAFER", "每片（Per Wafer）", 10),
                    new ItemDef("PER_LOT", "每批次（Per Lot）", 20),
                    new ItemDef("PER_RUN", "每次运行（Per Run）", 30))),
            // BOM 失效策略标记（V38）：MDS 只存策略标记，FEFO/FIFO 拣配执行归运行时
            new DictDef("mds.cons_expiry_policy", "失效策略", List.of(
                    new ItemDef("FEFO", "先到期先出（FEFO）", 10),
                    new ItemDef("FIFO", "先进先出（FIFO）", 20))),
            // 动态查询/视图引擎（V39）
            new DictDef("mds.dq_param_type", "查询参数类型", List.of(
                    new ItemDef("STRING", "字符串", 10),
                    new ItemDef("NUMBER", "数字", 20),
                    new ItemDef("DATE", "日期", 30),
                    new ItemDef("DICT", "字典下拉", 40))),
            new DictDef("mds.dq_format_type", "列格式化类型", List.of(
                    new ItemDef("NONE", "原样", 10),
                    new ItemDef("DATE", "日期", 20),
                    new ItemDef("DATETIME", "日期时间", 30),
                    new ItemDef("DICT", "字典翻译", 40),
                    new ItemDef("NUMBER", "数字", 50))));

    private final SysDictRepository dictRepository;
    private final SysDictItemRepository itemRepository;
    private final com.cim.mds.server.site.CimProductRepository productRepository;

    public MdsDictSeedService(SysDictRepository dictRepository,
                              SysDictItemRepository itemRepository,
                              com.cim.mds.server.site.CimProductRepository productRepository) {
        this.dictRepository = dictRepository;
        this.itemRepository = itemRepository;
        this.productRepository = productRepository;
    }

    /** 种子结果（供日志）。 */
    public record SeedReport(int dicts, int items, int alignedProducts) {
    }

    /** 执行种子（幂等，全在一个事务内）。 */
    @Transactional
    public SeedReport seed() {
        int dictCount = 0;
        int itemCount = 0;
        for (DictDef def : DICTS) {
            SysDict dict = dictRepository.findFirstByCodeOrderByCreateTimeAsc(def.code()).orElse(null);
            if (dict == null) {
                dict = new SysDict();
                dict.setCode(def.code());
                dict.setName(def.name());
                dict.setStatus(EnableStatus.ENABLED);
                // MDS 种子字典是系统级：管理页不可删除（明细同理）
                dict.setIsSystem(true);
                dict = dictRepository.save(dict);
                dictCount++;
            } else if (!Boolean.TRUE.equals(dict.getIsSystem())) {
                // 幂等回填：早于系统级标记建出的存量字典补标（种子拥有权口径）
                dict.setIsSystem(true);
                dictRepository.save(dict);
            }
            for (SysDictItem item : itemRepository.findByDictIdOrderBySortNoAsc(dict.getId())) {
                if (!Boolean.TRUE.equals(item.getIsSystem())) {
                    item.setIsSystem(true);
                    itemRepository.save(item);
                }
            }
            List<String> existingKeys = itemRepository.findByDictIdOrderBySortNoAsc(dict.getId()).stream()
                    .map(SysDictItem::getItemKey)
                    .toList();
            for (ItemDef item : def.items()) {
                if (existingKeys.contains(item.itemKey())) {
                    continue;
                }
                SysDictItem entity = new SysDictItem();
                entity.setDictId(dict.getId());
                entity.setItemKey(item.itemKey());
                entity.setItemValue(item.itemValue());
                entity.setSortNo(item.sortNo());
                entity.setStatus(EnableStatus.ENABLED);
                entity.setIsSystem(true);
                itemRepository.save(entity);
                itemCount++;
            }
        }
        int aligned = alignProductSeedWaferTypes();
        int retired = retireConsumableToolItem();
        SeedReport report = new SeedReport(dictCount, itemCount, aligned);
        log.info("[mds-dict] 字典种子完成：字典 +{} · 明细 +{} · 产品晶圆类型对齐 {} · 退役项清除 {}",
                report.dicts(), report.items(), report.alignedProducts(), retired);
        return report;
    }

    /** V6 产品种子的中文晶圆类型 → 字典 itemKey（一次性对齐；幂等：找不到中文值即 0）。 */
    /**
     * 退役词表项清除（V37：TOOL 移出耗材类别——耐用工具归 equipment 域）。
     *
     * <p>种子只增不改，但退役项必须清——放在 Java 种子而非 Flyway 迁移：
     * {@code sys_dict_item} 是平台 cim-system 域的表，不在 MDS 迁移 schema 内。
     * 幂等：不存在即 0。</p>
     */
    private int retireConsumableToolItem() {
        SysDict dict = dictRepository.findFirstByCodeOrderByCreateTimeAsc("mds.consumable_type").orElse(null);
        if (dict == null) {
            return 0;
        }
        List<SysDictItem> retired = itemRepository.findByDictIdOrderBySortNoAsc(dict.getId()).stream()
                .filter(i -> "TOOL".equals(i.getItemKey()))
                .toList();
        itemRepository.deleteAll(retired);
        return retired.size();
    }

    private int alignProductSeedWaferTypes() {
        int n = 0;
        for (var p : productRepository.findAll()) {
            if (Boolean.TRUE.equals(p.getDeleted()) || p.getSubstrateType() == null) {
                continue;
            }
            String mapped = switch (p.getSubstrateType()) {
                case "外延片" -> "EPI";
                case "P 型衬底" -> "P_SUB";
                default -> null;
            };
            if (mapped != null) {
                p.setSubstrateType(mapped);
                productRepository.save(p);
                n++;
            }
        }
        return n;
    }
}
