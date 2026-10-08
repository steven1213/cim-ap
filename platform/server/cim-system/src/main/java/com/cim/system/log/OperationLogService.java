package com.cim.system.log;

import com.cim.core.shared.PageResult;
import com.cim.spring.support.web.PageQuery;
import com.cim.spring.support.web.PageResults;
import com.cim.system.support.PermissionCodes;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 操作日志服务。
 *
 * <p>不继承 {@code AbstractJpaService}：{@link OperationLog} 是 {@code BaseEventData} 流水，
 * 无「定义 / 逻辑删除 / 历史」语义，强套基类会引入无意义的 version/deleted 列。</p>
 *
 * <p>写入 {@link #record(OperationLog)} <b>不加鉴权</b>——它由切面在任意业务方法内调用；
 * 查询 {@link #page(PageQuery)} 才需要 {@code sys:log:list}。</p>
 */
@Service
public class OperationLogService {

    private final OperationLogRepository repository;

    public OperationLogService(OperationLogRepository repository) {
        this.repository = repository;
    }

    /** 落一条操作日志。 */
    @Transactional
    public OperationLog record(OperationLog log) {
        if (log.getSuccess() == null) {
            log.setSuccess(Boolean.TRUE);
        }
        return repository.save(log);
    }

    /** 分页查询操作日志。 */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + PermissionCodes.LOG_LIST + "')")
    public PageResult<OperationLog> page(PageQuery query) {
        return PageResults.from(repository.findAll(query.toPageable()));
    }

    /** 某业务键的操作轨迹。 */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + PermissionCodes.LOG_LIST + "')")
    public List<OperationLog> byBizKey(String bizKey) {
        return repository.findByBizKeyOrderByEventTimeDesc(bizKey);
    }
}
