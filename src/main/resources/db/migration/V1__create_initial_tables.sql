CREATE TABLE trips (
    id            BIGSERIAL      PRIMARY KEY,
    name          VARCHAR(120)   NOT NULL,
    destination   VARCHAR(120)   NOT NULL,
    base_currency CHAR(3)        NOT NULL,
    budget        NUMERIC(19, 2) NOT NULL,
    start_date    DATE           NOT NULL,
    end_date      DATE           NOT NULL,
    CONSTRAINT chk_trips_budget_positive CHECK (budget > 0),
    CONSTRAINT chk_trips_dates CHECK (end_date >= start_date)
);

CREATE TABLE expenses (
    id                BIGSERIAL      PRIMARY KEY,
    trip_id           BIGINT         NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    description       VARCHAR(255)   NOT NULL,
    category          VARCHAR(20)    NOT NULL,
    original_amount   NUMERIC(19, 2) NOT NULL,
    original_currency CHAR(3)        NOT NULL,
    exchange_rate     NUMERIC(19, 8) NOT NULL,
    converted_amount  NUMERIC(19, 2) NOT NULL,
    expense_date      DATE           NOT NULL,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT chk_expenses_category CHECK (category IN ('FOOD', 'TRANSPORT', 'LODGING', 'LEISURE', 'SHOPPING', 'OTHER')),
    CONSTRAINT chk_expenses_original_amount_positive CHECK (original_amount > 0),
    CONSTRAINT chk_expenses_exchange_rate_positive CHECK (exchange_rate > 0),
    CONSTRAINT chk_expenses_converted_amount_positive CHECK (converted_amount > 0)
);

CREATE INDEX idx_expenses_trip_category ON expenses (trip_id, category);
