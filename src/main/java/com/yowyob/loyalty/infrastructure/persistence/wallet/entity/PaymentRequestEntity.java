package com.yowyob.loyalty.infrastructure.persistence.wallet.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("payment_requests")
public class PaymentRequestEntity {

    @Id
    private UUID id;

    @Column("tenant_id")
    private UUID tenantId;

    @Column("wallet_id")
    private UUID walletId;

    @Column("member_id")
    private UUID memberId;

    @Column("wallet_transaction_id")
    private UUID walletTransactionId;

    @Column("external_ref")
    private String externalRef;

    private String provider;
    private String method;
    private String direction;
    private BigDecimal amount;
    private String currency;

    @Column("payer_reference")
    private String payerReference;

    @Column("provider_reference")
    private String providerReference;

    @Column("redirect_url")
    private String redirectUrl;

    @Column("idempotency_key")
    private String idempotencyKey;

    private String status;

    @Column("raw_status")
    private String rawStatus;

    @Column("initiated_at")
    private Instant initiatedAt;

    @Column("confirmed_at")
    private Instant confirmedAt;

    @Column("expires_at")
    private Instant expiresAt;
}
