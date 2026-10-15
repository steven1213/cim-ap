package com.cim.rms.server.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RmsNotificationRepository extends JpaRepository<RmsNotification, String> {

    /**
     * 当前用户可见的通知：指定收件人是自己 + 无收件人的全员广播 + 命中自己权限的受众广播。
     * 受众是否命中由调用方传布尔（有 rms:signoff:approve / rms:integration:operate）。
     */
    @Query("""
            select n from RmsNotification n
            where n.deleted = false
              and ( n.recipient = :me
                 or (n.recipient is null and n.audience is null)
                 or (n.recipient is null and n.audience = 'APPROVER' and :approver = true)
                 or (n.recipient is null and n.audience = 'INTEGRATION' and :integrator = true) )
            order by n.occurredAt desc, n.createTime desc
            """)
    List<RmsNotification> findVisible(@Param("me") String me,
                                      @Param("approver") boolean approver,
                                      @Param("integrator") boolean integrator);
}
