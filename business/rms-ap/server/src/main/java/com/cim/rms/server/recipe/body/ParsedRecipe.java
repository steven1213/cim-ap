package com.cim.rms.server.recipe.body;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配方 Body 的结构化解析结果（对齐 SEMI E139 的 Recipe = Header + Parameters + SubRecipes 视图）。
 *
 * <p>解析是「配方内容可见」的破局点：没有它，配方详情只能显示版本号与格式，
 * 参数级比对（FR-C4）、EC/SV 校验、良构/可执行校验全部悬空。</p>
 *
 * @param format   来源格式（BodyFormat 名）
 * @param meta     配方头/元信息（机型、修订号、校验和等，可能为空）
 * @param steps    步序（可能为空）
 * @param params   参数（键/名称/类型/值/单位/上下限）
 * @param subRecipes 子配方（对齐 Recipe.parentId 层级与 E139 SubRecipe）
 * @param warnings 解析告警（非致命，如缺字段、忽略行）
 */
public record ParsedRecipe(
        String format,
        Map<String, String> meta,
        List<Step> steps,
        List<Param> params,
        List<SubRecipe> subRecipes,
        List<String> warnings) {

    /** 步序。{@code params} 为该步涉及的参数键。 */
    public record Step(Integer stepNo, String name, String remark, List<String> params) {
    }

    /** 参数（{@code min}/{@code max} 供展示；合规判定仍以 ParamSpec 为准）。 */
    public record Param(String key, String name, String type, String value,
                        String unit, String min, String max) {
    }

    /** 子配方引用。 */
    public record SubRecipe(String name, String ref, Integer stepNo, List<Param> params) {
    }

    public boolean hasParams() {
        return params != null && !params.isEmpty();
    }

    /**
     * 扁平「键 → 值」映射。
     *
     * <p>用于写入 {@code RecipeVersion.paramSnapshot} —— 该列被参数级比对与
     * {@code ParamSpecService.checkSnapshot} 消费，必须是扁平 map，故此处做投影。</p>
     */
    public Map<String, String> flatParams() {
        Map<String, String> m = new LinkedHashMap<>();
        if (params != null) {
            for (Param p : params) {
                if (p.key() != null) {
                    m.put(p.key(), p.value());
                }
            }
        }
        return m;
    }
}
