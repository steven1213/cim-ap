package com.cim.rms.server.recipe.body;

import com.cim.rms.server.recipe.BodyFormat;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * 配方 Body 解析服务：Base64 解码 → 选择解析器 → 结构化结果 / 扁平快照。
 *
 * <p><b>失败不阻断</b>：解析失败只降级为空结果并告警，绝不因配方格式不认识而拒绝上传
 * （未识别的机型格式是常态，Req.md §1.2 P2）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeBodyParseService {

    private final List<RecipeBodyParser> parsers;
    private final ObjectMapper objectMapper;

    /** 已注册解析器清单（W11 SPI 开放：前端格式下拉数据源 + 排查「格式没被解析」问题）。 */
    public List<FormatInfo> formats() {
        List<FormatInfo> out = new java.util.ArrayList<>();
        for (RecipeBodyParser p : parsers) {
            out.add(new FormatInfo(p.format() == null ? null : p.format().name(), p.description()));
        }
        return out;
    }

    /** 注册格式信息。 */
    public record FormatInfo(String format, String description) {
    }

    /** 解析结果或失败原因（供良构校验区分「解析失败」与「解析成功但无参数」）。 */
    public record Outcome(ParsedRecipe parsed, String error) {
        public boolean ok() {
            return parsed != null;
        }
    }

    /**
     * 解码 Body 文本。
     *
     * <p>{@code BINARY} 返回 {@code null}（需机型适配器，本期不处理）；
     * 其余格式按 Base64 解码 UTF-8，解码失败时按「已是明文」容忍（演示/手工录入场景）。</p>
     */
    public String decodeText(BodyFormat format, String bodyBase64) {
        if (bodyBase64 == null || bodyBase64.isBlank()) {
            return null;
        }
        if (format == BodyFormat.BINARY) {
            return null;
        }
        try {
            return new String(Base64.getDecoder().decode(bodyBase64), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return bodyBase64;
        }
    }

    /** 解析（带失败原因）。 */
    public Outcome parseOutcome(BodyFormat format, String bodyBase64) {
        if (bodyBase64 == null || bodyBase64.isBlank()) {
            return new Outcome(null, "Body 为空");
        }
        BodyFormat fmt = format == null ? BodyFormat.TEXT : format;
        if (fmt == BodyFormat.BINARY) {
            return new Outcome(null, "BINARY 格式需机型适配器，本期不解析");
        }
        RecipeBodyParser parser = parsers.stream().filter(p -> p.format() == fmt).findFirst().orElse(null);
        if (parser == null) {
            return new Outcome(null, "无匹配的解析器: " + fmt);
        }
        String text = decodeText(fmt, bodyBase64);
        if (text == null || text.isBlank()) {
            return new Outcome(null, "Body 解码后为空");
        }
        try {
            return new Outcome(parser.parse(text), null);
        } catch (Exception e) {
            return new Outcome(null, e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    /** 解析（无失败原因版，失败即空）。 */
    public Optional<ParsedRecipe> tryParse(BodyFormat format, String bodyBase64) {
        Outcome o = parseOutcome(format, bodyBase64);
        if (!o.ok()) {
            log.debug("[body-parse] 解析未成功: {}", o.error());
        }
        return Optional.ofNullable(o.parsed());
    }

    /** 生成扁平参数快照 JSON（写入 {@code RecipeVersion.paramSnapshot}）；无参数返回 {@code null}。 */
    public String snapshotJson(ParsedRecipe parsed) {
        if (parsed == null || !parsed.hasParams()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(parsed.flatParams());
        } catch (Exception e) {
            log.warn("[body-parse] 参数快照序列化失败: {}", e.toString());
            return null;
        }
    }

    /** 便捷方法：直接由 Body 得到参数快照 JSON（失败/无参数返回 {@code null}）。 */
    public String snapshotJson(BodyFormat format, String bodyBase64) {
        Outcome o = parseOutcome(format, bodyBase64);
        return o.ok() ? snapshotJson(o.parsed()) : null;
    }
}
