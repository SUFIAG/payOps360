package com.payops.identity.adapter.input.web;

import com.payops.identity.application.port.input.InvitationUseCase;
import com.payops.identity.application.port.input.UserAdministrationUseCase;
import com.payops.identity.application.port.input.UserView;
import com.payops.identity.domain.Role;
import com.payops.shared.adapter.web.ApiResponse;
import com.payops.shared.adapter.web.AuthenticatedUser;
import com.payops.shared.adapter.web.ClientInfo;
import com.payops.shared.adapter.web.PageResponse;
import com.payops.shared.application.RequestContext;
import com.payops.shared.domain.PageQuery;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "User administration")
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasAuthority('USER_MANAGE')")
@RequiredArgsConstructor
class UserAdministrationController {

    private final UserAdministrationUseCase users;
    private final InvitationUseCase invitations;

    @GetMapping
    ApiResponse<PageResponse<UserView>> list(@AuthenticationPrincipal AuthenticatedUser user,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "25") int size) {
        return ApiResponse.ok(PageResponse.from(users.list(user.actor(), PageQuery.of(page, size)), v -> v));
    }

    @PostMapping
    ApiResponse<UserAdministrationUseCase.CreatedUser> create(@AuthenticationPrincipal AuthenticatedUser user,
                                                              @Valid @RequestBody CreateUserRequest request,
                                                              HttpServletRequest http) {
        return ApiResponse.ok(users.create(user.actor(), request.email(), request.fullName(), request.role(), context(http)));
    }

    @PostMapping("/invitations")
    ApiResponse<UserView> invite(@AuthenticationPrincipal AuthenticatedUser user,
                                 @Valid @RequestBody CreateUserRequest request, HttpServletRequest http) {
        return ApiResponse.ok(invitations.invite(user.actor(), request.email(), request.fullName(), request.role(),
                context(http)));
    }

    @PostMapping("/{userId}/invitations/resend")
    ApiResponse<UserView> resendInvitation(@AuthenticationPrincipal AuthenticatedUser user,
                                           @PathVariable UUID userId, HttpServletRequest http) {
        return ApiResponse.ok(invitations.resend(user.actor(), userId, context(http)));
    }

    @DeleteMapping("/{userId}/invitations")
    ApiResponse<Void> revokeInvitation(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID userId,
                                       HttpServletRequest http) {
        invitations.revoke(user.actor(), userId, context(http));
        return ApiResponse.ok(null);
    }

    @PutMapping("/{userId}/role")
    ApiResponse<UserView> changeRole(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID userId,
                                     @Valid @RequestBody ChangeRoleRequest request, HttpServletRequest http) {
        return ApiResponse.ok(users.changeRole(user.actor(), userId, request.role(), context(http)));
    }

    @PostMapping("/{userId}/disable")
    ApiResponse<UserView> disable(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID userId,
                                  HttpServletRequest http) {
        return ApiResponse.ok(users.disable(user.actor(), userId, context(http)));
    }

    @PostMapping("/{userId}/enable")
    ApiResponse<UserView> enable(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID userId,
                                 HttpServletRequest http) {
        return ApiResponse.ok(users.enable(user.actor(), userId, context(http)));
    }

    @PostMapping("/{userId}/unlock")
    ApiResponse<UserView> unlock(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID userId,
                                 HttpServletRequest http) {
        return ApiResponse.ok(users.unlock(user.actor(), userId, context(http)));
    }

    @PostMapping("/{userId}/reset-password")
    ApiResponse<UserAdministrationUseCase.CreatedUser> resetPassword(@AuthenticationPrincipal AuthenticatedUser user,
                                                                     @PathVariable UUID userId, HttpServletRequest http) {
        return ApiResponse.ok(users.resetPassword(user.actor(), userId, context(http)));
    }

    @PostMapping("/{userId}/reset-mfa")
    ApiResponse<UserView> resetMfa(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID userId,
                                   HttpServletRequest http) {
        return ApiResponse.ok(users.resetMfa(user.actor(), userId, context(http)));
    }

    private static RequestContext context(HttpServletRequest request) {
        ClientInfo info = ClientInfo.from(request);
        return new RequestContext(info.ipAddress(), info.userAgent());
    }

    record CreateUserRequest(@NotBlank @Email @Size(max = 320) String email,
                             @NotBlank @Size(min = 2, max = 200) String fullName,
                             @NotNull Role role) {
    }

    record ChangeRoleRequest(@NotNull Role role) {
    }
}
