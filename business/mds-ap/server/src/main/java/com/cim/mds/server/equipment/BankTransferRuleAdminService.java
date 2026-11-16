package com.cim.mds.server.equipment;

import java.util.List;

import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bank 间转运规则管理——「允许从哪个 Bank 搬到哪个 Bank」的 allowlist 路由表。
 *
 * <p>行业口径（SEMI E88 6.1.1/6.1.2）：目的地分配与容量校验责任在 Host（MCS/MES），
 * 本表即 Host 侧路由主数据。守门：from≠to（自环无意义）、from/to 必须是真实存在的
 * Bank、from→to 唯一对（重复 409）。priority 沿 E88 Priority 1-99 口径（99 最高）。</p>
 */
@Service
@RequiredArgsConstructor
public class BankTransferRuleAdminService {

    private final CimBankTransferRuleRepository ruleRepository;
    private final CimStockerBankRepository bankRepository;

    // ------------------------------------------------------------------ 视图 / 请求

    public record BankTransferRuleView(String id, String fromBankId, String toBankId,
                                       Integer priority, Boolean enabled) {
    }

    public record BankTransferRuleReq(String fromBankId, String toBankId,
                                      Integer priority, Boolean enabled) {
    }

    // ------------------------------------------------------------------ 查询

    @Transactional(readOnly = true)
    public List<BankTransferRuleView> list(String bankId) {
        List<CimBankTransferRule> rules = blankToNull(bankId) == null
                ? ruleRepository.searchAll()
                : ruleRepository.findByBank(bankId);
        return rules.stream().map(BankTransferRuleAdminService::toView).toList();
    }

    // ------------------------------------------------------------------ 写

    @Transactional
    public BankTransferRuleView create(BankTransferRuleReq req) {
        requireText(req.fromBankId(), "fromBankId");
        requireText(req.toBankId(), "toBankId");
        String from = req.fromBankId().trim();
        String to = req.toBankId().trim();
        requireBank(from);
        requireBank(to);
        if (from.equals(to)) {
            throw new BizException(BizCode.PARAM_INVALID, "fromBank 与 toBank 不能相同");
        }
        if (ruleRepository.findByFromBankIdAndToBankIdAndDeletedFalse(from, to).isPresent()) {
            throw new BizException(BizCode.DATA_DUPLICATE,
                    "该转运规则已存在（from→to 唯一）: " + from + " → " + to);
        }
        CimBankTransferRule entity = new CimBankTransferRule();
        entity.setFromBankId(from);
        entity.setToBankId(to);
        entity.setPriority(req.priority());
        entity.setEnabled(req.enabled() == null || req.enabled());
        return toView(ruleRepository.save(entity));
    }

    @Transactional
    public BankTransferRuleView update(String id, BankTransferRuleReq req) {
        CimBankTransferRule entity = requireRule(id);
        // from/to 是路由标识，建后不可改（改指向应删旧建新，保持规则可追溯）
        entity.setPriority(req.priority());
        entity.setEnabled(req.enabled() == null || req.enabled());
        return toView(ruleRepository.save(entity));
    }

    @Transactional
    public void delete(String id) {
        CimBankTransferRule entity = requireRule(id);
        entity.setDeleted(Boolean.TRUE);
        ruleRepository.save(entity);
    }

    // ------------------------------------------------------------------ 内部

    private CimBankTransferRule requireRule(String id) {
        return ruleRepository.findById(id)
                .filter(r -> !Boolean.TRUE.equals(r.getDeleted()))
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "转运规则不存在: " + id));
    }

    private void requireBank(String bankId) {
        bankRepository.findById(bankId)
                .filter(b -> !Boolean.TRUE.equals(b.getDeleted()))
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "存储区不存在: " + bankId));
    }

    private static BankTransferRuleView toView(CimBankTransferRule r) {
        return new BankTransferRuleView(r.getId(), r.getFromBankId(), r.getToBankId(),
                r.getPriority(), r.getEnabled());
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, field + " 不能为空");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
