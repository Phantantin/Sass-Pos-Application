-- Supports the paginated operational audit-log queries introduced after V4.
-- MySQL can scan these indexes in reverse for the created_at DESC, id DESC sort.
CREATE INDEX idx_audit_log_action_created ON audit_log (action, created_at);
CREATE INDEX idx_audit_log_created_id ON audit_log (created_at, id);
