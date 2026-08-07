-- V4__automatic_withdrawals.sql
-- Retiradas automaticas: se registra el proveedor, el destino y el id de payout
-- para ejecutar el pago de euros directamente sin intervencion manual.

ALTER TABLE withdrawal_request
    ADD COLUMN provider VARCHAR(20),
    ADD COLUMN destination VARCHAR(255),
    ADD COLUMN provider_payout_id VARCHAR(255);
