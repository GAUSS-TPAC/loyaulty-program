package com.yowyob.loyalty.infrastructure.persistence.wallet.mapper;

import com.yowyob.loyalty.domain.shared.model.TenantId;
import com.yowyob.loyalty.domain.shared.model.UserId;
import com.yowyob.loyalty.domain.wallet.model.PaymentDirection;
import com.yowyob.loyalty.domain.wallet.model.PaymentRequest;
import com.yowyob.loyalty.domain.wallet.model.PaymentStatus;
import com.yowyob.loyalty.infrastructure.persistence.wallet.entity.PaymentRequestEntity;
import org.springframework.stereotype.Component;

@Component
public class PaymentRequestMapper {

    public PaymentRequest toDomain(PaymentRequestEntity entity) {
        return new PaymentRequest(
                entity.getId(),
                TenantId.of(entity.getTenantId()),
                entity.getWalletId(),
                UserId.of(entity.getMemberId()),
                entity.getWalletTransactionId(),
                entity.getExternalRef(),
                entity.getProvider(),
                entity.getMethod(),
                PaymentDirection.valueOf(entity.getDirection()),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getPayerReference(),
                entity.getProviderReference(),
                entity.getRedirectUrl(),
                entity.getIdempotencyKey(),
                PaymentStatus.valueOf(entity.getStatus()),
                entity.getRawStatus(),
                entity.getInitiatedAt(),
                entity.getConfirmedAt(),
                entity.getExpiresAt());
    }

    public PaymentRequestEntity toEntity(PaymentRequest domain) {
        return PaymentRequestEntity.builder()
                .id(domain.id())
                .tenantId(domain.tenantId().value())
                .walletId(domain.walletId())
                .memberId(domain.memberId().value())
                .walletTransactionId(domain.walletTransactionId())
                .externalRef(domain.externalRef())
                .provider(domain.provider())
                .method(domain.method())
                .direction(domain.direction().name())
                .amount(domain.amount())
                .currency(domain.currency())
                .payerReference(domain.payerReference())
                .providerReference(domain.providerReference())
                .redirectUrl(domain.redirectUrl())
                .idempotencyKey(domain.idempotencyKey())
                .status(domain.status().name())
                .rawStatus(domain.rawStatus())
                .initiatedAt(domain.initiatedAt())
                .confirmedAt(domain.confirmedAt())
                .expiresAt(domain.expiresAt())
                .build();
    }
}
