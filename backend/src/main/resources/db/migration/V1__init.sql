-- 리뷰-매출 연계 데이터분석 시스템 초기 스키마 (테이블 7개)

CREATE TABLE owner (
    id               BIGSERIAL PRIMARY KEY,
    provider         VARCHAR(20)  NOT NULL CHECK (provider IN ('KAKAO', 'GOOGLE', 'DEV')),
    provider_user_id VARCHAR(255) NOT NULL,
    email            VARCHAR(320),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_owner_provider UNIQUE (provider, provider_user_id)
);

CREATE TABLE store (
    id                BIGSERIAL PRIMARY KEY,
    owner_id          BIGINT       NOT NULL REFERENCES owner (id),
    name              VARCHAR(100) NOT NULL,
    category          VARCHAR(20)  NOT NULL
        CHECK (category IN ('KOREAN', 'CHINESE', 'JAPANESE', 'WESTERN', 'CAFE', 'PUB', 'ETC')),
    place_id          VARCHAR(50),
    place_name        VARCHAR(200),
    address           VARCHAR(500),
    last_collected_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_store_owner ON store (owner_id);

CREATE TABLE menu (
    id              BIGSERIAL PRIMARY KEY,
    store_id        BIGINT       NOT NULL REFERENCES store (id),
    pos_name        VARCHAR(200) NOT NULL,
    normalized_name VARCHAR(200) NOT NULL,
    aliases         TEXT[]       NOT NULL DEFAULT '{}',
    CONSTRAINT uq_menu_store_normalized UNIQUE (store_id, normalized_name)
);

CREATE TABLE job (
    id              BIGSERIAL PRIMARY KEY,
    store_id        BIGINT      NOT NULL REFERENCES store (id),
    type            VARCHAR(20) NOT NULL
        CHECK (type IN ('SALES_UPLOAD', 'REVIEW_COLLECT', 'REVIEW_UPLOAD', 'ANALYZE')),
    status          VARCHAR(20) NOT NULL
        CHECK (status IN ('REQUESTED', 'RUNNING', 'COMPLETED', 'FAILED')),
    file_path       VARCHAR(500),
    period_start    DATE,
    period_end      DATE,
    processed_count INTEGER     NOT NULL DEFAULT 0,
    error_count     INTEGER     NOT NULL DEFAULT 0,
    error_detail    JSONB,
    requested_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at     TIMESTAMPTZ
);
CREATE INDEX idx_job_store_requested ON job (store_id, requested_at DESC);

CREATE TABLE sales_record (
    id        BIGSERIAL PRIMARY KEY,
    store_id  BIGINT       NOT NULL REFERENCES store (id),
    job_id    BIGINT       NOT NULL REFERENCES job (id),
    menu_id   BIGINT REFERENCES menu (id),
    menu_name VARCHAR(200) NOT NULL,
    sold_at   TIMESTAMP    NOT NULL,   -- Asia/Seoul 현지 시각
    quantity  INTEGER      NOT NULL,
    amount    BIGINT       NOT NULL
);
CREATE INDEX idx_sales_store_sold_at ON sales_record (store_id, sold_at);
CREATE INDEX idx_sales_menu ON sales_record (menu_id);

CREATE TABLE review (
    id             BIGSERIAL PRIMARY KEY,
    store_id       BIGINT      NOT NULL REFERENCES store (id),
    job_id         BIGINT      NOT NULL REFERENCES job (id),
    source         VARCHAR(10) NOT NULL CHECK (source IN ('NAVER', 'FILE')),
    dedup_key      CHAR(64)    NOT NULL,
    -- 원본 (저장 후 수정 금지)
    content        TEXT        NOT NULL,
    rating         NUMERIC(2, 1),
    written_at     DATE        NOT NULL,
    visited_at     DATE,
    author_hash    CHAR(64),
    -- 분석 결과 (5주차 이후 채움)
    reliability    REAL,
    excluded       BOOLEAN,
    exclude_reason VARCHAR(50),
    neg_types      TEXT[],
    neg_probs      JSONB,
    menu_ids       BIGINT[],
    time_slot      VARCHAR(20),
    model_version  VARCHAR(50),
    analyzed_at    TIMESTAMPTZ,
    CONSTRAINT uq_review_store_dedup UNIQUE (store_id, dedup_key)
);
CREATE INDEX idx_review_store_written_at ON review (store_id, written_at);

-- 해석 결과 (이번 범위에서는 테이블만 생성)
CREATE TABLE insight (
    id          BIGSERIAL PRIMARY KEY,
    store_id    BIGINT      NOT NULL REFERENCES store (id),
    job_id      BIGINT REFERENCES job (id),
    rule_code   VARCHAR(50) NOT NULL,
    week        DATE        NOT NULL,
    target_type VARCHAR(20),
    target_id   BIGINT,
    status      VARCHAR(20),
    evidence    JSONB,
    confidence  REAL,
    sentence    TEXT,
    verdict     VARCHAR(20),
    comment     TEXT
);
CREATE INDEX idx_insight_store_week ON insight (store_id, week);
