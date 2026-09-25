CREATE DATABASE IF NOT EXISTS finance_tracker;
USE finance_tracker;
CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(80) NOT NULL,
    email VARCHAR(190) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    monthly_budget DECIMAL(12, 2) NOT NULL DEFAULT 3000,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS transactions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    type ENUM('INCOME', 'EXPENSE') NOT NULL,
    category VARCHAR(60) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    description VARCHAR(160) NOT NULL,
    transaction_date DATE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transactions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT positive_amount CHECK (amount > 0),
    INDEX idx_transactions_user_date (user_id, transaction_date)
);

CREATE TABLE IF NOT EXISTS monthly_budgets (
    user_id BIGINT NOT NULL,
    budget_month DATE NOT NULL,
    amount DECIMAL(12, 2) NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, budget_month),
    CONSTRAINT fk_budgets_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT positive_budget CHECK (amount >= 0)
);
