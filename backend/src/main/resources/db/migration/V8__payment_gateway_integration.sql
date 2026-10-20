-- =======================================================
-- Restaurant Management System - V8 Payment Gateway Integration
-- Adds support for multi-gateway processing (Stripe & Razorpay)
-- =======================================================

-- 1. Extend bill_payments with generic gateway columns
ALTER TABLE bill_payments 
ADD COLUMN IF NOT EXISTS gateway_provider VARCHAR(20),
ADD COLUMN IF NOT EXISTS gateway_order_id VARCHAR(100),
ADD COLUMN IF NOT EXISTS gateway_payment_id VARCHAR(100),
ADD COLUMN IF NOT EXISTS gateway_signature VARCHAR(255),
ADD COLUMN IF NOT EXISTS currency VARCHAR(10) DEFAULT 'SGD';

CREATE INDEX IF NOT EXISTS idx_bill_payments_gateway_order ON bill_payments(gateway_order_id);
CREATE INDEX IF NOT EXISTS idx_bill_payments_gateway_payment ON bill_payments(gateway_payment_id);
CREATE INDEX IF NOT EXISTS idx_bill_payments_gateway_provider ON bill_payments(gateway_provider);

-- 2. Gateway Webhook Events table for strict deduplication & auditing
CREATE TABLE IF NOT EXISTS gateway_webhook_events (
    id VARCHAR(36) PRIMARY KEY,
    gateway_provider VARCHAR(20) NOT NULL,
    event_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSED',
    payload TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_gateway_webhook_event UNIQUE (gateway_provider, event_id)
);

CREATE INDEX IF NOT EXISTS idx_gateway_webhook_events_lookup ON gateway_webhook_events(gateway_provider, event_id);
