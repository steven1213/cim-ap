package com.cim.system.user;

import com.cim.core.history.Historizable;
import com.cim.core.model.BaseHistoryData;
import com.cim.system.support.EnableStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * {@link SysUser} 的整行快照历史表（{@code sys_user_hist}）。
 *
 * <p>与主实体**同包同名 + {@code Hist} 后缀**、业务字段**同构**，由 {@code HistoryMapper}
 * 反射解析并拷贝，零样板（见 README §9）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "sys_user_hist")
public class SysUserHist extends BaseHistoryData implements Historizable {

    @Column(name = "username", length = 64)
    private String username;

    @Column(name = "external_id", length = 128)
    private String externalId;

    @Column(name = "display_name", length = 128)
    private String displayName;

    @Column(name = "email", length = 128)
    private String email;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "lang", length = 16)
    private String lang;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16)
    private EnableStatus status;
}
