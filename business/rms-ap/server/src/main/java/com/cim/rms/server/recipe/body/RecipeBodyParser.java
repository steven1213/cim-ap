package com.cim.rms.server.recipe.body;

import com.cim.rms.server.recipe.BodyFormat;

/**
 * 配方 Body 解析器 SPI（可插拔）。
 *
 * <p>每个实现声明自己支持的 {@link BodyFormat}；新增设备私有格式时只须再加一个实现
 * （即 Req.md §1.2 P2 所说的「机型格式适配器」），不必改调用方。</p>
 */
public interface RecipeBodyParser {

    /** 本解析器支持的格式。 */
    BodyFormat format();

    /** 解析器说明（SPI 注册表展示用；缺省为类名）。 */
    default String description() {
        return getClass().getSimpleName();
    }

    /**
     * 解析文本 Body。
     *
     * @param text 已解码的 Body 文本（UTF-8）
     * @return 结构化结果（不含 null 字段的语义错误时抛异常，由调用方降级为告警）
     */
    ParsedRecipe parse(String text);
}
