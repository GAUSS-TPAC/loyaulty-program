package com.yowyob.loyalty.application.wallet;

import com.yowyob.loyalty.application.wallet.handler.ConfirmTopUpHandler;
import com.yowyob.loyalty.domain.shared.model.TenantId;
import com.yowyob.loyalty.domain.shared.model.UserId;
import com.yowyob.loyalty.domain.wallet.model.PaymentDirection;
import com.yowyob.loyalty.domain.wallet.model.PaymentOrder;
import com.yowyob.loyalty.domain.wallet.model.PaymentOrderRequest;
import com.yowyob.loyalty.domain.wallet.model.PaymentRequest;
import com.yowyob.loyalty.domain.wallet.model.PaymentStatus;
import com.yowyob.loyalty.domain.wallet.model.TransactionSource;
import com.yowyob.loyalty.domain.wallet.model.WalletCreditResult;
import com.yowyob.loyalty.domain.wallet.port.in.CreditWalletUseCase;
import com.yowyob.loyalty.domain.wallet.port.out.PaymentGatewayPort;
import com.yowyob.loyalty.domain.wallet.port.out.PaymentRequestRepository;
import com.yowyob.loyalty.shared.exception.IdempotencyConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le crédit ne doit dépendre que de ce que la passerelle répond, jamais de ce que
 * l'appelant prétend.
 */
class ConfirmTopUpHandlerTest {

    private static final String ORDER_REF = "order-123";
    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    private static final UserId MEMBER = UserId.of(UUID.randomUUID());

    private FakePaymentRequestRepository repository;
    private FakeGateway gateway;
    private RecordingCreditUseCase credit;
    private ConfirmTopUpHandler handler;

    @BeforeEach
    void setUp() {
        repository = new FakePaymentRequestRepository();
        gateway = new FakeGateway();
        credit = new RecordingCreditUseCase();
        handler = new ConfirmTopUpHandler(repository, gateway, credit);
    }

    @Test
    void credits_the_wallet_when_the_gateway_confirms_the_payment() {
        repository.store(pendingRequest());
        gateway.respondWith(order("SUCCESS", PaymentStatus.COMPLETED));

        StepVerifier.create(handler.confirm(ORDER_REF))
                .assertNext(request -> {
                    assertThat(request.status()).isEqualTo(PaymentStatus.COMPLETED);
                    assertThat(request.confirmedAt()).isNotNull();
                })
                .verifyComplete();

        assertThat(credit.calls).hasSize(1);
        assertThat(credit.calls.get(0).amount()).isEqualByComparingTo("5000");
        assertThat(credit.calls.get(0).source()).isEqualTo(TransactionSource.TOPUP_GATEWAY);
        assertThat(credit.calls.get(0).idempotencyKey()).isEqualTo("topup-" + ORDER_REF);
    }

    @Test
    void does_not_credit_while_the_payment_is_still_pending() {
        repository.store(pendingRequest());
        gateway.respondWith(order("PENDING_PAYMENT", PaymentStatus.PENDING));

        StepVerifier.create(handler.confirm(ORDER_REF))
                .assertNext(request -> {
                    assertThat(request.status()).isEqualTo(PaymentStatus.PENDING);
                    assertThat(request.confirmedAt()).isNull();
                })
                .verifyComplete();

        assertThat(credit.calls).isEmpty();
    }

    @Test
    void does_not_credit_twice_when_the_request_is_already_final() {
        repository.store(pendingRequest().withGatewayState(order("SUCCESS", PaymentStatus.COMPLETED), Instant.now()));

        StepVerifier.create(handler.confirm(ORDER_REF))
                .assertNext(request -> assertThat(request.status()).isEqualTo(PaymentStatus.COMPLETED))
                .verifyComplete();

        assertThat(credit.calls).isEmpty();
        assertThat(gateway.refreshCount).isZero();
    }

    @Test
    void a_concurrent_callback_that_already_credited_is_not_an_error() {
        repository.store(pendingRequest());
        gateway.respondWith(order("SUCCESS", PaymentStatus.COMPLETED));
        credit.failWithIdempotencyConflict = true;

        StepVerifier.create(handler.confirm(ORDER_REF))
                .assertNext(request -> assertThat(request.status()).isEqualTo(PaymentStatus.COMPLETED))
                .verifyComplete();

        assertThat(repository.updated.get()).isNotNull();
    }

    @Test
    void unknown_reference_is_rejected() {
        StepVerifier.create(handler.confirm("does-not-exist"))
                .expectErrorMessage("Aucune demande de paiement connue pour la référence does-not-exist")
                .verify();
    }

    private PaymentRequest pendingRequest() {
        Instant now = Instant.now();
        return new PaymentRequest(
                UUID.randomUUID(), TENANT, UUID.randomUUID(), MEMBER, null, ORDER_REF,
                "MYCOOLPAY", "MOBILE_MONEY", PaymentDirection.INBOUND,
                new BigDecimal("5000"), "XAF", "+237600000000", null, "https://pay.local/1",
                "idem-1", PaymentStatus.PENDING, "PENDING_PAYMENT", now, null, now.plusSeconds(1800));
    }

    private PaymentOrder order(String rawStatus, PaymentStatus status) {
        return new PaymentOrder(ORDER_REF, rawStatus, status, "MYCOOLPAY", "MOBILE_MONEY",
                "PSP-1", "https://pay.local/1", new BigDecimal("5000"), "XAF",
                Instant.now(), Instant.now());
    }

    private static final class FakePaymentRequestRepository implements PaymentRequestRepository {
        private final AtomicReference<PaymentRequest> stored = new AtomicReference<>();
        private final AtomicReference<PaymentRequest> updated = new AtomicReference<>();

        void store(PaymentRequest request) {
            stored.set(request);
        }

        @Override
        public Mono<PaymentRequest> save(PaymentRequest request) {
            stored.set(request);
            return Mono.just(request);
        }

        @Override
        public Mono<PaymentRequest> findByExternalRef(String externalRef) {
            PaymentRequest current = stored.get();
            return current != null && current.externalRef().equals(externalRef)
                    ? Mono.just(current)
                    : Mono.empty();
        }

        @Override
        public Mono<PaymentRequest> findById(UUID id) {
            return Mono.justOrEmpty(stored.get());
        }

        @Override
        public Mono<PaymentRequest> update(PaymentRequest request) {
            updated.set(request);
            stored.set(request);
            return Mono.just(request);
        }
    }

    private static final class FakeGateway implements PaymentGatewayPort {
        private PaymentOrder response;
        private int refreshCount;

        void respondWith(PaymentOrder order) {
            this.response = order;
        }

        @Override
        public Mono<PaymentOrder> initiate(PaymentOrderRequest request) {
            return Mono.justOrEmpty(response);
        }

        @Override
        public Mono<PaymentOrder> refresh(TenantId tenantId, String orderId) {
            refreshCount++;
            return Mono.justOrEmpty(response);
        }

        @Override
        public Mono<PaymentOrder> fetch(TenantId tenantId, String orderId) {
            return Mono.justOrEmpty(response);
        }
    }

    private record CreditCall(TenantId tenantId, UserId memberId, BigDecimal amount,
                              TransactionSource source, String referenceId, String idempotencyKey) {}

    private static final class RecordingCreditUseCase implements CreditWalletUseCase {
        private final List<CreditCall> calls = new ArrayList<>();
        private boolean failWithIdempotencyConflict;

        @Override
        public Mono<WalletCreditResult> credit(TenantId tenantId, UserId memberId, BigDecimal amount,
                                               TransactionSource source, String referenceId, String idempotencyKey) {
            if (failWithIdempotencyConflict) {
                return Mono.error(new IdempotencyConflictException(idempotencyKey));
            }
            calls.add(new CreditCall(tenantId, memberId, amount, source, referenceId, idempotencyKey));
            return Mono.just(new WalletCreditResult(null, amount, amount));
        }
    }
}
