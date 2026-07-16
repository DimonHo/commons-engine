-- =====================================================
-- V8: Add worker_id and service_type to ledger_events
--     (P0 fix: payment integrity - settle/refund must
--      reconstruct transaction from event store)
-- =====================================================

ALTER TABLE ledger_events ADD COLUMN IF NOT EXISTS worker_id VARCHAR(36);
ALTER TABLE ledger_events ADD COLUMN IF NOT EXISTS service_type VARCHAR(30);

-- Index for potential worker payout queries
CREATE INDEX IF NOT EXISTS idx_ledger_worker ON ledger_events(worker_id);
