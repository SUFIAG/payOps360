package com.payops.shared.adapter.web.live;

import com.payops.shared.adapter.web.AuthenticatedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Live stream")
@RestController
@RequiredArgsConstructor
class LiveStreamController {

    private final SseLiveEventPublisher publisher;

    /**
     * Real-time feed of payments, alerts, incidents and provider health for the
     * caller's organisation. Authenticated with the usual Bearer token.
     */
    @GetMapping(path = "/api/v1/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(@AuthenticationPrincipal AuthenticatedUser user) {
        return publisher.subscribe(user.organizationId());
    }
}
