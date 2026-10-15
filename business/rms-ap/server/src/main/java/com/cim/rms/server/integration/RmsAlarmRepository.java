package com.cim.rms.server.integration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Alarm 仓储。 */
public interface RmsAlarmRepository extends JpaRepository<RmsAlarm, String> {

    List<RmsAlarm> findByDeletedFalseOrderByCreateTimeDesc();

    List<RmsAlarm> findByAcknowledgedFalseAndDeletedFalse();

    long countByAcknowledgedFalseAndDeletedFalse();
}
