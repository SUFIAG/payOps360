package com.payops.intelligence.adapter.input.web;

import com.payops.incident.application.port.input.IncidentView;
import com.payops.incident.application.port.input.PostmortemUseCase;
import com.payops.intelligence.application.port.input.PostmortemDraftUseCase;
import com.payops.intelligence.application.port.input.BriefingUseCase;
import com.payops.intelligence.application.port.input.CopilotUseCase;
import com.payops.intelligence.application.port.input.InsightsUseCase;
import com.payops.intelligence.application.port.input.InvestigationUseCase;
import com.payops.intelligence.application.port.input.ReconciliationExplainUseCase;
import com.payops.intelligence.application.port.input.SmartSearchUseCase;
import com.payops.shared.adapter.web.ApiResponse;
import com.payops.shared.adapter.web.AuthenticatedUser;
import com.payops.shared.adapter.web.ClientInfo;
import com.payops.shared.application.RequestContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "AI intelligence")
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
class IntelligenceController {

    private final InsightsUseCase insights;
    private final CopilotUseCase copilot;
    private final InvestigationUseCase investigation;
    private final SmartSearchUseCase smartSearch;
    private final BriefingUseCase briefing;
    private final PostmortemDraftUseCase postmortemDrafts;
    private final ReconciliationExplainUseCase reconciliationExplain;

    @GetMapping("/insights")
    @PreAuthorize("hasAuthority('ANALYTICS_READ') or hasAuthority('PAYMENT_READ')")
    ApiResponse<InsightsUseCase.OperationalInsights> insights(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(insights.insights(user.organizationId()));
    }

    @PostMapping("/copilot")
    @PreAuthorize("hasAuthority('AI_USE')")
    ApiResponse<CopilotUseCase.CopilotReply> ask(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @Valid @RequestBody CopilotRequest request) {
        return ApiResponse.ok(copilot.ask(user.actor(), request.question(),
                request.history() == null ? List.of() : request.history().stream()
                        .map(t -> new CopilotUseCase.Turn(t.role(), t.content())).toList()));
    }

    @PostMapping("/incidents/{reference}/investigate")
    @PreAuthorize("hasAuthority('AI_USE') and hasAuthority('INCIDENT_READ')")
    ApiResponse<IncidentView> investigate(@AuthenticationPrincipal AuthenticatedUser user,
                                          @PathVariable String reference, HttpServletRequest http) {
        ClientInfo info = ClientInfo.from(http);
        return ApiResponse.ok(investigation.investigateIncident(user.actor(), reference,
                new RequestContext(info.ipAddress(), info.userAgent())));
    }

    @PostMapping("/incidents/{reference}/postmortem-draft")
    @PreAuthorize("hasAuthority('AI_USE') and hasAuthority('INCIDENT_MANAGE')")
    ApiResponse<PostmortemUseCase.PostmortemView> draftPostmortem(@AuthenticationPrincipal AuthenticatedUser user,
                                                                  @PathVariable String reference,
                                                                  HttpServletRequest http) {
        ClientInfo info = ClientInfo.from(http);
        return ApiResponse.ok(postmortemDrafts.draftPostmortem(user.actor(), reference,
                new RequestContext(info.ipAddress(), info.userAgent())));
    }

    @GetMapping("/payments/{reference}/explain")
    @PreAuthorize("hasAuthority('AI_USE') and hasAuthority('PAYMENT_READ')")
    ApiResponse<InvestigationUseCase.PaymentInsight> explain(@AuthenticationPrincipal AuthenticatedUser user,
                                                             @PathVariable String reference) {
        return ApiResponse.ok(investigation.explainPayment(user.actor(), reference));
    }

    @PostMapping("/reconciliation/exceptions/{reference}/explain")
    @PreAuthorize("hasAuthority('AI_USE') and hasAuthority('RECON_READ')")
    ApiResponse<ReconciliationExplainUseCase.ExceptionExplanation> explainReconciliation(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable String reference, HttpServletRequest http) {
        ClientInfo info = ClientInfo.from(http);
        return ApiResponse.ok(reconciliationExplain.explainException(user.actor(), reference,
                new RequestContext(info.ipAddress(), info.userAgent())));
    }

    @PostMapping("/search/interpret")
    @PreAuthorize("hasAuthority('PAYMENT_READ')")
    ApiResponse<SmartSearchUseCase.SearchInterpretation> interpret(@AuthenticationPrincipal AuthenticatedUser user,
                                                                   @Valid @RequestBody SearchRequest request) {
        return ApiResponse.ok(smartSearch.interpret(user.actor(), request.query()));
    }

    @GetMapping("/briefing")
    @PreAuthorize("hasAuthority('AI_USE') and hasAuthority('ANALYTICS_READ')")
    ApiResponse<BriefingUseCase.Briefing> briefing(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(briefing.briefing(user.actor()));
    }

    record CopilotRequest(@NotBlank @Size(max = 2000) String question, @Size(max = 20) List<@Valid TurnDto> history) {
    }

    record TurnDto(@Size(max = 20) String role, @Size(max = 8000) String content) {
    }

    record SearchRequest(@NotBlank @Size(max = 500) String query) {
    }
}
