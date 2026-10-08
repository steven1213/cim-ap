package com.cim.system.log;

import com.cim.core.model.BaseEventData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 登录日志（design.md §2.10 / plan.md T6.4）——流水表，无历史表。
 *
 * <p><b>写入方</b>：登录/登出发生在 {@code business/iam-ap}（IAM 负责签发与吊销），
 * 本表的记录动作由 IAM 或本 ap 的审计网关调用 {@code LoginLogService} 落库；
 * 本模块只提供<b>存储与查询</b>，不参与认证判定。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "sys_login_log")
public class LoginLog extends BaseEventData {

    /** 登录账号。 */
    @Column(name = "username", length = 64)
    private String username;

    /** 类型：{@code LOGIN} / {@code LOGOUT}。 */
    @Column(name = "login_type", length = 32)
    private String loginType;

    /** 是否成功。 */
    @Column(name = "success", nullable = false)
    private Boolean success = Boolean.TRUE;

    /** 失败原因。 */
    @Column(name = "fail_reason", length = 256)
    private String failReason;

    /** 客户端 IP。 */
    @Column(name = "client_ip", length = 64)
    private String clientIp;

    /** 客户端标识（UA）。 */
    @Column(name = "user_agent", length = 512)
    private String userAgent;
}
