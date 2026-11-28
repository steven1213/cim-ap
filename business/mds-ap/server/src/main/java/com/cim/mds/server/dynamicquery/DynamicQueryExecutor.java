package com.cim.mds.server.dynamicquery;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 动态查询执行器（V39）——运行时唯一执行入口，三层防护与保存验证同源。
 *
 * <p>① 命名参数 → JDBC 位置参数（{@link DynamicQuerySqlValidator#scanParams}，
 * 值一律参数绑定，杜绝拼接）；② 分页外层包装（LIMIT/OFFSET + COUNT 子查询），
 * 页大小取 min(请求值, query.page_size_max) 强制封顶；③ 只读连接（setReadOnly）
 * + queryTimeout + maxRows——即使 SQL 通过验证，物理层也杜绝任何写与失控扫描。</p>
 *
 * <p>dry-run（LIMIT 1）承担保存验证第三层：表/列真实存在才允许落库；
 * 同时为 ViewModel col_key 配置提供结果集元数据。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicQueryExecutor {

    /** dry-run / 导出的绝对行数上限（防失控扫描，超出直接截断）。 */
    public static final int EXPORT_MAX_ROWS = 10_000;

    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    // ------------------------------------------------------------------ 结果元数据

    /**
     * 结果集列元数据（LIMIT 1 dry-run）。col_key 配置校验与前端列选择共用。
     */
    public List<Map<String, Object>> columns(CimQueryDef def, List<CimQueryParam> declaredParams,
                                             Map<String, String> stringParams) {
        java.util.Set<String> declared = declareSet(declaredParams);
        DynamicQuerySqlValidator.ParamScan scan =
                DynamicQuerySqlValidator.scanParams(def.getSqlText(), declared);
        List<Object> values = bindValues(def, declaredParams, scan, stringParams);
        return runStatement(def, stringParams, 1, (ps, con) -> {
            ResultSet rs = ps.executeQuery();
            ResultSetMetaData md = rs.getMetaData();
            List<Map<String, Object>> cols = new ArrayList<>();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                Map<String, Object> col = new LinkedHashMap<>();
                col.put("key", md.getColumnLabel(i).toLowerCase());
                col.put("label", md.getColumnLabel(i));
                col.put("sqlType", md.getColumnTypeName(i));
                cols.add(col);
            }
            return cols;
        }, 5, "SELECT * FROM (" + scan.jdbcSqlText() + ") dq_meta LIMIT 1", values);
    }

    // ------------------------------------------------------------------ 分页执行

    public record PageData(long total, List<Map<String, Object>> rows) {
    }

    /**
     * 分页执行：COUNT 子查询 + 外层 LIMIT/OFFSET；页大小取 min(size, page_size_max)。
     */
    public PageData page(CimQueryDef def, List<CimQueryParam> declaredParams,
                         Map<String, String> stringParams, int page, int size) {
        java.util.Set<String> declared = declareSet(declaredParams);
        DynamicQuerySqlValidator.ParamScan scan =
                DynamicQuerySqlValidator.scanParams(def.getSqlText(), declared);
        List<Object> values = bindValues(def, declaredParams, scan, stringParams);

        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), def.getPageSizeMax() == null ? 500 : def.getPageSizeMax());
        int offset = (safePage - 1) * safeSize;

        long total = runStatement(def, stringParams, 1, (ps, con) -> {
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getLong(1);
        }, 10, "SELECT COUNT(*) FROM (" + scan.jdbcSqlText() + ") dq_cnt", values);

        List<Object> pageValues = new ArrayList<>(values);
        pageValues.add(safeSize);
        pageValues.add(offset);
        String pageSql = "SELECT * FROM (" + scan.jdbcSqlText() + ") dq_page LIMIT ? OFFSET ?";
        List<Map<String, Object>> rows = runStatement(def, stringParams, safeSize, (ps, con) -> {
            ResultSet rs = ps.executeQuery();
            ResultSetMetaData md = rs.getMetaData();
            List<Map<String, Object>> list = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    row.put(md.getColumnLabel(i).toLowerCase(), rs.getObject(i));
                }
                list.add(row);
            }
            return list;
        }, 30, pageSql, pageValues);
        return new PageData(total, rows);
    }

    // ------------------------------------------------------------------ 导出（上限封顶）

    public List<Map<String, Object>> exportRows(CimQueryDef def, List<CimQueryParam> declaredParams,
                                                Map<String, String> stringParams) {
        java.util.Set<String> declared = declareSet(declaredParams);
        DynamicQuerySqlValidator.ParamScan scan =
                DynamicQuerySqlValidator.scanParams(def.getSqlText(), declared);
        List<Object> values = bindValues(def, declaredParams, scan, stringParams);
        String sql = "SELECT * FROM (" + scan.jdbcSqlText() + ") dq_exp LIMIT " + EXPORT_MAX_ROWS;
        return runStatement(def, stringParams, EXPORT_MAX_ROWS, (ps, con) -> {
            ResultSet rs = ps.executeQuery();
            ResultSetMetaData md = rs.getMetaData();
            List<Map<String, Object>> list = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    row.put(md.getColumnLabel(i).toLowerCase(), rs.getObject(i));
                }
                list.add(row);
            }
            return list;
        }, 60, sql, values);
    }

    // ------------------------------------------------------------------ 内部

    private interface ResultSetMapper<T> {
        T map(PreparedStatement ps, Connection con) throws SQLException;
    }

    /**
     * 统一执行：只读连接 + 语句超时 + maxRows + 值绑定。dry-run 用 LIMIT 包装后的
     * sqlText（原始未重写），保存验证与运行时同一条链路。
     */
    private <T> T runStatement(CimQueryDef def, Map<String, String> stringParams, int maxRows,
                               ResultSetMapper<T> mapper, int timeoutSeconds, String sql, List<Object> values) {
        return jdbcTemplate.execute((Connection con) -> {
            con.setReadOnly(true);
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setQueryTimeout(timeoutSeconds);
                ps.setMaxRows(maxRows);
                ps.setFetchSize(Math.min(maxRows, 500));
                for (int i = 0; i < values.size(); i++) {
                    Object v = values.get(i);
                    if (v instanceof LocalDate d) {
                        ps.setObject(i + 1, java.sql.Date.valueOf(d));
                    } else if (v instanceof LocalDateTime t) {
                        ps.setObject(i + 1, java.sql.Timestamp.valueOf(t));
                    } else {
                        ps.setObject(i + 1, v);
                    }
                }
                return mapper.map(ps, con);
            }
        });
    }

    /**
     * 保存验证第三层：只读真跑 LIMIT 1——表/列真实存在、语句可执行。
     * dry-run 前先做语法+参数两层（与保存验证同链路）。
     */
    public void requireDryRunnable(CimQueryDef def, List<CimQueryParam> declaredParams,
                                   Map<String, String> sampleParams) {
        DynamicQuerySqlValidator.requireValidSelect(def.getSqlText());
        java.util.Set<String> declared = declareSet(declaredParams);
        DynamicQuerySqlValidator.ParamScan scan =
                DynamicQuerySqlValidator.scanParams(def.getSqlText(), declared);
        DynamicQuerySqlValidator.requireParamsConsistent(scan, declared);
        Map<String, String> sample = new LinkedHashMap<>();
        for (CimQueryParam p : declaredParams) {
            sample.put(p.getParamKey(), sampleParams.getOrDefault(p.getParamKey(), sampleValue(p)));
        }
        try {
            runStatement(def, sample, 1, (ps, con) -> {
                try (ResultSet rs = ps.executeQuery()) {
                    rs.getMetaData();
                    return null;
                }
            }, 5, "SELECT * FROM (" + scan.jdbcSqlText() + ") dq_dry LIMIT 1",
                    bindValues(def, declaredParams, scan, sample));
        } catch (org.springframework.dao.DataAccessException e) {
            log.debug("[dq-validate] dry-run 失败: {}", e.getMostSpecificCause().getMessage());
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.dq.dryRunFailed",
                    rootMessage(e.getMostSpecificCause().getMessage()));
        }
    }

    /** 声明参数 → 执行值转换（STRING/DICT 原样，NUMBER 解析，DATE 解析日期）。 */
    private List<Object> bindValues(CimQueryDef def, List<CimQueryParam> declaredParams,
                                    DynamicQuerySqlValidator.ParamScan scan, Map<String, String> stringParams) {
        Map<String, Object> byName = new LinkedHashMap<>();
        for (CimQueryParam p : declaredParams) {
            String raw = stringParams.get(p.getParamKey());
            if (raw == null || raw.isEmpty()) {
                if (Boolean.TRUE.equals(p.getRequired())) {
                    throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.dq.paramRequired", p.getParamKey());
                }
                byName.put(p.getParamKey(), null);
                continue;
            }
            try {
                switch (p.getParamType() == null ? "STRING" : p.getParamType()) {
                    case "NUMBER" -> byName.put(p.getParamKey(), new java.math.BigDecimal(raw));
                    case "DATE" -> byName.put(p.getParamKey(), raw.length() > 10
                            ? LocalDateTime.parse(raw) : LocalDate.parse(raw));
                    default -> byName.put(p.getParamKey(), raw);
                }
            } catch (NumberFormatException | DateTimeParseException e) {
                throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.dq.paramTypeMismatch",
                        p.getParamKey(), raw, p.getParamType());
            }
        }
        return DynamicQuerySqlValidator.bindValues(scan, byName);
    }

    private java.util.Set<String> declareSet(List<CimQueryParam> declaredParams) {
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        for (CimQueryParam p : declaredParams) {
            keys.add(p.getParamKey());
        }
        return keys;
    }

    private String sampleValue(CimQueryParam p) {
        return switch (p.getParamType() == null ? "STRING" : p.getParamType()) {
            case "NUMBER" -> "1";
            case "DATE" -> "2000-01-01";
            default -> "";
        };
    }

    private String rootMessage(String msg) {
        if (msg == null) {
            return "unknown";
        }
        String one = msg.replace('\n', ' ').replace('\r', ' ');
        return one.length() > 300 ? one.substring(0, 300) : one;
    }
}
