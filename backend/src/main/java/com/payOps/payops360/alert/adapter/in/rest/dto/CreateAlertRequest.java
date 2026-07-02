package com.payops.payops360.alert.adapter.in.rest.dto;

import com.payops.payops360.alert.domain.model.AlertSeverity;
import com.payops.payops360.alert.domain.model.AlertType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for creating alert request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAlertRequest {
    
    @NotNull(message = "Alert type is required")
    private AlertType alertType;
    
    @NotNull(message = "Severity is required")
    private AlertSeverity severity;
    
    @NotBlank(message = "Entity type is required")
    private String entityType;
    
    @NotBlank(message = "Entity ID is required")
    private String entityId;
    
    @NotBlank(message = "Title is required")
    private String title;
    
    private String description;
    private String metricName;
    private Double metricValue;
    private Double thresholdValue;
}
