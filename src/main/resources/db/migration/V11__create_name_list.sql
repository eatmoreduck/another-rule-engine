-- 黑名单/白名单表
CREATE TABLE IF NOT EXISTS name_list (
    id          BIGSERIAL PRIMARY KEY,
    list_type   VARCHAR(10)  NOT NULL,
    key_type    VARCHAR(20)  NOT NULL,
    key_value   VARCHAR(256) NOT NULL,
    reason      TEXT,
    source      VARCHAR(255),
    expired_at  TIMESTAMP,
    created_by  VARCHAR(255) NOT NULL DEFAULT 'system',
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  VARCHAR(255),
    updated_at  TIMESTAMP,
    CONSTRAINT chk_nl_list_type CHECK (list_type IN ('BLACK', 'WHITE')),
    CONSTRAINT chk_nl_key_type  CHECK (key_type  IN ('ID_NO', 'DEVICE_ID', 'IP', 'PHONE_NO', 'MAC_ADDR')),
    CONSTRAINT uq_nl_type_key    UNIQUE (list_type, key_type, key_value)
);

CREATE INDEX idx_nl_lookup  ON name_list (list_type, key_type, key_value);
CREATE INDEX idx_nl_expired ON name_list (expired_at);

COMMENT ON TABLE  name_list IS '黑名单/白名单';
COMMENT ON COLUMN name_list.list_type IS 'BLACK-黑名单, WHITE-白名单';
COMMENT ON COLUMN name_list.key_type  IS 'ID_NO, DEVICE_ID, IP, PHONE_NO, MAC_ADDR';
COMMENT ON COLUMN name_list.key_value IS '实际值';
COMMENT ON COLUMN name_list.reason    IS '列入原因';
COMMENT ON COLUMN name_list.source    IS '添加来源';
COMMENT ON COLUMN name_list.expired_at IS '过期时间';
