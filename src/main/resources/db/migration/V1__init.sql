CREATE TABLE business (
    id            UUID PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    slug          VARCHAR(80)  NOT NULL UNIQUE,
    timezone      VARCHAR(50)  NOT NULL,
    opening_time  TIME         NOT NULL,
    closing_time  TIME         NOT NULL,
    slot_minutes  INT          NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE service_offering (
    id               UUID PRIMARY KEY,
    business_id      UUID          NOT NULL REFERENCES business (id),
    name             VARCHAR(120)  NOT NULL,
    duration_minutes INT           NOT NULL,
    price            NUMERIC(12,2) NOT NULL,
    deposit_percent  INT           NOT NULL,
    active           BOOLEAN       NOT NULL
);

CREATE TABLE booking (
    id               UUID PRIMARY KEY,
    business_id      UUID          NOT NULL REFERENCES business (id),
    service_id       UUID          NOT NULL REFERENCES service_offering (id),
    customer_name    VARCHAR(120)  NOT NULL,
    customer_phone   VARCHAR(20)   NOT NULL,
    start_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    end_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    status           VARCHAR(30)   NOT NULL,
    deposit_amount   NUMERIC(12,2) NOT NULL,
    hold_expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    payment_id       VARCHAR(64),
    checkout_url     VARCHAR(500),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    version          BIGINT        NOT NULL
);

-- Búsqueda de solapamientos y armado de la agenda diaria.
CREATE INDEX idx_booking_business_start ON booking (business_id, start_at);
-- Job de vencimiento de señas impagas.
CREATE INDEX idx_booking_status_hold ON booking (status, hold_expires_at);

-- Idempotencia de webhooks: el proveedor reintenta notificaciones, cada evento se procesa una sola vez.
CREATE TABLE processed_webhook (
    id           UUID PRIMARY KEY,
    provider     VARCHAR(30)  NOT NULL,
    event_key    VARCHAR(150) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_processed_webhook UNIQUE (provider, event_key)
);

-- Transactional outbox: los eventos se guardan en la misma transacción que el cambio de estado
-- y un relay los publica después (hoy a logs, en la etapa 2 a Kafka).
CREATE TABLE outbox_event (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(50)   NOT NULL,
    aggregate_id   UUID          NOT NULL,
    event_type     VARCHAR(80)   NOT NULL,
    payload        VARCHAR(4000) NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at   TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_unpublished ON outbox_event (published_at, created_at);
