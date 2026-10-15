package com.cim.rms.server.notification;

import com.cim.core.port.IdGenerator;
import com.cim.rms.server.security.RmsPermissions;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 站内通知服务（W9 审批实时通知）。
 *
 * <p><b>发布</b>：{@link #publish} 落库后遍历全部 {@link NotifyChannel} 渠道投递；
 * 任一渠道失败只记日志，<b>绝不</b>让通知失败拖垮业务事务（签核提交/审批是主事务）。</p>
 *
 * <p><b>可见性</b>：按「指定收件人 / 全员广播 / 权限受众广播」三类过滤（见仓储查询）；
 * 受众是否命中由调用方以当前用户权限判定（{@code rms:signoff:approve} / {@code rms:integration:operate}）。</p>
 *
 * <p><b>已读</b>：per-user 登记（{@link RmsNotificationRead}），广播类通知互不影响。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final RmsNotificationRepository notificationRepository;
    private final RmsNotificationReadRepository readRepository;
    private final List<NotifyChannel> channels;
    private final IdGenerator idGenerator;

    /** 可见条目上限（通知中心单次拉取；更旧的靠 read-all 兜底）。 */
    private static final int VISIBLE_LIMIT = 200;

    // ==================== 发布（业务侧挂钩调用） ====================

    /**
     * 发布一条通知。{@code recipient}（到人）与 {@code audience}（APPROVER/INTEGRATION 受众广播）
     * 至少给一个，都空则为全员广播。任何异常只记日志不外抛。
     */
    public void publish(String type, String title, String content,
                        String recipient, String audience, String refType, String refId) {
        try {
            RmsNotification n = new RmsNotification();
            n.setType(type);
            n.setTitle(title);
            n.setContent(content);
            n.setRecipient(recipient);
            n.setAudience(audience);
            n.setRefType(refType);
            n.setRefId(refId);
            n.setOccurredAt(Instant.now());
            RmsNotification saved = notificationRepository.save(n);
            for (NotifyChannel ch : channels) {
                try {
                    ch.deliver(saved);
                } catch (Exception e) {
                    log.warn("[notify] 渠道 {} 投递失败（不影响业务）: {}", ch.name(), e.toString());
                }
            }
        } catch (Exception e) {
            log.warn("[notify] 通知发布失败 type={} err={}", type, e.toString());
        }
    }

    // ==================== 拉取（通知中心） ====================

    /** 当前用户可见的通知（含已读标记），时间倒序，上限 {@value VISIBLE_LIMIT} 条。 */
    public NotificationPage visible(String me, boolean approver, boolean integrator, boolean unreadOnly) {
        List<RmsNotification> rows = notificationRepository.findVisible(me, approver, integrator);
        Set<String> readIds = readRepository.findByUsernameAndDeletedFalse(me).stream()
                .map(RmsNotificationRead::getNotificationId)
                .collect(Collectors.toSet());
        List<NotificationView> items = new ArrayList<>();
        int unread = 0;
        for (RmsNotification n : rows) {
            if (items.size() >= VISIBLE_LIMIT) {
                break;
            }
            boolean read = readIds.contains(n.getId());
            if (!read) {
                unread++;
            }
            if (!unreadOnly || !read) {
                items.add(toView(n, read));
            }
        }
        return new NotificationPage(items, unread, rows.size() > VISIBLE_LIMIT);
    }

    /** 标记单条已读（幂等）。 */
    @Transactional
    public void markRead(String id, String username) {
        RmsNotification n = notificationRepository.findById(id).orElse(null);
        if (n == null || Boolean.TRUE.equals(n.getDeleted())) {
            return; // 不存在/已删：静默（通知是易失数据，403/404 无意义）
        }
        if (readRepository.findByNotificationIdAndUsernameAndDeletedFalse(id, username).isPresent()) {
            return;
        }
        RmsNotificationRead r = new RmsNotificationRead();
        r.setNotificationId(id);
        r.setUsername(username);
        r.setReadAt(Instant.now());
        readRepository.save(r);
    }

    /** 全部已读（当前可见范围内逐条登记）。 */
    @Transactional
    public int markAllRead(String me, boolean approver, boolean integrator) {
        List<RmsNotification> rows = notificationRepository.findVisible(me, approver, integrator);
        Set<String> already = readRepository.findByUsernameAndDeletedFalse(me).stream()
                .map(RmsNotificationRead::getNotificationId)
                .collect(Collectors.toCollection(HashSet::new));
        int marked = 0;
        for (RmsNotification n : rows) {
            if (already.contains(n.getId())) {
                continue;
            }
            RmsNotificationRead r = new RmsNotificationRead();
            r.setNotificationId(n.getId());
            r.setUsername(me);
            r.setReadAt(Instant.now());
            readRepository.save(r);
            marked++;
        }
        return marked;
    }

    // ==================== 视图 ====================

    private static NotificationView toView(RmsNotification n, boolean read) {
        return new NotificationView(n.getId(), n.getType(), n.getTitle(), n.getContent(),
                n.getRecipient(), n.getAudience(), n.getRefType(), n.getRefId(), read,
                fmt(n.getOccurredAt()));
    }

    private static String fmt(Instant t) {
        return t == null ? null
                : java.time.LocalDateTime.ofInstant(t, ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public record NotificationView(String id, String type, String title, String content,
                                   String recipient, String audience, String refType, String refId,
                                   boolean read, String occurredAt) {
    }

    public record NotificationPage(List<NotificationView> items, int unread, boolean truncated) {
    }

    /** 供权限过滤使用的受众→权限码映射（与前端 permCodes 对齐）。 */
    public static final Map<String, String> AUDIENCE_PERMS = Map.of(
            "APPROVER", RmsPermissions.SIGNOFF_APPROVE,
            "INTEGRATION", RmsPermissions.INTEGRATION_OPERATE);
}
