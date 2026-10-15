package com.cim.rms.server.recipe.body;

import com.cim.rms.server.recipe.BodyFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 结构化 JSON 配方解析器（{@link BodyFormat#JSON_STRUCTURED}）。
 *
 * <p>支持两种形态（前者为推荐形态，对齐 E139 的 Header/Parameters/SubRecipes 划分）：</p>
 * <pre>
 * ① 结构化：{ "meta": {...},
 *             "params": { "CHAMBER.TEMP": {"value":"70","unit":"C","min":"60","max":"80"} },
 *             "steps": [ {"no":1,"name":"升温","params":["CHAMBER.TEMP"]} ],
 *             "subRecipes": [ {"name":"DEP","ref":"RCP-CVD-SUB"} ] }
 * ② 扁平：  { "CHAMBER.TEMP": 70, "PRESSURE": 2.5 }   // 顶层标量字段一律视为参数
 * </pre>
 * <p>{@code params} 亦支持数组形态 {@code [{"key":"A","value":"1"}]}。</p>
 */
@Component
@RequiredArgsConstructor
public class JsonStructuredBodyParser implements RecipeBodyParser {

    private static final List<String> RESERVED = List.of("meta", "params", "steps", "subRecipes");

    private final ObjectMapper objectMapper;

    @Override
    public BodyFormat format() {
        return BodyFormat.JSON_STRUCTURED;
    }

    @Override
    public ParsedRecipe parse(String text) {
        JsonNode root;
        try {
            root = objectMapper.readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 解析失败: " + e.getMessage(), e);
        }
        if (root == null || root.isMissingNode() || root.isNull()) {
            throw new IllegalArgumentException("JSON 为空");
        }
        if (!root.isObject()) {
            throw new IllegalArgumentException("JSON 顶层必须是对象（object）");
        }

        List<String> warnings = new ArrayList<>();
        Map<String, String> meta = new LinkedHashMap<>();
        JsonNode metaNode = root.get("meta");
        if (metaNode != null && metaNode.isObject()) {
            metaNode.fields().forEachRemaining(e -> meta.put(e.getKey(), asText(e.getValue())));
        }

        List<ParsedRecipe.Param> params = new ArrayList<>();
        JsonNode paramsNode = root.get("params");
        if (paramsNode == null || paramsNode.isNull()) {
            // 扁平形态：顶层非保留字段即参数
            root.fields().forEachRemaining(e -> {
                if (!RESERVED.contains(e.getKey())) {
                    params.add(toParam(e.getKey(), e.getValue()));
                }
            });
            if (params.isEmpty()) {
                warnings.add("未找到 params 字段，且顶层无其它字段可视为参数");
            }
        } else if (paramsNode.isObject()) {
            paramsNode.fields().forEachRemaining(e -> params.add(toParam(e.getKey(), e.getValue())));
        } else if (paramsNode.isArray()) {
            for (JsonNode n : paramsNode) {
                String key = text(n, "key");
                if (key == null || key.isBlank()) {
                    warnings.add("params 数组元素缺少 key，已忽略");
                    continue;
                }
                params.add(new ParsedRecipe.Param(key, text(n, "name"), text(n, "type"),
                        text(n, "value"), text(n, "unit"), text(n, "min"), text(n, "max")));
            }
        } else {
            warnings.add("params 必须是对象或数组");
        }

        List<ParsedRecipe.Step> steps = new ArrayList<>();
        JsonNode stepsNode = root.get("steps");
        if (stepsNode != null && stepsNode.isArray()) {
            int idx = 0;
            for (JsonNode n : stepsNode) {
                idx++;
                Integer no = n.hasNonNull("no") ? n.get("no").asInt()
                        : (n.hasNonNull("stepNo") ? n.get("stepNo").asInt() : idx);
                List<String> keys = new ArrayList<>();
                JsonNode p = n.get("params");
                if (p != null && p.isArray()) {
                    p.forEach(x -> keys.add(x.asText()));
                } else if (p != null && p.isTextual()) {
                    keys.add(p.asText());
                }
                steps.add(new ParsedRecipe.Step(no, text(n, "name"), text(n, "remark"), keys));
            }
        }

        List<ParsedRecipe.SubRecipe> subs = new ArrayList<>();
        JsonNode subNode = root.get("subRecipes");
        if (subNode != null && subNode.isArray()) {
            for (JsonNode n : subNode) {
                List<ParsedRecipe.Param> sp = new ArrayList<>();
                JsonNode spn = n.get("params");
                if (spn != null && spn.isObject()) {
                    spn.fields().forEachRemaining(e -> sp.add(toParam(e.getKey(), e.getValue())));
                }
                subs.add(new ParsedRecipe.SubRecipe(text(n, "name"), text(n, "ref"),
                        n.hasNonNull("stepNo") ? n.get("stepNo").asInt() : null, sp));
            }
        }

        return new ParsedRecipe("JSON_STRUCTURED", meta, steps, params, subs, warnings);
    }

    private ParsedRecipe.Param toParam(String key, JsonNode node) {
        if (node != null && node.isObject()) {
            return new ParsedRecipe.Param(key, text(node, "name"), text(node, "type"),
                    text(node, "value"), text(node, "unit"), text(node, "min"), text(node, "max"));
        }
        // 标量：值即内容，类型按 JSON 推断
        String type = node == null ? null
                : node.isNumber() ? "NUMBER"
                : node.isBoolean() ? "BOOLEAN"
                : node.isTextual() ? "STRING" : null;
        return new ParsedRecipe.Param(key, null, type, asText(node), null, null, null);
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n == null ? null : n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String asText(JsonNode v) {
        return v == null || v.isNull() ? null : v.asText();
    }
}
