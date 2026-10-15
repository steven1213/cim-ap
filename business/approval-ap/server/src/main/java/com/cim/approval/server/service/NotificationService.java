package com.cim.approval.server.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实时通知：基于 SSE 的内存级推送（演示/单实例）。
 *
 * <p>生产多实例部署应替换为 Redis Pub/Sub 或消息队列广播；此处用内存 Map 维持 userId→SseEmitter 映射，
 * 由 {@code NotificationEventListener} 在任务创建/改派时主动推送。</p>
 */
@Service
public class NotificationService {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String userId) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        emitter.onCompletion(() -> emitters.remove(userId, emitter));
        emitter.onTimeout(() -> emitters.remove(userId, emitter));
        emitters.put(userId, emitter);
        return emitter;
    }

    public void pushToUser(String userId, String eventType, Object payload) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(eventType).data(payload));
        } catch (IOException e) {
            emitters.remove(userId, emitter);
        }
    }

    public void pushEvent(Map<String, Object> event) {
        Object user = event.get("userId");
        if (user != null) {
            pushToUser(String.valueOf(user), String.valueOf(event.get("type")), event);
        }
    }
}
