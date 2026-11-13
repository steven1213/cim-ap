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
            new DictDef("mds.wafer_type", "晶圆类型", List.of(
                    new ItemDef("P_SUB", "P 型衬底", 10),
                    new ItemDef("N_SUB", "N 型衬底", 20),
                    new ItemDef("EPI", "外延片", 30),
                    new ItemDef("SOI", "SOI 片", 40),
                    new ItemDef("SAPPHIRE", "蓝宝石", 50),
                    new ItemDef("OTHER", "其他", 90))),
            new DictDef("mds.product_group", "产品分组", List.of(
                    new ItemDef("CIS", "CIS（图像传感器）", 10),
                    new ItemDef("PMIC", "PMIC（电源管理）", 20),
                    new ItemDef("LOGIC", "逻辑", 30),
                    new ItemDef("MEMS", "MEMS（传感器）", 40),
                    new ItemDef("NPW", "陪片", 50),
                    new ItemDef("OTHER", "其他", 90))));

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
        SeedReport report = new SeedReport(dictCount, itemCount, aligned);
        log.info("[mds-dict] 字典种子完成：字典 +{} · 明细 +{} · 产品晶圆类型对齐 {}",
                report.dicts(), report.items(), report.alignedProducts());
        return report;
    }

    /** V6 产品种子的中文晶圆类型 → 字典 itemKey（一次性对齐；幂等：找不到中文值即 0）。 */
    private int alignProductSeedWaferTypes() {
        int n = 0;
        for (var p : productRepository.findAll()) {
            if (Boolean.TRUE.equals(p.getDeleted()) || p.getWaferType() == null) {
                continue;
            }
            String mapped = switch (p.getWaferType()) {
                case "外延片" -> "EPI";
                case "P 型衬底" -> "P_SUB";
                default -> null;
            };
            if (mapped != null) {
                p.setWaferType(mapped);
                productRepository.save(p);
                n++;
            }
        }
        return n;
    }
}
