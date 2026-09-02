--liquibase formatted sql

--changeset yowyob:026-create-bonification-transactions
-- ============================================================
-- V026 — Journal local des transactions Bonification
--
-- Jusqu'ici l'intégration bonification était en « tire et oublie » : le résultat
-- renvoyé par le partenaire (bonusapi) repartait vers l'appelant HTTP sans laisser
-- la moindre trace locale. Impossible dans ces conditions de mesurer le volume, le
-- taux de succès ou la contribution de ce canal aux bonus distribués.
--
-- Ce journal est en écriture seule (append-only) : une ligne par soumission, y
-- compris les échecs, avec le statut brut du partenaire conservé tel quel — son API
-- ne publie aucune énumération de statuts.
--
-- Pas de devise : l'API partenaire manipule un montant nu (double), sans unité
-- monétaire déclarée. Le champ reste donc un montant abstrait, à ne pas additionner
-- avec les montants wallet/facturation.
-- ============================================================

CREATE TABLE IF NOT EXISTS bonification_transactions (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL,

    -- Identifiant renvoyé par le partenaire ; NULL si la soumission a échoué avant
    -- d'obtenir une réponse exploitable.
    transaction_id      VARCHAR(255),
    client_login        VARCHAR(255)    NOT NULL,

    amount              NUMERIC(19, 4)  NOT NULL,
    debit               BOOLEAN         NOT NULL,

    -- Statut brut du partenaire (COMPLETE, PENDING, ERROR…), non normalisé
    status              VARCHAR(60)     NOT NULL,
    succeeded           BOOLEAN         NOT NULL,
    message             TEXT,

    submitted_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT bt_pkey PRIMARY KEY (id)
);

-- Analytique : séries temporelles et classements, toujours bornés par tenant + fenêtre
CREATE INDEX IF NOT EXISTS idx_bt_tenant_submitted
    ON bonification_transactions (tenant_id, submitted_at DESC);

-- Vue plateforme : même agrégation sans prédicat de tenant
CREATE INDEX IF NOT EXISTS idx_bt_submitted
    ON bonification_transactions (submitted_at DESC);

COMMENT ON TABLE  bonification_transactions             IS 'Journal append-only des transactions poussées vers l''API partenaire Bonification. Alimente les statistiques ; jamais lu par le flux métier.';
COMMENT ON COLUMN bonification_transactions.status      IS 'Statut brut du partenaire, conservé tel quel : son API ne publie aucune énumération.';
COMMENT ON COLUMN bonification_transactions.amount      IS 'Montant sans devise — l''API partenaire ne déclare aucune unité monétaire. Ne pas agréger avec les montants wallet/facturation.';
