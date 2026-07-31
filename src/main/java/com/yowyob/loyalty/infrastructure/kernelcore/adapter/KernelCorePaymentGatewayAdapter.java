package com.yowyob.loyalty.infrastructure.kernelcore.adapter;

import com.yowyob.loyalty.domain.shared.model.TenantId;
import com.yowyob.loyalty.domain.wallet.model.PaymentOrder;
import com.yowyob.loyalty.domain.wallet.model.PaymentOrderRequest;
import com.yowyob.loyalty.domain.wallet.port.out.PaymentGatewayPort;
import com.yowyob.loyalty.infrastructure.kernelcore.config.KernelCoreProperties;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelApiResponse;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelInitiatePaymentRequest;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelPaymentOrderDto;
import com.yowyob.loyalty.shared.exception.KernelCoreUnavailableException;
import com.yowyob.loyalty.shared.exception.PaymentGatewayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Passerelle de paiement adossée à Kernel Core (payment-gateway-controller).
 *
 * <ul>
 *   <li>POST /api/payments/orders — ouvre l'ordre, renvoie {@code redirectUrl}</li>
 *   <li>GET  /api/payments/orders/{id} — état connu de Kernel Core</li>
 *   <li>POST /api/payments/orders/{id}/refresh — force la re-synchro avec le provider</li>
 * </ul>
 *
 * <p>Kernel Core est ici l'agrégateur : c'est lui qui parle à MyCoolPay/Stripe et qui
 * détient les clés provider. Ce backend ne voit jamais les credentials du PSP.
 *
 * <p>Le tenant plateforme part en {@code X-Tenant-Id} et l'organisation du tenant loyalty en
 * {@code X-Organization-Id} — c'est bien l'identifiant d'organisation Kernel Core que ce
 * backend utilise comme {@code tenantId} (cf. {@link KernelCoreTenantAdapter}, qui résout un
 * tenant via GET /api/organizations/{tenantId}).
 */
public class KernelCorePaymentGatewayAdapter implements PaymentGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(KernelCorePaymentGatewayAdapter.class);

    private static final ParameterizedTypeReference<KernelApiResponse<KernelPaymentOrderDto>> ORDER_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient kernelCoreWebClient;
    private final KernelCoreTokenService tokenService;
    private final KernelCoreProperties properties;

    public KernelCorePaymentGatewayAdapter(WebClient kernelCoreWebClient,
                                           KernelCoreTokenService tokenService,
                                           KernelCoreProperties properties) {
        this.kernelCoreWebClient = kernelCoreWebClient;
        this.tokenService = tokenService;
        this.properties = properties;
    }

    @Override
    public Mono<PaymentOrder> initiate(PaymentOrderRequest request) {
        KernelCoreProperties.Payments payments = properties.getPayments();
        KernelInitiatePaymentRequest body = new KernelInitiatePaymentRequest(
                properties.getServiceClientId(),
                payments.getServiceCode(),
                request.idempotencyKey(),
                request.amount(),
                request.currency(),
                request.provider(),
                request.method().name(),
                request.payerReference(),
                request.description(),
                emptyToNull(payments.getCallbackUrl()));

        return serviceToken()
                .flatMap(token -> kernelCoreWebClient.post()
                        .uri("/api/payments/orders")
                        .headers(headers -> applyContext(headers, request.tenantId(), token))
                        .bodyValue(body)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, resp -> toGatewayError(resp, "initiation du paiement"))
                        .bodyToMono(ORDER_TYPE))
                .flatMap(response -> unwrap(response, "POST /api/payments/orders"))
                .map(this::toDomain)
                .doOnNext(order -> log.info("Ordre de paiement Kernel Core {} ouvert (statut brut={})",
                        order.id(), order.rawStatus()));
    }

    @Override
    public Mono<PaymentOrder> refresh(TenantId tenantId, String orderId) {
        return serviceToken()
                .flatMap(token -> kernelCoreWebClient.post()
                        .uri("/api/payments/orders/{id}/refresh", orderId)
                        .headers(headers -> applyContext(headers, tenantId, token))
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, resp -> toGatewayError(resp, "rafraîchissement du paiement"))
                        .bodyToMono(ORDER_TYPE))
                .flatMap(response -> unwrap(response, "POST /api/payments/orders/" + orderId + "/refresh"))
                .map(this::toDomain);
    }

    @Override
    public Mono<PaymentOrder> fetch(TenantId tenantId, String orderId) {
        return serviceToken()
                .flatMap(token -> kernelCoreWebClient.get()
                        .uri("/api/payments/orders/{id}", orderId)
                        .headers(headers -> applyContext(headers, tenantId, token))
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, resp -> toGatewayError(resp, "lecture du paiement"))
                        .bodyToMono(ORDER_TYPE))
                .flatMap(response -> unwrap(response, "GET /api/payments/orders/" + orderId))
                .map(this::toDomain);
    }

    /**
     * Le token service-to-service est optionnel (KERNEL_TOKEN_ENDPOINT souvent vide) : le
     * couple X-Client-Id/X-Api-Key porté par le WebClient suffit aux appels machine. On
     * émet donc une chaîne vide plutôt que rien, sinon la chaîne réactive s'arrête ici.
     */
    private Mono<String> serviceToken() {
        return tokenService.getServiceToken().defaultIfEmpty("");
    }

    private void applyContext(HttpHeaders headers, TenantId tenantId, String token) {
        if (token != null && !token.isBlank()) {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (properties.getTenantId() != null && !properties.getTenantId().isBlank()) {
            headers.set("X-Tenant-Id", properties.getTenantId());
        }
        if (tenantId != null) {
            headers.set("X-Organization-Id", tenantId.value().toString());
        }
    }

    private Mono<Throwable> toGatewayError(org.springframework.web.reactive.function.client.ClientResponse response,
                                           String action) {
        HttpStatusCode status = response.statusCode();
        return response.bodyToMono(String.class).defaultIfEmpty("")
                .map(body -> status.is5xxServerError()
                        ? new KernelCoreUnavailableException("Kernel Core indisponible pour " + action + " : " + body)
                        : new PaymentGatewayException("Kernel Core a refusé " + action + " (" + status.value() + ") : " + body));
    }

    private Mono<KernelPaymentOrderDto> unwrap(KernelApiResponse<KernelPaymentOrderDto> response, String call) {
        if (response == null || !response.isSuccess() || response.getData() == null) {
            String message = response == null ? "réponse vide" : response.getMessage();
            return Mono.error(new PaymentGatewayException("Réponse Kernel Core invalide pour " + call + " : " + message));
        }
        return Mono.just(response.getData());
    }

    private PaymentOrder toDomain(KernelPaymentOrderDto dto) {
        return new PaymentOrder(
                dto.getId(),
                dto.getStatus(),
                KernelPaymentStatusMapper.map(dto.getStatus()),
                dto.getProvider(),
                dto.getMethod(),
                dto.getProviderReference(),
                dto.getRedirectUrl(),
                dto.getAmount(),
                dto.getCurrency(),
                dto.getCreatedAt(),
                dto.getUpdatedAt());
    }

    private static String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
