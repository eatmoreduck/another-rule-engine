-- V23: Add unique constraint on (rule_key, version) in rule_versions table
-- This prevents TOCTOU race condition when concurrent version creation occurs

-- First, deduplicate any existing duplicate records (keep the one with highest id)
DELETE FROM rule_versions
WHERE id NOT IN (
    SELECT MAX(id)
    FROM rule_versions
    GROUP BY rule_key, version
);

-- Add the unique constraint
ALTER TABLE rule_versions
ADD CONSTRAINT uq_rule_versions_rule_key_version UNIQUE (rule_key, version);
