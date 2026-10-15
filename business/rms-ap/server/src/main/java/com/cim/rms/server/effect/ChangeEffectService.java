package com.cim.rms.server.effect;

import com.cim.core.port.IdGenerator;
import com.cim.rms.server.audit.RmsAuditService;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 变更效果登记服务（W11）：{@link #record} 由「版本生效」两侧挂钩调用
 * （签核批准自动激活 / 手动激活）；登记失败只记日志，不拖垮激活主事务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeEffectService {

    private final RmsChangeEffectRepository repository;
    private final IdGenerator idGenerator;

    /** 激活时登记一行变更效果。任何异常只记日志（登记是增强，不是闸门）。 */
    public void record(String recipeId, String recipeCode, String fromVersionId, Integer fromVersionNo,
                       String toVersionId, Integer toVersionNo, int paramDiffCount, String changeSummary) {
        try {
            RmsChangeEffect e = new RmsChangeEffect();
            e.setRecipeId(recipeId);
            e.setRecipeCode(recipeCode);
            e.setFromVersionId(fromVersionId);
            e.setFromVersionNo(fromVersionNo);
            e.setToVersionId(toVersionId);
            e.setToVersionNo(toVersionNo);
            e.setActivatedBy(RmsAuditService.currentActor());
            e.setActivatedAt(Instant.now());
            e.setParamDiffCount(paramDiffCount);
            e.setChangeSummary(changeSummary);
            repository.save(e);
        } catch (Exception ex) {
            log.warn("[effect] 变更效果登记失败 recipe={} err={}", recipeCode, ex.toString());
        }
    }

    /** 配方的变更历史（时间倒序）。 */
    public List<EffectView> list(String recipeId) {
        if (recipeId == null || recipeId.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, "recipeId 不能为空");
        }
        List<RmsChangeEffect> rows = repository.findByRecipeIdAndDeletedFalseOrderByActivatedAtDesc(recipeId);
        List<EffectView> out = new ArrayList<>();
        for (RmsChangeEffect e : rows) {
            out.add(new EffectView(e.getId(), e.getRecipeId(), e.getRecipeCode(),
                    e.getFromVersionId(), e.getFromVersionNo(), e.getToVersionId(), e.getToVersionNo(),
                    e.getActivatedBy(), fmt(e.getActivatedAt()), e.getParamDiffCount(),
                    e.getChangeSummary(), e.getSpcRef()));
        }
        return out;
    }

    private static String fmt(Instant t) {
        return t == null ? null
                : LocalDateTime.ofInstant(t, ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public record EffectView(String id, String recipeId, String recipeCode,
                             String fromVersionId, Integer fromVersionNo,
                             String toVersionId, Integer toVersionNo,
                             String activatedBy, String activatedAt, int paramDiffCount,
                             String changeSummary, String spcRef) {
    }
}
