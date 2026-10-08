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
 * 登录日志服务——本模块只提供<b>存储与查询</b>，不参与认证判定
 * （登录发生在 {@code business/iam-ap}，由 IAM 或审计网关注入调用 {@link #record(LoginLog)}）。
 */
@Service
public class LoginLogService {

    private final LoginLogRepository repository;

    public LoginLogService(LoginLogRepository repository) {
        this.repository = repository;
    }

    /** 落一条登录/登出日志。 */
    @Transactional
    public LoginLog record(LoginLog log) {
        if (log.getSuccess() == null) {
            log.setSuccess(Boolean.TRUE);
        }
        return repository.save(log);
    }

    /** 分页查询登录日志。 */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + PermissionCodes.LOG_LIST + "')")
    public PageResult<LoginLog> page(PageQuery query) {
        return PageResults.from(repository.findAll(query.toPageable()));
    }

    /** 某账号的登录轨迹。 */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + PermissionCodes.LOG_LIST + "')")
    public List<LoginLog> byUsername(String username) {
        return repository.findByUsernameOrderByEventTimeDesc(username);
    }
}
