package com.payops.incident.adapter.in.web;

import com.payops.incident.application.port.in.IncidentCommandUseCase;
import com.payops.incident.application.port.in.IncidentQueryUseCase;
import com.payops.incident.application.port.in.IncidentView;
import com.payops.incident.application.port.in.IncidentSlaUseCase;
import com.payops.incident.application.port.in.PostmortemUseCase;
import com.payops.incident.domain.IncidentStatus;
import com.payops.incident.domain.Postmortem;
import com.payops.incident.domain.SlaPolicy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.PutMapping;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import com.payops.shared.adapter.web.ApiResponse;
import com.payops.shared.adapter.web.AuthenticatedUser;
import com.payops.shared.adapter.web.ClientInfo;
import com.payops.shared.adapter.web.PageResponse;
import com.payops.shared.application.RequestContext;
import com.payops.shared.domain.PageQuery;
import com.payops.shared.domain.Severity;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Incidents")
@RestController
@RequestMapping("/api/v1/incidents")
@RequiredArgsConstructor
class IncidentController {

    private final IncidentQueryUseCase queries;
    private final IncidentCommandUseCase commands;
    private final IncidentSlaUseCase sla;
    private final PostmortemUseCase postmortems;

    // ------------------------------------------------------------ SLA targets

    @GetMapping("/sla-targets")
    @PreAuthorize("hasAuthority('INCIDENT_READ')")
    ApiResponse<List<IncidentSlaUseCase.SlaTargetView>> slaTargets(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(sla.slaTargets(user.organizationId()));
    }

    @PutMapping("/sla-targets")
    @PreAuthorize("hasAuthority('NOTIFICATION_MANAGE')")
    ApiResponse<List<IncidentSlaUseCase.SlaTargetView>> updateSlaTargets(@AuthenticationPrincipal AuthenticatedUser user,
                                                                        @Valid @RequestBody SlaTargetsRequest request,
                                                                        HttpServletRequest http) {
        Map<Severity, SlaPolicy.Target> targets = new EnumMap<>(Severity.class);
        request.targets().forEach(t -> targets.put(t.severity(), new SlaPolicy.Target(t.ackMinutes(), t.resolveMinutes())));
        return ApiResponse.ok(sla.updateSlaTargets(user.actor(), targets, context(http)));
    }

    // ------------------------------------------------------------ postmortem

    @GetMapping("/{reference}/postmortem")
    @PreAuthorize("hasAuthority('INCIDENT_READ')")
    ApiResponse<PostmortemUseCase.PostmortemView> postmortem(@AuthenticationPrincipal AuthenticatedUser user,
                                                             @PathVariable String reference) {
        return ApiResponse.ok(postmortems.get(user.organizationId(), reference));
    }

    @PutMapping("/{reference}/postmortem")
    @PreAuthorize("hasAuthority('INCIDENT_MANAGE')")
    ApiResponse<PostmortemUseCase.PostmortemView> savePostmortem(@AuthenticationPrincipal AuthenticatedUser user,
                                                                 @PathVariable String reference,
                                                                 @Valid @RequestBody PostmortemRequest request,
                                                                 HttpServletRequest http) {
        return ApiResponse.ok(postmortems.save(user.actor(), reference, request.toContent(), context(http)));
    }

    @PostMapping("/{reference}/postmortem/publish")
    @PreAuthorize("hasAuthority('INCIDENT_MANAGE')")
    ApiResponse<PostmortemUseCase.PostmortemView> publishPostmortem(@AuthenticationPrincipal AuthenticatedUser user,
                                                                    @PathVariable String reference,
                                                                    HttpServletRequest http) {
        return ApiResponse.ok(postmortems.publish(user.actor(), reference, context(http)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('INCIDENT_READ')")
    ApiResponse<PageResponse<IncidentView>> list(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @RequestParam(required = false) IncidentStatus status,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "25") int size) {
        return ApiResponse.ok(PageResponse.from(queries.list(user.organizationId(), status, PageQuery.of(page, size)), v -> v));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('INCIDENT_READ')")
    ApiResponse<IncidentQueryUseCase.Summary> summary(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(queries.summary(user.organizationId()));
    }

    @GetMapping("/{reference}")
    @PreAuthorize("hasAuthority('INCIDENT_READ')")
    ApiResponse<IncidentQueryUseCase.IncidentDetails> get(@AuthenticationPrincipal AuthenticatedUser user,
                                                          @PathVariable String reference) {
        return ApiResponse.ok(queries.get(user.organizationId(), reference));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('INCIDENT_MANAGE')")
    ApiResponse<IncidentView> declare(@AuthenticationPrincipal AuthenticatedUser user,
                                      @Valid @RequestBody DeclareRequest request, HttpServletRequest http) {
        return ApiResponse.ok(commands.declare(user.actor(), request.title(), request.description(), request.severity(),
                request.providerCode(), context(http)));
    }

    @PostMapping("/{reference}/acknowledge")
    @PreAuthorize("hasAuthority('INCIDENT_MANAGE')")
    ApiResponse<IncidentView> acknowledge(@AuthenticationPrincipal AuthenticatedUser user,
                                          @PathVariable String reference, HttpServletRequest http) {
        return ApiResponse.ok(commands.acknowledge(user.actor(), reference, context(http)));
    }

    @PostMapping("/{reference}/investigate")
    @PreAuthorize("hasAuthority('INCIDENT_MANAGE')")
    ApiResponse<IncidentView> investigate(@AuthenticationPrincipal AuthenticatedUser user,
                                          @PathVariable String reference, HttpServletRequest http) {
        return ApiResponse.ok(commands.investigate(user.actor(), reference, context(http)));
    }

    @PostMapping("/{reference}/escalate")
    @PreAuthorize("hasAuthority('INCIDENT_MANAGE')")
    ApiResponse<IncidentView> escalate(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String reference,
                                       @Valid @RequestBody EscalateRequest request, HttpServletRequest http) {
        return ApiResponse.ok(commands.escalate(user.actor(), reference, request.severity(), context(http)));
    }

    @PostMapping("/{reference}/resolve")
    @PreAuthorize("hasAuthority('INCIDENT_MANAGE')")
    ApiResponse<IncidentView> resolve(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String reference,
                                      @Valid @RequestBody NoteRequest request, HttpServletRequest http) {
        return ApiResponse.ok(commands.resolve(user.actor(), reference, request.note(), context(http)));
    }

    @PostMapping("/{reference}/notes")
    @PreAuthorize("hasAuthority('INCIDENT_MANAGE')")
    ApiResponse<IncidentView> addNote(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String reference,
                                      @Valid @RequestBody NoteRequest request) {
        return ApiResponse.ok(commands.addNote(user.actor(), reference, request.note()));
    }

    private static RequestContext context(HttpServletRequest request) {
        ClientInfo info = ClientInfo.from(request);
        return new RequestContext(info.ipAddress(), info.userAgent());
    }

    record DeclareRequest(@NotBlank @Size(max = 300) String title, @Size(max = 4000) String description,
                          @NotNull Severity severity, @Size(max = 60) String providerCode) {
    }

    record EscalateRequest(@NotNull Severity severity) {
    }

    record NoteRequest(@NotBlank @Size(max = 2000) String note) {
    }

    record SlaTargetRequest(@NotNull Severity severity, @Min(1) @Max(10_080) int ackMinutes,
                            @Min(1) @Max(43_200) int resolveMinutes) {
    }

    record SlaTargetsRequest(@NotNull @Size(min = 1, max = 4) List<@Valid @NotNull SlaTargetRequest> targets) {
    }

    record TimelineItemRequest(@NotNull Instant at, @NotBlank @Size(max = 2000) String text) {
    }

    record ActionItemRequest(@NotBlank @Size(max = 1000) String text, @Size(max = 320) String owner,
                             LocalDate dueDate, boolean done) {
    }

    record PostmortemRequest(@Size(max = 20_000) String summary, @Size(max = 20_000) String impact,
                             @Size(max = 20_000) String rootCause,
                             @Size(max = 100) List<@Valid TimelineItemRequest> timeline,
                             @Size(max = 100) List<@Valid ActionItemRequest> actionItems) {

        PostmortemUseCase.PostmortemContent toContent() {
            return new PostmortemUseCase.PostmortemContent(summary, impact, rootCause,
                    timeline == null ? List.of() : timeline.stream()
                            .map(t -> new Postmortem.TimelineItem(t.at(), t.text().trim())).toList(),
                    actionItems == null ? List.of() : actionItems.stream()
                            .map(a -> new Postmortem.ActionItem(a.text(), a.owner(), a.dueDate(), a.done())).toList());
        }
    }
}
