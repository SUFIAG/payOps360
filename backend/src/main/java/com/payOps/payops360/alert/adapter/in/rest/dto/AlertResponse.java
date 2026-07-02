package com.payops.payops360.alert.adapter.in.rest.dto;

import com.payops.payops360.alert.domain.model.AlertSeverity;
import com.payops.payops360.alert.domain.model.AlertStatus;
import com.payops.payops360.alert.domain.model.AlertType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * DTO for alert response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertResponse {
    
    private Long id;
    private String alertId;
    
    private AlertType alertType;
    private AlertSeverity severity;
    
    private String entityType;
    private String entityId;
    
    private String title;
    private String description;
    private String metricName;
    private Double metricValue;
    private Double thresholdValue;
    
    private AlertStatus status;
    private Instant statusChangedAt;
    
    private Instant resolvedAt;
    private String resolvedBy;
    private String resolutionNote;
    
    private Long incidentId;
    
    private Map<String, Object> metadata;
    
    private Instant detectedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
