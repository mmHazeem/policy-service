CREATE TABLE IF NOT EXISTS policies (
    id UUID PRIMARY KEY,
    policy_number VARCHAR(255) NOT NULL UNIQUE,
    policy_holder VARCHAR(255),
    coverage_amount NUMERIC(19, 2),
    premium_amount NUMERIC(19, 2),
    start_date DATE,
    status VARCHAR(50)
    );