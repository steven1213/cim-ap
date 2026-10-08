package com.cim.system.log;

import com.cim.jpa.support.BaseRepository;

import java.util.List;

/** 登录日志仓储（流水表）。 */
public interface LoginLogRepository extends BaseRepository<LoginLog, String> {

    /** 某账号的登录轨迹（按时间倒序）。 */
    List<LoginLog> findByUsernameOrderByEventTimeDesc(String username);
}
