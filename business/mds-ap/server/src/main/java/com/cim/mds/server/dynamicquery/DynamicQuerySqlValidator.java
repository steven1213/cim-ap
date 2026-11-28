package com.cim.mds.server.dynamicquery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;

/**
 * 动态查询 SQL 校验器（V39）——保存前三层验证，运行时复用①②层产物。
 *
 * <p>用户拍板口径：Query 允许保存 SQL 文本，但必须通过验证才允许落库——
 * ① 语法层：JSqlParser 解析必须成功，且必须是<b>单条 SELECT</b>（拒绝
 * INSERT/UPDATE/DELETE/MERGE/DDL/多语句）；② 参数层：sql_text 中 {@code :name}
 * 占位（跳过字符串字面量）与声明参数完全一致；③ 真跑层：只读连接 + 超时 +
 * LIMIT 1 dry-run（表/列真实存在），由 {@link DynamicQueryExecutor} 承担。</p>
 *
 * <p>另拦截 {@code SELECT ... INTO}（写路径伪装成查询）。所有拒绝均返回
 * PARAM_INVALID（400）带具体原因，前端验证反馈直接展示。</p>
 */
@Slf4j
public final class DynamicQuerySqlValidator {

    private DynamicQuerySqlValidator() {
    }

    /**
     * 参数占位扫描结果：占位符出现顺序（含重复）+ 重写后的 JDBC 位置参数文本。
     */
    public record ParamScan(List<String> placeholderOrder, String jdbcSqlText) {
    }

    /**
     * 语法 + SELECT-only 验证：返回解析后的 Select（供后续遍历），非法抛 BizException。
     */
    public static Select requireValidSelect(String sqlText) {
        String trimmed = sqlText == null ? "" : sqlText.trim();
        if (trimmed.isEmpty()) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.dq.sqlEmpty");
        }
        // 允许结尾一个分号；其余任何分号都意味着多语句——直接拒绝
        String body = trimmed.endsWith(";") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        if (body.contains(";")) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.dq.multiStatement");
        }

        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(body);
        } catch (JSQLParserException e) {
            log.debug("[dq-validate] 语法解析失败: {}", e.getMessage());
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.dq.syntaxInvalid", rootMessage(e));
        }
        if (!(statement instanceof Select select)) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.dq.notSelect");
        }
        requireNoInto(select);
        return select;
    }

    /**
     * 参数占位扫描：跳过单引号字符串字面量与 {@code ::} 强转，
     * 收集 {@code :name} 出现顺序（含重复），并重写为 JDBC {@code ?} 位置参数。
     */
    public static ParamScan scanParams(String sqlText, Set<String> declaredKeys) {
        List<String> order = new ArrayList<>();
        StringBuilder out = new StringBuilder(sqlText.length());
        boolean inString = false;
        for (int i = 0; i < sqlText.length(); i++) {
            char c = sqlText.charAt(i);
            if (inString) {
                out.append(c);
                if (c == '\'') {
                    if (i + 1 < sqlText.length() && sqlText.charAt(i + 1) == '\'') {
                        out.append('\'');
                        i++;
                    } else {
                        inString = false;
                    }
                }
                continue;
            }
            if (c == '\'') {
                inString = true;
                out.append(c);
                continue;
            }
            if (c == ':' && i + 1 < sqlText.length()) {
                char next = sqlText.charAt(i + 1);
                if (next == ':') { // PostgreSQL 风格强转，非参数
                    out.append("::");
                    i++;
                    continue;
                }
                if (Character.isLetterOrDigit(next) || next == '_') {
                    int j = i + 1;
                    while (j < sqlText.length() && (Character.isLetterOrDigit(sqlText.charAt(j)) || sqlText.charAt(j) == '_')) {
                        j++;
                    }
                    String name = sqlText.substring(i + 1, j);
                    if (declaredKeys != null && !declaredKeys.contains(name)) {
                        throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.dq.paramUndeclared", name);
                    }
                    order.add(name);
                    out.append('?');
                    i = j - 1;
                    continue;
                }
            }
            out.append(c);
        }
        return new ParamScan(order, out.toString());
    }

    /**
     * 声明参数与占位符一致性校验：未声明被引用已拦截（scanParams），
     * 这里拦截反向——声明了但 SQL 未使用（契约漂移，前端参数区出现死控件）。
     */
    public static void requireParamsConsistent(ParamScan scan, Set<String> declaredKeys) {
        Set<String> used = new LinkedHashSet<>(scan.placeholderOrder());
        for (String declared : declaredKeys) {
            if (!used.contains(declared)) {
                throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.dq.paramUnused", declared);
            }
        }
    }

    /**
     * 命名参数 → 值绑定序列：按占位出现顺序展开（同参数多次出现重复绑定）。
     */
    public static List<Object> bindValues(ParamScan scan, Map<String, Object> valuesByName) {
        List<Object> values = new ArrayList<>(scan.placeholderOrder().size());
        for (String name : scan.placeholderOrder()) {
            values.add(valuesByName.get(name));
        }
        return values;
    }

    // ------------------------------------------------------------------ 内部

    /** 拒绝 SELECT ... INTO（写路径伪装成查询）；集合运算/括号子查询逐层下探。 */
    private static void requireNoInto(Select select) {
        Map<String, Select> selectBody = new LinkedHashMap<>();
        try {
            // 4.9 API：Select#withFromItem 系列遍历不便，直接借 toString 结构拦截：
            // PlainSelect.intoTables / SetOperationList 子项逐一检查
            collectIntoChecks(select, selectBody);
        } catch (RuntimeException e) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.dq.syntaxInvalid", e.getMessage());
        }
    }

    private static void collectIntoChecks(Select select, Map<String, Select> seen) {
        if (select == null || seen.containsKey(select.toString())) {
            return;
        }
        seen.put(select.toString(), select);
        if (select instanceof SetOperationList setOp) {
            for (Select item : setOp.getSelects()) {
                collectIntoChecks(item, seen);
            }
            return;
        }
        if (select instanceof PlainSelect plain && plain.getIntoTables() != null && !plain.getIntoTables().isEmpty()) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.dq.intoForbidden");
        }
        if (select instanceof PlainSelect plain) {
            collectIntoChecks(plain.getFromItem() instanceof Select s ? s : null, seen);
        }
        if (select instanceof ParenthesedSelect paren) {
            collectIntoChecks(paren.getSelect(), seen);
        }
    }

    private static String rootMessage(Exception e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return msg.length() > 200 ? msg.substring(0, 200) : msg;
    }
}
