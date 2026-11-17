package com.cim.mds.server.flow;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 流程规则声明式条件微语法求值器（V15）。
 *
 * <p>语法（自顶向下递归下降，|| 优先级最低）：</p>
 * <pre>
 *   expr    := or
 *   or      := and ( '||' and )*
 *   and     := cmp ( '&&' cmp )*
 *   cmp     := primary ( ('=='|'!='|'&gt;'|'&gt;='|'&lt;'|'&lt;=') primary )?
 *   primary := '(' expr ')' | true | false | 数字 | '字符串' | 标识符
 * </pre>
 *
 * <p>设计取舍：<b>不用 SpEL/脚本引擎</b>——规则条件面向工艺工程师，比较+逻辑组合
 * 足够；自制解析器无反射面，天然免注入，且 unknown 变量可显式收集（评估结果
 * 携带警告而不是静默失真）。语义：未知变量按 null 参与，等值比较恒 false、
 * 不等比较恒 true、排序比较恒 false。</p>
 */
public final class FlowRuleConditionEvaluator {

    /** 求值结果：matched = 条件是否成立；unknownVars = 表达式里未出现在 vars 的标识符。 */
    public record Result(boolean matched, List<String> unknownVars) {
    }

    private FlowRuleConditionEvaluator() {
    }

    /** 语法校验（不执行）：表达式非法抛 IllegalArgumentException。 */
    public static void validate(String expr) {
        if (expr == null || expr.isBlank()) {
            return;
        }
        try {
            new Parser(expr, Map.of(), new ArrayList<>()).parseExpr();
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }

    /** 求值：expr 空 = 恒真。语法错误抛 IllegalArgumentException（保存期已守门，运行期兜底按不命中）。 */
    public static Result evaluate(String expr, Map<String, Object> vars) {
        if (expr == null || expr.isBlank()) {
            return new Result(true, List.of());
        }
        List<String> unknown = new ArrayList<>();
        Parser p = new Parser(expr, vars == null ? Map.of() : vars, unknown);
        boolean matched = p.parseExpr();
        p.expectEnd();
        return new Result(matched, List.copyOf(unknown));
    }

    // ------------------------------------------------------------------ 词法

    private enum TokType { IDENT, STRING, NUMBER, OP, LPAREN, RPAREN, END }

    private record Tok(TokType type, String text, Object value) {
    }

    private static final class Lexer {
        private final String src;
        private int pos;

        Lexer(String src) {
            this.src = src;
        }

        Tok next() {
            skipWs();
            if (pos >= src.length()) {
                return new Tok(TokType.END, "", null);
            }
            char c = src.charAt(pos);
            if (c == '(') {
                pos++;
                return new Tok(TokType.LPAREN, "(", null);
            }
            if (c == ')') {
                pos++;
                return new Tok(TokType.RPAREN, ")", null);
            }
            if (c == '\'') {
                return lexString();
            }
            if (Character.isDigit(c)) {
                return lexNumber();
            }
            if (Character.isJavaIdentifierStart(c)) {
                return lexIdent();
            }
            return lexOp();
        }

        private Tok lexString() {
            int start = pos;
            pos++; // 开引号
            StringBuilder sb = new StringBuilder();
            while (pos < src.length() && src.charAt(pos) != '\'') {
                sb.append(src.charAt(pos++));
            }
            if (pos >= src.length()) {
                throw new IllegalArgumentException("字符串未闭合（位置 " + start + "）");
            }
            pos++; // 闭引号
            return new Tok(TokType.STRING, sb.toString(), sb.toString());
        }

        private Tok lexNumber() {
            int start = pos;
            while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) {
                pos++;
            }
            String text = src.substring(start, pos);
            try {
                return new Tok(TokType.NUMBER, text, new BigDecimal(text));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("非法数字: " + text);
            }
        }

        private Tok lexIdent() {
            int start = pos;
            while (pos < src.length() && Character.isJavaIdentifierPart(src.charAt(pos))) {
                pos++;
            }
            return new Tok(TokType.IDENT, src.substring(start, pos), null);
        }

        private Tok lexOp() {
            for (String op : new String[]{">=", "<=", "==", "!=", "&&", "||", ">", "<"}) {
                if (src.startsWith(op, pos)) {
                    pos += op.length();
                    return new Tok(TokType.OP, op, null);
                }
            }
            throw new IllegalArgumentException("非法字符 '" + src.charAt(pos) + "'（位置 " + pos + "）");
        }

        private void skipWs() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }
    }

    // ------------------------------------------------------------------ 语法

    private static final class Parser {
        private final Lexer lexer;
        private final Map<String, Object> vars;
        private final List<String> unknown;
        private Tok cur;

        Parser(String expr, Map<String, Object> vars, List<String> unknown) {
            this.lexer = new Lexer(expr);
            this.vars = vars;
            this.unknown = unknown;
            this.cur = lexer.next();
        }

        boolean parseExpr() {
            boolean left = parseAnd();
            while (cur.type() == TokType.OP && "||".equals(cur.text())) {
                advance();
                boolean right = parseAnd();
                left = left || right;
            }
            return left;
        }

        private boolean parseAnd() {
            boolean left = parseCmp();
            while (cur.type() == TokType.OP && "&&".equals(cur.text())) {
                advance();
                boolean right = parseCmp();
                left = left && right;
            }
            return left;
        }

        private boolean parseCmp() {
            Object left = parsePrimary();
            if (cur.type() == TokType.OP && isCompareOp(cur.text())) {
                String op = cur.text();
                advance();
                Object right = parsePrimary();
                return compare(left, op, right);
            }
            return truthy(left);
        }

        private Object parsePrimary() {
            switch (cur.type()) {
                case LPAREN -> {
                    advance();
                    boolean v = parseExpr();
                    expect(TokType.RPAREN, ")");
                    return v;
                }
                case STRING, NUMBER -> {
                    Object v = cur.value();
                    advance();
                    return v;
                }
                case IDENT -> {
                    String name = cur.text();
                    if ("true".equals(name)) {
                        advance();
                        return Boolean.TRUE;
                    }
                    if ("false".equals(name)) {
                        advance();
                        return Boolean.FALSE;
                    }
                    advance();
                    if (vars.containsKey(name)) {
                        return vars.get(name);
                    }
                    if (!unknown.contains(name)) {
                        unknown.add(name);
                    }
                    return null;
                }
                default -> throw new IllegalArgumentException("意外的符号 '" + cur.text() + "'");
            }
        }

        private void expect(TokType type, String what) {
            if (cur.type() != type) {
                throw new IllegalArgumentException("期望 " + what + "，实际 '" + cur.text() + "'");
            }
            advance();
        }

        void expectEnd() {
            if (cur.type() != TokType.END) {
                throw new IllegalArgumentException("表达式末尾有多余内容 '" + cur.text() + "'");
            }
        }

        private void advance() {
            cur = lexer.next();
        }

        private static boolean isCompareOp(String op) {
            return op.equals("==") || op.equals("!=") || op.equals(">")
                    || op.equals(">=") || op.equals("<") || op.equals("<=");
        }

        private static boolean truthy(Object v) {
            return Boolean.TRUE.equals(v);
        }

        private static boolean compare(Object left, String op, Object right) {
            boolean leftNull = left == null;
            boolean rightNull = right == null;
            if ("==".equals(op)) {
                return !leftNull && !rightNull && norm(left).equals(norm(right));
            }
            if ("!=".equals(op)) {
                if (leftNull || rightNull) {
                    return leftNull != rightNull;
                }
                return !norm(left).equals(norm(right));
            }
            if (leftNull || rightNull) {
                return false;
            }
            int cmp = compareValues(left, right);
            return switch (op) {
                case ">" -> cmp > 0;
                case ">=" -> cmp >= 0;
                case "<" -> cmp < 0;
                case "<=" -> cmp <= 0;
                default -> false;
            };
        }

        /** 归一化：BigDecimal 与字符串按数值可比；Boolean 原样。 */
        private static Object norm(Object v) {
            if (v instanceof BigDecimal bd) {
                return bd.stripTrailingZeros();
            }
            if (v instanceof Number n) {
                return new BigDecimal(n.toString()).stripTrailingZeros();
            }
            if (v instanceof String s && isNumeric(s)) {
                return new BigDecimal(s).stripTrailingZeros();
            }
            return v;
        }

        private static int compareValues(Object left, Object right) {
            Object l = norm(left);
            Object r = norm(right);
            if (l instanceof BigDecimal bd1 && r instanceof BigDecimal bd2) {
                return bd1.compareTo(bd2);
            }
            if (l instanceof Boolean b1 && r instanceof Boolean b2) {
                return Boolean.compare(b1, b2);
            }
            return l.toString().compareTo(r.toString());
        }

        private static boolean isNumeric(String s) {
            try {
                new BigDecimal(s);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }
}
