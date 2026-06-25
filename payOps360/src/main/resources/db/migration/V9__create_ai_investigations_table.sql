-- V9: Create ai_investigations table

CREATE TABLE ai_investigations (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL UNIQUE,
    investigation_type VARCHAR(50) NOT NULL,
    context TEXT,
    prompt TEXT,
    ai_response TEXT,
    root_cause TEXT,
    recommendations JSONB,
    confidence_score DOUBLE PRECISION,
    model VARCHAR(50),
    investigated_at TIMESTAMP NOT NULL,
    processing_time_ms BIGINT,
    CONSTRAINT fk_ai_investigation_incident FOREIGN KEY (incident_id) REFERENCES incidents(id) ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX idx_ai_investigation_incident_id ON ai_investigations(incident_id);
CREATE INDEX idx_ai_investigation_investigated_at ON ai_investigations(investigated_at DESC);
CREATE INDEX idx_ai_investigation_confidence ON ai_investigations(confidence_score DESC);

-- Comments
COMMENT ON TABLE ai_investigations IS 'AI-powered root cause analysis and recommendations for incidents';
COMMENT ON COLUMN ai_investigations.investigation_type IS 'Type: ROOT_CAUSE_ANALYSIS, PATTERN_ANALYSIS, PREDICTION';
COMMENT ON COLUMN ai_investigations.confidence_score IS 'AI confidence score (0.0 to 1.0)';
COMMENT ON COLUMN ai_investigations.model IS 'AI model used: gpt-4, claude-3-opus, mock';
COMMENT ON COLUMN ai_investigations.recommendations IS 'JSON array of actionable recommendations';

