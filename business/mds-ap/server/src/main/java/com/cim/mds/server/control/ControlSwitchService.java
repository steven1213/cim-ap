package com.cim.mds.server.control;

import java.util.List;

import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 卡控开关服务（V18）——按域批量查询 + 启停切换。
 *
 * <p>启停语义：{@code enabled} 只在 true/false 间翻转，不删行（消费方按 code 引用，
 * 行是长期事实源）。查询按 {@code domain_code} 批量——消费方（MES）按 TTL 周期拉取
 * 落本地缓存，本服务不可达时消费方沿用最近缓存值，不在请求链路上强依赖本服务。</p>
 */
@Service
public class ControlSwitchService {

    private final CimControlSwitchRepository repository;

    public ControlSwitchService(CimControlSwitchRepository repository) {
        this.repository = repository;
    }

    // ================================================================ 视图

    /** 开关行。 */
    public record SwitchView(String id, String controlCode, String domainCode, String name,
            boolean enabled, String description) {
    }

    // ================================================================ 查询

    /** 按域批量（domainCode 空 = 全量，跨域管理口径）。 */
    @Transactional(readOnly = true)
    public List<SwitchView> list(String domainCode) {
        List<CimControlSwitch> rows = (domainCode == null || domainCode.isBlank())
                ? repository.findByDeletedFalseOrderByDomainCodeAscControlCodeAsc()
                : repository.findByDomainCodeAndDeletedFalseOrderByControlCodeAsc(domainCode);
        return rows.stream().map(ControlSwitchService::toView).toList();
    }

    // ================================================================ 启停

    /** 启停切换（code 不存在 2001；enabled 与现值相同直接返回，幂等）。 */
    @Transactional
    public SwitchView setEnabled(String controlCode, boolean enabled) {
        CimControlSwitch e = repository.findByControlCodeAndDeletedFalse(controlCode)
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND,
                        "卡控开关不存在: " + controlCode));
        e.setEnabled(enabled);
        return toView(repository.save(e));
    }

    // ================================================================ 内部

    private static SwitchView toView(CimControlSwitch e) {
        return new SwitchView(e.getId(), e.getControlCode(), e.getDomainCode(), e.getName(),
                Boolean.TRUE.equals(e.getEnabled()), e.getDescription());
    }
}
