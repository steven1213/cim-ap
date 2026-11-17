package com.cim.mds.server.reason;

import java.time.LocalDateTime;
import java.util.List;

import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * WIP 原因事件（V16 示例域）服务——事件创建 + 履历查询。
 *
 * <p><b>快照口径</b>：创建事件时把原因码当时的五个关键属性（编码/语义域/树路径/
 * 归属部门/短名）一次性固化到事件行；此后主数据的任何演进（改名、挪部门、挪树位、
 * 停用）都不回写历史。新事务选码守门：码必须启用，且码的 {@code type_key} 必须与
 * 事务类型一致（Hold 事件只能选 HOLD 域的码）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WipReasonEventService {

    private final CimWipReasonEventRepository eventRepository;
    private final CimReasonCodeRepository codeRepository;
    private final CimReasonCodeTypeRepository typeRepository;

    // ---------------------------------------------------------------- 视图 / 请求

    /** 事件视图（展示全部走快照字段）。 */
    public record EventView(String id, String lotId, String waferId, String operationCode,
                            String equipmentCode, String eventType, String reasonId,
                            LocalDateTime occurredAt, String operator,
                            String reasonCodeSnapshot, String reasonTypeSnapshot,
                            String reasonPathSnapshot, String owningDeptSnapshot,
                            String reasonNameSnapshot) {
    }

    /** 事件创建请求（occurredAt 空 = 当前时刻；waferId/operationCode/equipmentCode 可空）。 */
    public record EventCreateReq(String lotId, String waferId, String operationCode,
                                 String equipmentCode, String eventType, String reasonId,
                                 LocalDateTime occurredAt, String operator) {
    }

    // ---------------------------------------------------------------- 查询

    @Transactional(readOnly = true)
    public List<EventView> list(String lotId) {
        List<CimWipReasonEvent> events = (lotId == null || lotId.isBlank())
                ? eventRepository.findByDeletedFalseOrderByOccurredAtDescIdDesc()
                : eventRepository.findByLotIdAndDeletedFalseOrderByOccurredAtDescIdDesc(lotId.trim());
        return events.stream().map(this::toView).toList();
    }

    // ---------------------------------------------------------------- 创建

    @Transactional
    public EventView create(EventCreateReq req) {
        String lotId = requireText(req.lotId(), "lotId");
        String eventType = requireText(req.eventType(), "eventType");
        // 事务类型须是已登记的语义域（类型表为准，不钉死 8 类——开放维护口径）
        typeRepository.findByTypeKeyAndDeletedFalse(eventType)
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "原因码类型不存在: " + eventType));
        CimReasonCode reason = codeRepository.findByIdAndDeletedFalse(req.reasonId())
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "原因码不存在"));
        if (!Boolean.TRUE.equals(reason.getEnabled())) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "原因码已停用，不可用于新事务: " + reason.getReasonCode());
        }
        if (!eventType.equals(reason.getTypeKey())) {
            throw new BizException(BizCode.PARAM_INVALID,
                    "原因码语义域不匹配（事件 " + eventType + " / 码 " + reason.getTypeKey() + "）");
        }
        CimWipReasonEvent event = new CimWipReasonEvent();
        event.setLotId(lotId);
        event.setWaferId(blankToNull(req.waferId()));
        event.setOperationCode(blankToNull(req.operationCode()));
        event.setEquipmentCode(blankToNull(req.equipmentCode()));
        event.setEventType(eventType);
        event.setReasonId(reason.getId());
        event.setOccurredAt(req.occurredAt() == null ? LocalDateTime.now() : req.occurredAt());
        event.setOperator(blankToNull(req.operator()));
        // 快照五件套：固化事件发生时刻的主数据视图（不随后续演进变化）
        event.setReasonCodeSnapshot(reason.getReasonCode());
        event.setReasonTypeSnapshot(reason.getTypeKey());
        event.setReasonPathSnapshot(reason.getNodePath());
        event.setOwningDeptSnapshot(reason.getOwningDept());
        event.setReasonNameSnapshot(reason.getShortName());
        return toView(eventRepository.save(event));
    }

    // ---------------------------------------------------------------- 内部

    private EventView toView(CimWipReasonEvent e) {
        return new EventView(e.getId(), e.getLotId(), e.getWaferId(), e.getOperationCode(),
                e.getEquipmentCode(), e.getEventType(), e.getReasonId(), e.getOccurredAt(),
                e.getOperator(), e.getReasonCodeSnapshot(), e.getReasonTypeSnapshot(),
                e.getReasonPathSnapshot(), e.getOwningDeptSnapshot(), e.getReasonNameSnapshot());
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, field + " 不能为空");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
