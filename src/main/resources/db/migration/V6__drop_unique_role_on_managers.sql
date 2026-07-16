-- V4 created managers.role as UNIQUE, which prevents two managers from ever
-- sharing the same role (e.g. two "Team Lead" managers). That is almost
-- certainly unintended, so drop the auto-generated unique constraint.
ALTER TABLE managers
    DROP CONSTRAINT IF EXISTS managers_role_key;
