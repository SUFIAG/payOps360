package com.payops.shared.adapter.web.live;

import com.payops.shared.application.LiveEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-Sent Events fan-out, partitioned by tenant so an operator only ever
 * receives events of their own organisation. For multi-replica deployments
 * the publish side should be backed by a broker (Redis pub/sub, Kafka).
 */
@Slf4j
@Component
public class SseLiveEventPublisher implements LiveEventPublisher {

    private static final Duration TIMEOUT = Duration.ofMinutes(30);
    private static final int MAX_CONNECTIONS_PER_TENANT = 500;

    private final Map<Long, Set<SseEmitter>> emittersByTenant = new ConcurrentHashMap<>();

    public SseEmitter subscribe(long organizationId) {
        Set<SseEmitter> emitters = emittersByTenant.computeIfAbsent(organizationId, k -> ConcurrentHashMap.newKeySet());
        if (emitters.size() >= MAX_CONNECTIONS_PER_TENANT) {
            throw new IllegalStateException("Too many live connections");
        }
        SseEmitter emitter = new SseEmitter(TIMEOUT.toMillis());
        emitters.add(emitter);
        Runnable remove = () -> emitters.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        send(emitter, "connected", Map.of("at", Instant.now().toString()));
        return emitter;
    }

    @Override
    public void publish(long organizationId, String type, Object payload) {
        Set<SseEmitter> emitters = emittersByTenant.get(organizationId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        emitters.forEach(emitter -> {
            if (!send(emitter, type, payload)) {
                emitters.remove(emitter);
            }
        });
    }

    /** Keeps idle connections (and intermediaries) alive. */
    @Scheduled(fixedRate = 20_000)
    void heartbeat() {
        emittersByTenant.values().forEach(emitters -> emitters.removeIf(e -> !send(e, "heartbeat", Map.of())));
    }

    private boolean send(SseEmitter emitter, String type, Object payload) {
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name(type).data(payload, MediaType.APPLICATION_JSON));
            }
            return true;
        } catch (IOException | IllegalStateException e) {
            emitter.completeWithError(e);
            return false;
        }
    }
}
