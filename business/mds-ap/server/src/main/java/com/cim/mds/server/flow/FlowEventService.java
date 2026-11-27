package com.cim.mds.server.flow;

import com.cim.core.port.IdGenerator;
import com.cim.mds.server.site.EditLockSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程域事件历史服务（V24，P3-2 拍板项 D）——动作级事件统一写入口 + 读端点数据源。
 *
 * <p><b>形态</b>：一张通用 {@code cim_flow_event_hist}（detail=变更摘要 JSON），不做 myCIM 式
 * 逐实体 Hist 全行快照——平台 {@code @History(SNAPSHOT)} 仅在 {@code AbstractJpaService} 写路径
 * 自动触发（MDS 服务直用 Repository 未走该基类），且全行快照对 {@code flow_xml} 大列成本过高。</p>
 *
 * <p><b>写点清单</b>（历史失败只告警不阻断主流程）：</p>
 * <ul>
 *   <li>生命周期：{@code CREATE/COPY/UPDATE/DELETE/FREEZE/UNFREEZE/ACTIVATE/DISABLE}
 *       （FlowAdminService 对应方法）</li>
 *   <li>画布与解析产物：{@code SAVE_XML}（保存画布含节点/边/Q-time 重建）、
 *       {@code REFRESH_OP}（刷新工序引用）</li>
 *   <li>覆盖数据：{@code ASSIGN_REPLACE/ATTR_REPLACE}（指派/KV 增删改）</li>
 *   <li>批量导入：{@code IMPORT}（Excel 三段导入 per 流程）</li>
 * </ul>
 *
 * <p>事件主体类型（entity_type）：FLOW/ASSIGN/ATTR/QTIME/STEP；OPERATION 层指派/参数无
 * 流程锚点，不落本表（操作历史归工序域）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowEventService {

    // ---- entity_type 词表 ----
    public static final String T_FLOW = "FLOW";
    public static final String T_ASSIGN = "ASSIGN";
    public static final String T_ATTR = "ATTR";
    public static final String T_QTIME = "QTIME";
    public static final String T_STEP = "STEP";

    /** 单流程事件读取上限（时间倒序截断；审计量有界）。 */
    public static final int MAX_EVENTS = 200;

    private final FlowEventHistRepository eventRepository;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    /** 读端点视图（实体直出已够用，不另做投影——字段全量返回）。 */
    public List<CimFlowEventHist> listByFlow(String flowId) {
        return eventRepository.findTop200ByFlowIdOrderByEventTimeDescIdDesc(flowId);
    }

    /**
     * 记录一条事件（detail=摘要 JSON；任何失败只告警，不阻断业务主流程）。
     */
    public void record(String flowId, String flowCode, String entityType, String action, Object detail) {
        try {
            CimFlowEventHist e = new CimFlowEventHist();
            e.setId(idGenerator.nextId());
            e.setFlowId(flowId);
            e.setFlowCode(flowCode);
            e.setEntityType(entityType);
            e.setAction(action);
            String user = EditLockSupport.currentUser();
            e.setActor(user != null ? user : "system");
            e.setDetail(toJson(detail));
            e.setEventTime(LocalDateTime.now());
            eventRepository.save(e);
        } catch (Exception ex) {
            log.warn("flow event record failed: flow={} action={}", flowId, action, ex);
        }
    }

    /** 摘要 JSON 序列化（保持键序，紧凑输出）。 */
    private String toJson(Object detail) {
        if (detail == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (Exception ex) {
            log.warn("flow event detail serialize failed", ex);
            return String.valueOf(detail);
        }
    }

    /** 快捷摘要构造（LinkedHashMap 保持键序，避免无序 JSON 噪音）。 */
    public static Map<String, Object> summary(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
