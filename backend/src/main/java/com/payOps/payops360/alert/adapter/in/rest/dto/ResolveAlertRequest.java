package com.payops.payops360.alert.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for resolving alert request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolveAlertRequest {
    
    @NotBlank(message = "User ID is required")
    private String userId;
    
    private String resolutionNote;
}
