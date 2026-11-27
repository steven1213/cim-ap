package com.cim.mds.server.edc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.cim.mds.server.flow.CimParamDef;
import com.cim.mds.server.flow.CimParamDefRepository;
import com.cim.mds.server.flow.ParamDefAdminService;

import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * EDC Excel 导入（V28）——模板下载 + 批量导入，「模板下载 → 填好 → 导入」为主动线，
 * 页面逐条新建能力保留。五类：
 *
 * <ul>
 *   <li>PARAM_DEF：参数模板（upsert by attr_key；复用 ParamDefAdminService 守门）</li>
 *   <li>PARAM_SET_ITEM：参数集成员（targetId=参数集 id；upsert by attr_key）</li>
 *   <li>PLAN_ITEM：EDC 方案明细（targetId=方案 id；须 UNFROZEN；upsert by attr_key）</li>
 *   <li>REPORT_ITEM：报告模板成员（targetId=模板 id；upsert by svid）</li>
 *   <li>EQUIP_MAP：设备接口映射（upsert by 机台×CEID×模板）</li>
 * </ul>
 *
 * <p>导入口径：逐行校验、好行落库、坏行进报告（不整单回滚），报告含行号与原因。
 * 解析通道与流程导入同款（base64 JSON，无 multipart）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EdcImportService {

    public static final String KIND_PARAM_DEF = "PARAM_DEF";
    public static final String KIND_SET_ITEM = "PARAM_SET_ITEM";
    public static final String KIND_PLAN_ITEM = "PLAN_ITEM";
    public static final String KIND_REPORT_ITEM = "REPORT_ITEM";
    public static final String KIND_EQUIP_MAP = "EQUIP_MAP";

    private static final DataFormatter CELL_FMT = new DataFormatter();

    private final ParamDefAdminService paramDefService;
    private final CimParamDefRepository defRepository;
    private final EdcAdminService adminService;
    private final EdcPlanService planService;
    private final CimEdcParamSetRepository setRepository;
    private final CimEdcParamSetItemRepository setItemRepository;
    private final CimEdcReportTplRepository tplRepository;
    private final CimEdcReportTplItemRepository tplItemRepository;
    private final CimEdcPlanRepository planRepository;
    private final CimEdcPlanItemRepository planItemRepository;
    private final CimEdcEquipMapRepository mapRepository;

    public record RowFail(int row, String message) {
    }

    public record ImportReport(String kind, int total, int success, int failed, List<RowFail> failures) {
    }

    // ================================================================ 模板

    @Transactional(readOnly = true)
    public byte[] template(String kind) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            switch (kind) {
                case KIND_PARAM_DEF -> exampleSheet(wb, "param_defs",
                        new String[]{"attr_key", "attr_name", "group_code", "value_type", "enum_dict",
                                "unit", "required", "scopes", "sort_no"},
                        "ETCH_GAS_FLOW|刻蚀气体流量|PROCESS|DECIMAL||sccm|TRUE|OPERATION,FLOW,STEP|100");
                case KIND_SET_ITEM -> exampleSheet(wb, "param_set_items",
                        new String[]{"attr_key", "sort_no"},
                        "ETCH_GAS_FLOW|10");
                case KIND_PLAN_ITEM -> exampleSheet(wb, "plan_items",
                        new String[]{"attr_key", "collect_mode", "sample_rule", "interval_minutes",
                                "required", "target", "lsl", "usl", "ooc_action", "sort_no"},
                        "ETCH_GAS_FLOW|AUTO|BY_TIME|30|TRUE|100|90|110|HOLD|10");
                case KIND_REPORT_ITEM -> exampleSheet(wb, "report_tpl_items",
                        new String[]{"svid", "attr_key", "sort_no"},
                        "1001|ETCH_GAS_FLOW|1");
                case KIND_EQUIP_MAP -> exampleSheet(wb, "equip_maps",
                        new String[]{"equipment_code", "ceid", "tpl_code", "source_channel"},
                        "EQP-ETCH-01|1001|RPT-ETCH-PROC|GEM");
                default -> throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.edc.importKindInvalid", kind);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BizException(BizCode.PARAM_INVALID, "模板生成失败: " + e.getMessage());
        }
    }

    private void exampleSheet(XSSFWorkbook wb, String name, String[] headers, String example) {
        Sheet sh = wb.createSheet(name);
        Row head = sh.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            head.createCell(i).setCellValue(headers[i]);
        }
        Row ex = sh.createRow(1);
        String[] cols = example.split("\\|", -1);
        for (int i = 0; i < headers.length; i++) {
            ex.createCell(i).setCellValue(i < cols.length ? cols[i] : "");
        }
        for (int i = 0; i < headers.length; i++) {
            sh.autoSizeColumn(i);
        }
    }

    // ================================================================ 导入

    @Transactional
    public ImportReport importExcel(String kind, String dataBase64, String targetId) {
        byte[] bytes;
        try {
            bytes = java.util.Base64.getDecoder().decode(dataBase64);
        } catch (IllegalArgumentException e) {
            throw new BizException(BizCode.PARAM_INVALID, "dataBase64 非法: " + e.getMessage());
        }
        List<String[]> rows = parseRows(bytes, kind);
        List<RowFail> failures = new ArrayList<>();
        int success = 0;
        for (int i = 0; i < rows.size(); i++) {
            String[] cols = rows.get(i);
            int excelRow = i + 2; // 表头占 1 行
            try {
                switch (kind) {
                    case KIND_PARAM_DEF -> importParamDef(cols);
                    case KIND_SET_ITEM -> importSetItem(cols, targetId);
                    case KIND_PLAN_ITEM -> importPlanItem(cols, targetId);
                    case KIND_REPORT_ITEM -> importReportItem(cols, targetId);
                    case KIND_EQUIP_MAP -> importEquipMap(cols);
                    default -> throw BizException.of(BizCode.PARAM_INVALID,
                            "mds.biz.edc.importKindInvalid", kind);
                }
                success++;
            } catch (BizException e) {
                failures.add(new RowFail(excelRow, e.getMessage()));
            } catch (Exception e) {
                failures.add(new RowFail(excelRow, "解析失败: " + e.getMessage()));
            }
        }
        return new ImportReport(kind, rows.size(), success, failures.size(), failures);
    }

    private List<String[]> parseRows(byte[] bytes, String kind) {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sh = wb.getSheetAt(0);
            int colCount = switch (kind) {
                case KIND_PARAM_DEF -> 9;
                case KIND_SET_ITEM -> 2;
                case KIND_PLAN_ITEM -> 10;
                case KIND_REPORT_ITEM -> 3;
                case KIND_EQUIP_MAP -> 4;
                default -> throw BizException.of(BizCode.PARAM_INVALID,
                        "mds.biz.edc.importKindInvalid", kind);
            };
            List<String[]> rows = new ArrayList<>();
            for (int r = 1; r <= sh.getLastRowNum(); r++) {
                Row row = sh.getRow(r);
                if (row == null) {
                    continue;
                }
                String[] cols = new String[colCount];
                boolean empty = true;
                for (int c = 0; c < colCount; c++) {
                    Cell cell = row.getCell(c);
                    String v = cell == null ? "" : CELL_FMT.formatCellValue(cell).trim();
                    cols[c] = v.isEmpty() ? null : v;
                    if (v.isEmpty() && cell != null && cell.getCellType() == CellType.NUMERIC) {
                        // 数字单元格保留原始精度（防 DataFormatter 千分位/科学计数）
                        cols[c] = new java.math.BigDecimal(String.valueOf(
                                cell.getNumericCellValue())).stripTrailingZeros().toPlainString();
                    }
                    if (cols[c] != null) {
                        empty = false;
                    }
                }
                if (!empty) {
                    rows.add(cols);
                }
            }
            return rows;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(BizCode.PARAM_INVALID, "Excel 解析失败: " + e.getMessage());
        }
    }

    // ---------------- 各类导入实现 ----------------

    private void importParamDef(String[] c) {
        String attrKey = requireCol(c[0], "attr_key");
        var existing = defRepository.findByAttrKeyAndDeletedFalse(attrKey);
        ParamDefAdminService.ParamDefReq req = new ParamDefAdminService.ParamDefReq(
                attrKey,
                requireCol(c[1], "attr_name"),
                c[2] == null || c[2].isBlank() ? "DC" : c[2],
                c[3] == null || c[3].isBlank() ? CimParamDef.VT_STRING : c[3],
                c[4],
                c[5],
                c[6] == null || "TRUE".equalsIgnoreCase(c[6]),
                // scopes 空白 = 纯字典参数（不作为 ContextChain KV 暴露），与 EdcDemoSeed 同口径
                c[7] == null || c[7].isBlank() ? List.of() : List.of(c[7].split(",")),
                parseInt(c[8], "sort_no"),
                Boolean.TRUE);
        if (existing.isPresent()) {
            paramDefService.update(existing.get().getId(), req);
        } else {
            paramDefService.create(req);
        }
    }

    private void importSetItem(String[] c, String setId) {
        if (setId == null || setId.isBlank()) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.edc.importTargetInvalid");
        }
        CimEdcParamSet set = setRepository.findById(setId)
                .filter(s -> !Boolean.TRUE.equals(s.getDeleted()))
                .orElseThrow(() -> BizException.of(BizCode.PARAM_INVALID,
                        "mds.biz.edc.importTargetInvalid"));
        String attrKey = requireCol(c[0], "attr_key");
        adminService.requireParam(attrKey);
        Integer sortNo = parseInt(c[1], "sort_no");
        var existing = setItemRepository.findBySetIdAndDeletedFalseOrderBySortNoAsc(setId).stream()
                .filter(i -> attrKey.equals(i.getAttrKey()))
                .findFirst();
        if (existing.isPresent()) {
            existing.get().setSortNo(sortNo == null ? existing.get().getSortNo() : sortNo);
            setItemRepository.save(existing.get());
        } else {
            CimEdcParamSetItem item = new CimEdcParamSetItem();
            item.setSetId(set.getId());
            item.setAttrKey(attrKey);
            item.setSortNo(sortNo == null ? 0 : sortNo);
            setItemRepository.save(item);
        }
    }

    private void importPlanItem(String[] c, String planId) {
        if (planId == null || planId.isBlank()) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.edc.importTargetInvalid");
        }
        CimEdcPlan plan = planRepository.findById(planId)
                .filter(p -> !Boolean.TRUE.equals(p.getDeleted()))
                .orElseThrow(() -> BizException.of(BizCode.PARAM_INVALID,
                        "mds.biz.edc.importTargetInvalid"));
        if (!CimEdcPlan.STATUS_UNFROZEN.equals(plan.getStatus())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.edc.itemsEditableOnlyUnfrozen", plan.getPlanCode(), plan.getStatus());
        }
        String attrKey = requireCol(c[0], "attr_key");
        adminService.requireParam(attrKey);
        var existing = planItemRepository.findByPlanIdAndDeletedFalseOrderBySortNoAsc(planId).stream()
                .filter(i -> attrKey.equals(i.getAttrKey()))
                .findFirst();
        EdcPlanService.PlanItemReq req = new EdcPlanService.PlanItemReq(
                attrKey, c[1], c[2], parseInt(c[3], "interval_minutes"),
                c[4] != null && "TRUE".equalsIgnoreCase(c[4]),
                parseDecimal(c[5], "target"), parseDecimal(c[6], "lsl"), parseDecimal(c[7], "usl"),
                c[8], parseInt(c[9], "sort_no"));
        // 逐行走统一校验（enum/间隔/规格限守门与页面同口径）
        List<CimEdcPlanItem> validated = planService.validateItems(List.of(req), plan.getId());
        CimEdcPlanItem item = validated.get(0);
        if (existing.isPresent()) {
            item.setId(existing.get().getId());
            item.setVersion(existing.get().getVersion());
        }
        planItemRepository.save(item);
    }

    private void importReportItem(String[] c, String tplId) {
        if (tplId == null || tplId.isBlank()) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.edc.importTargetInvalid");
        }
        CimEdcReportTpl tpl = tplRepository.findById(tplId)
                .filter(t -> !Boolean.TRUE.equals(t.getDeleted()))
                .orElseThrow(() -> BizException.of(BizCode.PARAM_INVALID,
                        "mds.biz.edc.importTargetInvalid"));
        Integer svid = parseInt(c[0], "svid");
        if (svid == null || svid <= 0) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.edc.svidInvalid", c[0]);
        }
        String attrKey = requireCol(c[1], "attr_key");
        adminService.requireParam(attrKey);
        Integer sortNo = parseInt(c[2], "sort_no");
        var existing = tplItemRepository.findByTplIdAndDeletedFalseOrderBySortNoAscSvidAsc(tplId).stream()
                .filter(i -> svid.equals(i.getSvid()))
                .findFirst();
        if (existing.isPresent()) {
            existing.get().setAttrKey(attrKey);
            existing.get().setSortNo(sortNo == null ? existing.get().getSortNo() : sortNo);
            tplItemRepository.save(existing.get());
        } else {
            CimEdcReportTplItem item = new CimEdcReportTplItem();
            item.setTplId(tpl.getId());
            item.setSvid(svid);
            item.setAttrKey(attrKey);
            item.setSortNo(sortNo == null ? 0 : sortNo);
            tplItemRepository.save(item);
        }
    }

    private void importEquipMap(String[] c) {
        String tplCode = requireCol(c[2], "tpl_code");
        CimEdcReportTpl tpl = tplRepository.findByTplCodeAndDeletedFalse(tplCode)
                .orElseThrow(() -> BizException.of(BizCode.PARAM_INVALID,
                        "mds.biz.edc.tplNotFound", tplCode));
        adminService.createMap(new EdcAdminService.MapReq(
                requireCol(c[0], "equipment_code"),
                parseInt(c[1], "ceid"),
                tpl.getId(),
                c[3] == null || c[3].isBlank() ? CimEdcEquipMap.CH_GEM : c[3],
                null));
    }

    // ---------------- 小工具 ----------------

    private static String requireCol(String v, String name) {
        if (v == null || v.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, name + " 不能为空");
        }
        return v.trim();
    }

    private static Integer parseInt(String v, String name) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(v.trim());
        } catch (NumberFormatException e) {
            throw new BizException(BizCode.PARAM_INVALID, name + " 不是整数: " + v);
        }
    }

    private static BigDecimal parseDecimal(String v, String name) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(v.trim());
        } catch (NumberFormatException e) {
            throw new BizException(BizCode.PARAM_INVALID, name + " 不是数值: " + v);
        }
    }
}
