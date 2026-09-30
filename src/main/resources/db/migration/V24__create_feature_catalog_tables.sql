-- V24: create feature catalog tables for managed feature definitions and alias compatibility

CREATE TABLE feature_definition (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(120) NOT NULL,
    name VARCHAR(200) NOT NULL,
    data_type VARCHAR(50) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    example_value TEXT,
    description TEXT,
    scope VARCHAR(100),
    sensitivity VARCHAR(50) NOT NULL DEFAULT 'NORMAL',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    owner VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_feature_definition_code ON feature_definition (code);
CREATE INDEX idx_feature_definition_status ON feature_definition (status);
CREATE INDEX idx_feature_definition_sensitivity ON feature_definition (sensitivity);
CREATE INDEX idx_feature_definition_source_type ON feature_definition (source_type);

CREATE TABLE feature_alias (
    id BIGSERIAL PRIMARY KEY,
    alias_code VARCHAR(120) NOT NULL,
    canonical_code VARCHAR(120) NOT NULL,
    alias_type VARCHAR(50) NOT NULL DEFAULT 'LEGACY',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_feature_alias_canonical_code
        FOREIGN KEY (canonical_code) REFERENCES feature_definition(code) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_feature_alias_alias_code ON feature_alias (alias_code);
CREATE INDEX idx_feature_alias_canonical_code ON feature_alias (canonical_code);

INSERT INTO feature_definition (code, name, data_type, source_type, example_value, description, scope, sensitivity, status, owner)
VALUES
    ('user_age', '用户年龄', 'NUMBER', 'INPUT', '25', '用户基础画像中的年龄特征', 'USER_PROFILE', 'NORMAL', 'ACTIVE', 'system'),
    ('user_level', '用户等级', 'STRING', 'INPUT', 'VIP', '用户会员等级', 'USER_PROFILE', 'NORMAL', 'ACTIVE', 'system'),
    ('order_amount', '订单金额', 'NUMBER', 'INPUT', '1288.88', '订单支付金额', 'ORDER', 'NORMAL', 'ACTIVE', 'system'),
    ('risk_score', '风险分', 'NUMBER', 'DERIVED', '0.82', '上游模型输出的风险分', 'RISK_MODEL', 'SENSITIVE', 'ACTIVE', 'system');

INSERT INTO feature_alias (alias_code, canonical_code, alias_type, status)
VALUES
    ('amount', 'order_amount', 'LEGACY', 'ACTIVE'),
    ('score', 'risk_score', 'LEGACY', 'ACTIVE');
