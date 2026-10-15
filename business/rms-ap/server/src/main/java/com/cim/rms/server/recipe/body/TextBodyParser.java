package com.cim.rms.server.recipe.body;

import com.cim.rms.server.recipe.BodyFormat;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文本行式配方解析器（{@link BodyFormat#TEXT}）。
 *
 * <p>半导体机台导出的配方常为「键值行 + 段落」的纯文本。本解析器接受的语法：</p>
 * <pre>
 * # 以 # 或 ; 开头：注释（忽略）
 * &#64;model = CVD-3000            // 以 &#64; 开头：元信息（meta）
 * [STEP 升温]                    // 段落头：开启一个步序（亦接受 [升温]）
 * CHAMBER.TEMP = 70 | C          // 键 = 值 | 单位（单位与分隔符 | 可省略）
 * PRESSURE : 2.5                 // 键 : 值（亦接受）
 * </pre>
 * <p>段落下方的参数会同时归属到该步序；解析器对无法识别的行只记 warning，不抛错。</p>
 */
@Component
public class TextBodyParser implements RecipeBodyParser {

    @Override
    public BodyFormat format() {
        return BodyFormat.TEXT;
    }

    @Override
    public ParsedRecipe parse(String text) {
        List<String> warnings = new ArrayList<>();
        Map<String, String> meta = new LinkedHashMap<>();
        List<ParsedRecipe.Param> params = new ArrayList<>();
        List<ParsedRecipe.Step> steps = new ArrayList<>();

        // 当前步序（可变容器；写完再转 record）
        int[] stepNo = {0};
        String[] curStepName = {null};
        List<String> curStepParams = new ArrayList<>();

        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String line = stripComment(raw).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("[")) {
                int end = line.indexOf(']');
                if (end < 0) {
                    warnings.add("第 " + (i + 1) + " 行：段落头缺少 ']'，按普通行处理");
                } else {
                    // 收口上一步
                    if (curStepName[0] != null) {
                        steps.add(new ParsedRecipe.Step(stepNo[0], curStepName[0], null, new ArrayList<>(curStepParams)));
                    }
                    String inner = line.substring(1, end).trim();
                    String name = inner.replaceFirst("(?i)^step[\\s:]+", "").trim();
                    stepNo[0]++;
                    curStepName[0] = name.isEmpty() ? ("STEP-" + stepNo[0]) : name;
                    curStepParams.clear();
                    continue;
                }
            }
            int sep = separatorIndex(line);
            if (sep < 0) {
                warnings.add("第 " + (i + 1) + " 行：无法识别（缺少 '=' 或 ':'）");
                continue;
            }
            String key = line.substring(0, sep).trim();
            String rest = line.substring(sep + 1).trim();
            if (key.isEmpty()) {
                warnings.add("第 " + (i + 1) + " 行：键为空");
                continue;
            }
            String unit = null;
            int pipe = rest.indexOf('|');
            if (pipe >= 0) {
                unit = rest.substring(pipe + 1).trim();
                rest = rest.substring(0, pipe).trim();
            }
            if (key.startsWith("@")) {
                meta.put(key.substring(1), rest);
                continue;
            }
            params.add(new ParsedRecipe.Param(key, null, null, rest, unit, null, null));
            if (curStepName[0] != null) {
                curStepParams.add(key);
            }
        }
        if (curStepName[0] != null) {
            steps.add(new ParsedRecipe.Step(stepNo[0], curStepName[0], null, new ArrayList<>(curStepParams)));
        }

        if (params.isEmpty() && steps.isEmpty()) {
            warnings.add("未解析出任何参数或步序（请检查 KEY=VALUE 语法）");
        }
        return new ParsedRecipe("TEXT", meta, steps, params, List.of(), warnings);
    }

    /** 去掉行内注释（# 或 ; 之后的内容），但保留值中的井号不受影响的前提是前面有空白。 */
    private static String stripComment(String line) {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if ((c == '#' || c == ';') && (i == 0 || Character.isWhitespace(line.charAt(i - 1)))) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    /** 第一个 '=' 或 ':' 的位置（两者取更靠前者）。 */
    private static int separatorIndex(String line) {
        int eq = line.indexOf('=');
        int colon = line.indexOf(':');
        if (eq < 0) {
            return colon;
        }
        if (colon < 0) {
            return eq;
        }
        return Math.min(eq, colon);
    }
}
