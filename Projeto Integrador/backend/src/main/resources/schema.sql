CREATE TABLE IF NOT EXISTS app_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(190) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(30) NOT NULL DEFAULT 'Colaborador',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notebook (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    asset_tag VARCHAR(80) NOT NULL UNIQUE,
    serial_number VARCHAR(120),
    brand VARCHAR(80) NOT NULL,
    model VARCHAR(120) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DISPONIVEL',
    location VARCHAR(120),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS loan (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    notebook_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    checkout_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    due_date DATE NOT NULL,
    returned_at TIMESTAMP NULL,
    notes TEXT,
    CONSTRAINT fk_loan_notebook FOREIGN KEY (notebook_id) REFERENCES notebook(id),
    CONSTRAINT fk_loan_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

CREATE TABLE IF NOT EXISTS api_token (
    token_hash CHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_api_token_user_expiry (user_id, expires_at),
    CONSTRAINT fk_api_token_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);
