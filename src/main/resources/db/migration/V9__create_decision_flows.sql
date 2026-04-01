CREATE TABLE IF NOT EXISTS decision_flows (
    id BIGSERIAL PRIMARY KEY,
    flow_key VARCHAR(255) UNIQUE NOT NULL,
    flow_name VARCHAR(255) NOT NULL,
    flow_description TEXT,
    flow_graph TEXT NOT NULL,
    version INT NOT NULL DEFAULT 1,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255),
    updated_at TIMESTAMP,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    opt_lock_version BIGINT DEFAULT 0,
    environment_id BIGINT,
    CONSTRAINT chk_df_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED', 'DELETED'))
);

CREATE INDEX idx_df_flow_key ON decision_flows(flow_key);
CREATE INDEX idx_df_status ON decision_flows(status);
CREATE INDEX idx_df_enabled ON decision_flows(enabled);
CREATE INDEX idx_df_status_enabled ON decision_flows(status, enabled);

CREATE TABLE IF NOT EXISTS decision_flow_versions (
    id BIGSERIAL PRIMARY KEY,
    flow_id BIGINT NOT NULL,
    flow_key VARCHAR(255) NOT NULL,
    version INT NOT NULL,
    flow_graph TEXT NOT NULL,
    change_reason TEXT,
    changed_by VARCHAR(255) NOT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_rollback BOOLEAN DEFAULT FALSE,
    rollback_from_version INT,
    CONSTRAINT fk_dfv_flow FOREIGN KEY (flow_id) REFERENCES decision_flows(id)
);

CREATE INDEX idx_dfv_flow_id ON decision_flow_versions(flow_id);
CREATE INDEX idx_dfv_flow_key_version ON decision_flow_versions(flow_key, version);
