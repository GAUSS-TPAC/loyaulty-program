--liquibase formatted sql

--changeset yowyob:025-align-payment-requests-with-kernel-core splitStatements:false
-- ============================================================
-- V025 — payment_requests réalignée sur la passerelle Kernel Core
--
-- La table créée en V003 décrivait une intégration directe avec les providers
-- (MTN/ORANGE/STRIPE, webhook brut, compteurs de retry). L'encaissement passe
-- désormais par la passerelle Kernel Core (POST /api/payments/orders) : c'est elle
-- qui détient les credentials PSP et pilote les retries, et elle nous rend un
-- identifiant d'ordre + une URL de redirection.
--
-- DROP puis CREATE plutôt qu'une série d'ALTER : aucun adaptateur R2DBC n'a jamais
-- implémenté PaymentRequestRepository (seul un stub en mémoire existait), donc la
-- table n'a jamais reçu la moindre ligne dans aucun environnement.
--
-- Pas de colonne version/optimistic locking ici : deux confirmations concurrentes
-- convergent vers le même état final, et le double crédit est déjà empêché par la
-- clé d'idempotence portée par le crédit du wallet (topup-<externalRef>).
-- ============================================================

DROP TABLE IF EXISTS payment_requests;

DROP TYPE IF EXISTS payment_provider;
DROP TYPE IF EXISTS payment_direction;
DROP TYPE IF EXISTS payment_request_status;

CREATE TABLE payment_requests (

    id                      UUID            NOT NULL DEFAULT gen_random_uuid(),
    tenant_id               UUID            NOT NULL,
    wallet_id               UUID            NOT NULL,
    member_id               UUID            NOT NULL,
    -- Renseignée seulement après crédit effectif du wallet
    wallet_transaction_id   UUID,

    -- Identifiant de la PaymentOrder Kernel Core : clé de rapprochement des callbacks
    external_ref            VARCHAR(255)    NOT NULL,

    provider                VARCHAR(50)     NOT NULL,   -- MYCOOLPAY | STRIPE
    method                  VARCHAR(30),                -- MOBILE_MONEY | CARD
    direction               VARCHAR(10)     NOT NULL,   -- INBOUND | OUTBOUND

    amount                  NUMERIC(19, 4)  NOT NULL,
    currency                VARCHAR(3)      NOT NULL,

    payer_reference         VARCHAR(120),               -- MSISDN E.164 ou référence carte
    provider_reference      VARCHAR(255),               -- Référence propre au PSP
    redirect_url            TEXT,                       -- Page de paiement à présenter au payeur
    idempotency_key         VARCHAR(255),

    status                  VARCHAR(20)     NOT NULL,   -- PaymentStatus (Java)
    -- Libellé brut renvoyé par la passerelle : son OpenAPI type le statut en string
    -- libre, la valeur d'origine est donc conservée pour l'audit
    raw_status              VARCHAR(60),

    initiated_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    confirmed_at            TIMESTAMPTZ,
    expires_at              TIMESTAMPTZ,

    CONSTRAINT pr_pkey              PRIMARY KEY (id),
    CONSTRAINT pr_external_ref_uq   UNIQUE (external_ref),
    CONSTRAINT pr_amount_positive   CHECK (amount > 0),
    CONSTRAINT pr_currency_length   CHECK (char_length(currency) = 3),

    CONSTRAINT pr_fk_wallet
        FOREIGN KEY (wallet_id) REFERENCES wallets(id)
        ON DELETE RESTRICT,

    CONSTRAINT pr_fk_wallet_transaction
        FOREIGN KEY (wallet_transaction_id) REFERENCES wallet_transactions(id)
        ON DELETE RESTRICT
);

-- Historique des recharges d'un wallet
CREATE INDEX IF NOT EXISTS idx_pr_wallet_id
    ON payment_requests (wallet_id, tenant_id, initiated_at DESC);

-- Réconciliation : recharges ouvertes dont la fenêtre est dépassée
CREATE INDEX IF NOT EXISTS idx_pr_pending_expiry
    ON payment_requests (status, expires_at)
    WHERE status IN ('INITIATED', 'PENDING');

COMMENT ON TABLE  payment_requests                  IS 'Ordres de paiement délégués à la passerelle Kernel Core (/api/payments/orders). Table de rapprochement entre un callback entrant et le wallet à créditer.';
COMMENT ON COLUMN payment_requests.external_ref     IS 'Identifiant de la PaymentOrder Kernel Core. Unique : c''est la clé de réconciliation.';
COMMENT ON COLUMN payment_requests.raw_status       IS 'Statut brut de la passerelle, conservé tel quel car son OpenAPI ne publie aucune énumération.';
COMMENT ON COLUMN payment_requests.tenant_id        IS 'Dupliqué depuis le wallet : un callback arrive sans JWT, donc sans contexte de tenant à résoudre.';
