package com.cim.approval.server.repository;

import com.cim.approval.server.domain.CallbackOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface CallbackOutboxRepository extends JpaRepository<CallbackOutbox, String> {

    /** 拉取待重试且已过重试时间的记录（可靠投递调度器用）。 */
    @Query("select o from CallbackOutbox o where o.status = com.cim.approval.server.domain.CallbackOutbox.Status.PENDING "
            + "and o.nextRetryAt <= :now")
    List<CallbackOutbox> findDue(@Param("now") Instant now);

    List<CallbackOutbox> findByAppCodeOrderByCreatedAtAsc(String appCode);

    List<CallbackOutbox> findByBusinessKeyOrderByCreatedAtAsc(String businessKey);

    java.util.Optional<CallbackOutbox> findByMessageId(String messageId);

    List<CallbackOutbox> findByBusinessKey(String businessKey);

    List<CallbackOutbox> findByAppCode(String appCode);
    long countByStatus(com.cim.approval.server.domain.CallbackOutbox.Status status);
}
