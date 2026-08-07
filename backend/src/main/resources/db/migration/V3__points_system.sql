-- V3__points_system.sql
-- Sistema de puntuacion: compra de puntos (Stripe/PayPal), donaciones y retiradas.

ALTER TABLE points_history
    ADD COLUMN type VARCHAR(50) DEFAULT NULL AFTER description,
    ADD COLUMN created_at DATETIME DEFAULT NOW();

CREATE TABLE payment (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id INT UNSIGNED NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_payment_id VARCHAR(255),
    amount_eur DECIMAL(10,2) NOT NULL,
    points INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_payment_user FOREIGN KEY (user_id) REFERENCES user(id)
);

CREATE TABLE donation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    donor_id INT UNSIGNED NOT NULL,
    recipient_id INT UNSIGNED NOT NULL,
    points INT NOT NULL,
    message VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_donation_donor FOREIGN KEY (donor_id) REFERENCES user(id),
    CONSTRAINT fk_donation_recipient FOREIGN KEY (recipient_id) REFERENCES user(id)
);

CREATE TABLE withdrawal_request (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id INT UNSIGNED NOT NULL,
    points INT NOT NULL,
    eur_value DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_withdrawal_user FOREIGN KEY (user_id) REFERENCES user(id)
);

CREATE TABLE notification (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id INT UNSIGNED NOT NULL,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body VARCHAR(1000),
    is_read BOOLEAN NOT NULL DEFAULT false,
    created_at DATETIME NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES user(id)
);
