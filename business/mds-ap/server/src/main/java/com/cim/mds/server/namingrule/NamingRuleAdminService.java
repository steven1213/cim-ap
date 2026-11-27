package com.cim.mds.server.namingrule;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 编码规则（NamingRule）服务——配置化 Key Generation：有序段列表 + 段类型解析器 + 分桶计数器。
 *
 * <p><b>段列表模型（2026-11-16 定稿）</b>：规则模板 = JSON 段列表（列表顺序即编码顺序），
 * 段类型 LITERAL/ENUM/DATE/SEQ 各有解析器（{@link SegmentResolver}，注册表分发、
 * 新增段类型零侵入），每段声明字符长度——生成时逐段校验、整码定长。</p>
 *
 * <p><b>强守门口径（{@link #apply}，各域 create 同事务调用）</b>：</p>
 * <ul>
 *   <li>无规则 / 规则停用 → 不干预（编码留空由调用方原有 requireText 拒绝）；</li>
 *   <li>编码留空 + 规则启用 → 逐段渲染（SEQ 段分桶占号）→ 逐段长度校验；</li>
 *   <li>手填编码 + 规则启用 → 必须匹配段派生正则（不匹配 = 400），保证编码形态可预测。</li>
 * </ul>
 *
 * <p><b>计数器</b>：(rule_id, segment_index, scope_key) 分桶，FOR UPDATE 递增、只增不回收；
 * scope 支持 GLOBAL/BY_PARENT/BY_LOCATION/BY_TYPE/BY_DATE（按天重置，scope_key 带口径前缀）。</p>
 */
@Service
@RequiredArgsConstructor
public class NamingRuleAdminService {

    /** 流水号分桶口径（SEQ 段 scope 取值）。 */
    public static final String SCOPE_GLOBAL = "GLOBAL";
    public static final String SCOPE_BY_PARENT = "BY_PARENT";
    public static final String SCOPE_BY_LOCATION = "BY_LOCATION";
    public static final String SCOPE_BY_TYPE = "BY_TYPE";
    public static final String SCOPE_BY_DATE = "BY_DATE";
    public static final List<String> SEQ_SCOPES =
            List.of(SCOPE_GLOBAL, SCOPE_BY_PARENT, SCOPE_BY_LOCATION, SCOPE_BY_TYPE, SCOPE_BY_DATE);

    /** BY_LOCATION 分桶取最具体位置码的优先序（低→高）。 */
    private static final List<String> LOCATION_VARS = List.of("site", "fab", "area", "bay", "stage");

    // ---------------- object_type 常量（各域 create 点引用，避免魔法串） ----------------
    public static final String OBJ_SITE = "SITE";
    public static final String OBJ_FAB = "FAB";
    public static final String OBJ_AREA = "AREA";
    public static final String OBJ_BAY = "BAY";
    public static final String OBJ_STAGE = "STAGE";
    public static final String OBJ_AREA_GROUP = "AREA_GROUP";
    public static final String OBJ_EQUIPMENT_TYPE = "EQUIPMENT_TYPE";
    public static final String OBJ_TOOL_GROUP = "TOOL_GROUP";
    public static final String OBJ_EQUIPMENT = "EQUIPMENT";
    public static final String OBJ_EQUIPMENT_UNIT = "EQUIPMENT_UNIT";
    public static final String OBJ_STOCKER_BANK = "STOCKER_BANK";
    public static final String OBJ_TECHNOLOGY = "TECHNOLOGY";
    public static final String OBJ_PRODUCT = "PRODUCT";
    public static final String OBJ_CAPABILITY = "CAPABILITY";
    public static final String OBJ_FLOW = "FLOW";
    public static final String OBJ_REASON_CODE = "REASON_CODE";

    // ---------------------------------------------------------------- 对象注册表

    /**
     * 对象域声明。
     *
     * @param code      object_type（十四对象白名单，与 MDS 各域 create 点一一对应）
     * @param label     中文名
     * @param parentVar BY_PARENT 分桶取的上下文变量键（null = 无父，退化为全局单桶）
     * @param vars      段可用上下文变量（有序；ENUM 段 var 必须在其中）
     */
    public record ObjectSpec(String code, String label, String parentVar, List<String> vars) {
    }

    /** 十五对象白名单（新增域接入时在此注册 + 对应 create 点调 {@link #apply}）。 */
    public static List<ObjectSpec> objectSpecs() {
        return List.of(
                new ObjectSpec("SITE", "厂区", null, List.of()),
                new ObjectSpec("FAB", "晶圆厂", "site", List.of("site")),
                new ObjectSpec("AREA", "区域", "fab", List.of("fab")),
                new ObjectSpec("BAY", "Bay", "area", List.of("fab", "area")),
                new ObjectSpec("STAGE", "机台位", "bay", List.of("fab", "bay")),
                new ObjectSpec("AREA_GROUP", "区域组", "fab", List.of("fab")),
                new ObjectSpec("EQUIPMENT_TYPE", "设备类型", "kind", List.of("kind", "class")),
                new ObjectSpec("EQUIPMENT", "设备", null,
                        List.of("site", "fab", "area", "bay", "stage", "kind", "type")),
                new ObjectSpec("EQUIPMENT_UNIT", "设备子单元", "eqp", List.of("eqp", "unitType")),
                new ObjectSpec("STOCKER_BANK", "存储区 Bank", "eqp", List.of("eqp")),
                new ObjectSpec("TECHNOLOGY", "制程技术", null, List.of()),
                new ObjectSpec("PRODUCT", "产品", "tech", List.of("tech")),
                new ObjectSpec("CAPABILITY", "设备能力", null, List.of()),
                new ObjectSpec("FLOW", "制造流程", null, List.of("tech")),
                new ObjectSpec("REASON_CODE", "原因码", "type", List.of("type")));
    }

    private static ObjectSpec requireSpec(String objectType) {
        return objectSpecs().stream()
                .filter(s -> s.code().equals(objectType))
                .findFirst()
                .orElseThrow(() -> new BizException(BizCode.PARAM_INVALID,
                        "未知的编码对象类型: " + objectType));
    }

    // ---------------------------------------------------------------- 段列表处理

    /** 解析段列表 JSON（语法错误抛 400）。 */
    private List<SegmentSpec> parseSegments(String segmentsJson) {
        return registry.parse(segmentsJson);
    }

    /**
     * 校验段列表：逐段类型解析器自校验 + ENUM 变量白名单 + 长度声明一致性。
     * 通过后返回段列表（调用方复用，避免二次解析）。
     */
    private List<SegmentSpec> validateSegments(String objectType, String segmentsJson) {
        ObjectSpec spec = requireSpec(objectType);
        List<SegmentSpec> segments = registry.parse(segmentsJson);
        for (int i = 0; i < segments.size(); i++) {
            SegmentSpec seg = segments.get(i);
            if (seg.type() == null || seg.type().isBlank()) {
                throw SegmentResolverRegistry.badSegment(null, i, "段类型 type 不能为空");
            }
            registry.get(seg.type(), i).validate(seg, spec, i);
        }
        return segments;
    }

    /** 手填守门正则：逐段派生精确片段拼接（整码定长）。 */
    private String deriveRegex(List<SegmentSpec> segments) {
        StringBuilder re = new StringBuilder();
        for (SegmentSpec seg : segments) {
            re.append(registry.get(seg.type(), -1).regex(seg));
        }
        return re.toString();
    }

    // ---------------------------------------------------------------- 守门入口

    /**
     * create 守门：返回最终落库编码。
     *
     * @param objectType 对象域（白名单键）
     * @param provided   请求里的手填编码（可空——空 + 规则启用 = 自动生成）
     * @param ctx        上下文变量（调用方按对象域白名单构建，如 EQUIPMENT 的 fab/type/kind）
     */
    @Transactional
    public String apply(String objectType, String provided, Map<String, String> ctx) {
        CimNamingRule rule = ruleRepository.findByObjectTypeAndEnabledTrueAndDeletedFalse(objectType)
                .orElse(null);
        if (rule == null) {
            // 无规则/停用 → 不干预；空编码由调用方 requireText 拒绝（保持既有语义）
            if (provided == null || provided.isBlank()) {
                throw new BizException(BizCode.PARAM_INVALID, "编码不能为空");
            }
            return provided.trim();
        }
        List<SegmentSpec> segments = parseSegments(rule.getSegments());
        if (provided == null || provided.isBlank()) {
            return generate(rule, segments, ctx);
        }
        String candidate = provided.trim();
        String regex = deriveRegex(segments);
        if (!Pattern.matches(regex, candidate)) {
            throw new BizException(BizCode.PARAM_INVALID,
                    "编码 \"" + candidate + "\" 不符合命名规则 \"" + rule.getRuleName()
                            + "\"（要求 " + regex + "）");
        }
        return candidate;
    }

    /** 生成编码：逐段渲染（SEQ 段分桶占号）→ 逐段长度校验 → 拼接。 */
    private String generate(CimNamingRule rule, List<SegmentSpec> segments, Map<String, String> ctx) {
        Map<String, String> safeCtx = ctx == null ? Map.of() : ctx;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            SegmentSpec seg = segments.get(i);
            String rendered;
            if (SegmentSpec.TYPE_SEQ.equals(seg.type())) {
                rendered = allocate(rule, i, seg, safeCtx);
            } else {
                rendered = registry.get(seg.type(), i).render(seg, safeCtx, null);
            }
            requireDeclaredLength(rule, i, seg, rendered);
            sb.append(rendered);
        }
        return sb.toString();
    }

    /** 逐段长度校验：声明了 length 的段，渲染结果必须等长（整码定长契约）。 */
    private static void requireDeclaredLength(CimNamingRule rule, int index,
                                              SegmentSpec seg, String rendered) {
        if (seg.length() != null && rendered.length() != seg.length()) {
            throw new BizException(BizCode.PARAM_INVALID,
                    "编码规则 \"" + rule.getRuleName() + "\" 第 " + (index + 1) + " 段 "
                            + seg.type() + " 声明长度 " + seg.length()
                            + "，实际渲染 \"" + rendered + "\"（" + rendered.length() + " 字符）");
        }
    }

    /** 占号：段级桶行 FOR UPDATE 递增；新桶首行插 start。只增不回收。 */
    private String allocate(CimNamingRule rule, int index, SegmentSpec seg, Map<String, String> ctx) {
        ObjectSpec spec = requireSpec(rule.getObjectType());
        String scopeKey = deriveScopeKey(seg.scope(), spec, ctx);
        long start = seg.start() == null ? 1 : seg.start();
        CimNamingCounter counter = counterRepository
                .lockForAllocate(rule.getId(), index, scopeKey)
                .orElseGet(() -> {
                    CimNamingCounter fresh = new CimNamingCounter();
                    fresh.setRuleId(rule.getId());
                    fresh.setSegmentIndex(index);
                    fresh.setScopeKey(scopeKey);
                    fresh.setCurrentValue(start);
                    return counterRepository.saveAndFlush(fresh);
                });
        long next = counter.getCurrentValue();
        counter.setCurrentValue(next + 1);
        counterRepository.saveAndFlush(counter);
        return ("%0" + seg.length() + "d").formatted(next);
    }

    /**
     * 分桶键派生（带口径前缀防撞）：GLOBAL='G:*' / BY_PARENT='P:父码'
     * （父变量取对象注册 parentVar，null 退化全局）/ BY_LOCATION='L:最具体位置码' /
     * BY_TYPE='T:类型' / BY_DATE='D:yyyyMMdd'（按天重置流水）。
     */
    private static String deriveScopeKey(String scope, ObjectSpec spec, Map<String, String> ctx) {
        Map<String, String> safeCtx = ctx == null ? Map.of() : ctx;
        return switch (scope == null ? SCOPE_GLOBAL : scope) {
            case SCOPE_GLOBAL -> "G:*";
            case SCOPE_BY_PARENT -> "P:" + (spec.parentVar() == null ? "*"
                    : blankToStar(safeCtx.get(spec.parentVar())));
            case SCOPE_BY_LOCATION -> "L:" + LOCATION_VARS.stream()
                    .map(safeCtx::get)
                    .filter(v -> v != null && !v.isBlank())
                    .reduce((a, b) -> b) // 优先序末位 = 最具体层（stage/bay/area/fab/site）
                    .map(NamingRuleAdminService::blankToStar).orElse("*");
            case SCOPE_BY_TYPE -> {
                String kind = safeCtx.get("kind");
                String type = safeCtx.get("type");
                yield "T:" + (kind == null && type == null ? "*"
                        : (kind == null ? "" : kind) + ":" + (type == null ? "" : type));
            }
            case SCOPE_BY_DATE -> "D:" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            default -> throw new BizException(BizCode.PARAM_INVALID, "未知的流水号分桶口径: " + scope);
        };
    }

    private static String blankToStar(String v) {
        return v == null || v.isBlank() ? "*" : v.trim();
    }

    // ---------------------------------------------------------------- 试算

    /** 试算请求（前端配置时逐段验证；ctx 缺的变量用占位符渲染并回列 missingVars）。 */
    public record PreviewReq(String objectType, List<SegmentSpec> segments,
                             Map<String, String> ctx) {
    }

    /** 单段试算结果。 */
    public record PreviewSegment(int index, String type, String rendered, boolean missing) {
    }

    /** 试算结果：逐段明细 + 示例编码 + 派生正则 + 缺失变量清单。 */
    public record PreviewRes(String example, String regex, List<String> missingVars,
                             List<PreviewSegment> segmentResults) {
    }

    /** 试算：不入库不占号（不碰计数器），纯段渲染。 */
    @Transactional(readOnly = true)
    public PreviewRes preview(PreviewReq req) {
        if (req.objectType() == null || req.objectType().isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, "objectType 不能为空");
        }
        ObjectSpec spec = requireSpec(req.objectType());
        if (req.segments() == null || req.segments().isEmpty()) {
            throw SegmentResolverRegistry.badSegment(null, -1, "段列表不能为空");
        }
        Map<String, String> safeCtx = req.ctx() == null ? Map.of() : req.ctx();
        List<String> missing = new ArrayList<>();
        List<PreviewSegment> results = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        StringBuilder re = new StringBuilder();
        for (int i = 0; i < req.segments().size(); i++) {
            SegmentSpec seg = req.segments().get(i);
            if (seg.type() == null || seg.type().isBlank()) {
                throw SegmentResolverRegistry.badSegment(null, i, "段类型 type 不能为空");
            }
            SegmentResolver resolver = registry.get(seg.type(), i);
            resolver.validate(seg, spec, i);
            String rendered;
            boolean isMissing = false;
            if (SegmentSpec.TYPE_SEQ.equals(seg.type())) {
                long start = seg.start() == null ? 1 : seg.start();
                rendered = ("%0" + seg.length() + "d").formatted(start);
            } else if ((SegmentSpec.TYPE_ENUM.equals(seg.type())
                    || SegmentSpec.TYPE_VAR.equals(seg.type()))
                    && (safeCtx.get(seg.var()) == null || safeCtx.get(seg.var()).isBlank())) {
                rendered = "<" + seg.var().toUpperCase() + ">";
                missing.add(seg.var());
                isMissing = true;
            } else {
                rendered = resolver.render(seg, safeCtx, null);
            }
            results.add(new PreviewSegment(i, seg.type(), rendered, isMissing));
            sb.append(rendered);
            re.append(resolver.regex(seg));
        }
        return new PreviewRes(sb.toString(), re.toString(), missing, results);
    }

    // ---------------------------------------------------------------- 计数器查询 / 复制

    /** 计数器只读视图（运维可见当前值；不提供重置——号段只增不回收）。 */
    public record NamingCounterView(Integer segmentIndex, String scopeKey, Long currentValue) {
    }

    @Transactional(readOnly = true)
    public List<NamingCounterView> listCounters(String ruleId) {
        requireRule(ruleId);
        return counterRepository.findByRuleIdAndDeletedFalse(ruleId).stream()
                .map(c -> new NamingCounterView(c.getSegmentIndex(), c.getScopeKey(), c.getCurrentValue()))
                .toList();
    }

    /** 复制规则：名称加「·副本」、默认停用、不带计数器（占号历史不复制）。 */
    @Transactional
    public NamingRuleView copy(String id) {
        CimNamingRule source = requireRule(id);
        CimNamingRule entity = new CimNamingRule();
        entity.setObjectType(source.getObjectType());
        entity.setRuleName(source.getRuleName() + "·副本");
        entity.setSegments(source.getSegments());
        entity.setEnabled(false);
        entity.setRemark(source.getRemark());
        entity.setDescription(source.getDescription());
        entity.syncActiveMarker();
        return toView(ruleRepository.save(entity));
    }

    private CimNamingRule requireRule(String id) {
        return ruleRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "编码规则不存在: " + id));
    }

    // ---------------------------------------------------------------- CRUD

    /** 规则视图（objectType 建后不可改，前端据此禁用）。 */
    public record NamingRuleView(String id, String objectType, String ruleName,
                                 List<SegmentSpec> segments, Boolean enabled,
                                 String remark, String description) {
    }

    /** 新建/更新请求（objectType 仅 create 吃）。 */
    public record NamingRuleReq(String objectType, String ruleName,
                                List<SegmentSpec> segments, Boolean enabled,
                                String remark, String description) {
    }

    @Transactional(readOnly = true)
    public List<NamingRuleView> list() {
        return ruleRepository.findAll().stream()
                .filter(r -> !Boolean.TRUE.equals(r.getDeleted()))
                .map(this::toView)
                .toList();
    }

    @Transactional
    public NamingRuleView create(NamingRuleReq req) {
        if (req.objectType() == null || req.objectType().isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, "objectType 不能为空");
        }
        requireSpec(req.objectType());
        requireText(req.ruleName(), "ruleName");
        if (req.segments() == null || req.segments().isEmpty()) {
            throw SegmentResolverRegistry.badSegment(null, -1, "段列表不能为空");
        }
        String segmentsJson = registry.toJson(req.segments());
        validateSegments(req.objectType(), segmentsJson);
        if (Boolean.TRUE.equals(req.enabled())
                && ruleRepository.findByObjectTypeAndEnabledTrueAndDeletedFalse(req.objectType()).isPresent()) {
            throw new BizException(BizCode.DATA_DUPLICATE,
                    "对象 " + req.objectType() + " 已有启用中的编码规则，先停用再启用新规则");
        }
        CimNamingRule entity = new CimNamingRule();
        entity.setObjectType(req.objectType().trim());
        applyReq(entity, req, segmentsJson);
        entity.setEnabled(req.enabled() == null || req.enabled());
        entity.syncActiveMarker();
        return toView(ruleRepository.save(entity));
    }

    @Transactional
    public NamingRuleView update(String id, NamingRuleReq req) {
        CimNamingRule entity = requireRule(id);
        requireText(req.ruleName(), "ruleName");
        if (req.segments() == null || req.segments().isEmpty()) {
            throw SegmentResolverRegistry.badSegment(null, -1, "段列表不能为空");
        }
        // objectType 建后不可改（计数器分桶与守门语义都锚定它）
        String segmentsJson = registry.toJson(req.segments());
        validateSegments(entity.getObjectType(), segmentsJson);
        boolean enabling = (req.enabled() == null || req.enabled())
                && !Boolean.TRUE.equals(entity.getEnabled());
        if (enabling && ruleRepository.findByObjectTypeAndEnabledTrueAndDeletedFalse(entity.getObjectType())
                .filter(r -> !r.getId().equals(entity.getId())).isPresent()) {
            throw new BizException(BizCode.DATA_DUPLICATE,
                    "对象 " + entity.getObjectType() + " 已有启用中的编码规则，先停用再启用新规则");
        }
        applyReq(entity, req, segmentsJson);
        entity.setEnabled(req.enabled() == null || req.enabled());
        entity.syncActiveMarker();
        return toView(ruleRepository.save(entity));
    }

    /** 删除 = 物理删（规则 + 计数器；软删启用行会撞 uk_cim_naming_rule_active，同矩阵开关口径）。 */
    @Transactional
    public void delete(String id) {
        CimNamingRule entity = requireRule(id);
        counterRepository.findByRuleIdAndDeletedFalse(entity.getId())
                .forEach(counterRepository::delete);
        ruleRepository.delete(entity);
    }

    private static void applyReq(CimNamingRule entity, NamingRuleReq req, String segmentsJson) {
        entity.setRuleName(req.ruleName().trim());
        entity.setSegments(segmentsJson);
        entity.setRemark(req.remark());
        entity.setDescription(req.description());
    }

    private static void requireText(String v, String field) {
        if (v == null || v.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, field + " 不能为空");
        }
    }

    private NamingRuleView toView(CimNamingRule r) {
        List<SegmentSpec> segments;
        try {
            segments = registry.parse(r.getSegments());
        } catch (Exception e) {
            // 库内 JSON 损坏时列表页整体不可用不可取——兜底空段并保留其余字段
            segments = List.of();
        }
        return new NamingRuleView(r.getId(), r.getObjectType(), r.getRuleName(), segments,
                r.getEnabled(), r.getRemark(), r.getDescription());
    }

    private final CimNamingRuleRepository ruleRepository;
    private final CimNamingCounterRepository counterRepository;
    private final SegmentResolverRegistry registry;
}
