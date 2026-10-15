package com.cim.rms.server.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RmsNotificationReadRepository extends JpaRepository<RmsNotificationRead, String> {

    List<RmsNotificationRead> findByUsernameAndNotificationIdInAndDeletedFalse(String username,
                                                                              List<String> notificationIds);

    Optional<RmsNotificationRead> findByNotificationIdAndUsernameAndDeletedFalse(String notificationId,
                                                                                 String username);

    List<RmsNotificationRead> findByUsernameAndDeletedFalse(String username);
}
