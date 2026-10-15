package com.cim.rms.server.authz;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 用户↔设备授权仓储。 */
public interface UserDeviceAuthRepository extends JpaRepository<UserDeviceAuth, String> {

    List<UserDeviceAuth> findBySubjectAndDeletedFalseOrderByCreateTimeDesc(String subject);

    List<UserDeviceAuth> findByDeletedFalseOrderByCreateTimeDesc();

    long countByDeletedFalse();
}
