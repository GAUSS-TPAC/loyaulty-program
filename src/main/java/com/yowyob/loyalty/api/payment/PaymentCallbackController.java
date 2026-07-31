package com.yowyob.loyalty.api.payment;

import com.yowyob.loyalty.domain.wallet.port.in.ConfirmTopUpUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Point d'entrée des notifications de la passerelle Kernel Core
 * ({@code callbackUrl} envoyé à POST /api/payments/orders).
 *
 * <p>Le corps du callback n'est jamais cru : on n'en extrait qu'une référence d'ordre, puis
 * {@link ConfirmTopUpUseCase} rappelle la passerelle pour connaître l'état réel. Un callback
 * forgé ne peut donc pas créditer un wallet — au pire il déclenche une re-synchronisation.
 *
 * <p>La forme exacte du payload n'est pas publiée dans l'OpenAPI de Kernel Core (le endpoint
 * miroir {@code /api/payments/orders/callbacks/{provider}} y accepte une simple chaîne), d'où
 * la recherche de la référence parmi les noms de champs usuels, complétée par le paramètre de
 * requête {@code reference} en repli.
 */
@RestController
@RequestMapping("/api/v1/payments/callbacks")
@Tag(name = "Paiements", description = "Notifications de la passerelle de paiement")
public class PaymentCallbackController {

    private static final Logger log = LoggerFactory.getLogger(PaymentCallbackController.class);

    /** Noms de champ candidats, du plus spécifique au plus générique. */
    private static final List<String> REFERENCE_KEYS = List.of(
            "orderId", "order_id", "paymentOrderId", "payment_order_id",
            "externalRef", "external_ref", "reference", "transaction_ref", "transactionRef", "id");

    private final ConfirmTopUpUseCase confirmTopUpUseCase;

    public PaymentCallbackController(@Qualifier("confirmTopUpHandler") ConfirmTopUpUseCase confirmTopUpUseCase) {
        this.confirmTopUpUseCase = confirmTopUpUseCase;
    }

    @PostMapping("/kernel-core")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<Map<String, String>> onKernelCoreCallback(
            @RequestBody(required = false) Map<String, Object> payload,
            @RequestParam(required = false) String reference) {

        String orderRef = reference != null && !reference.isBlank() ? reference : extractReference(payload);
        if (orderRef == null) {
            log.warn("Callback paiement sans référence exploitable, champs reçus: {}",
                    payload == null ? "aucun" : payload.keySet());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Référence d'ordre de paiement absente du callback");
        }

        return confirmTopUpUseCase.confirm(orderRef)
                .doOnNext(request -> log.info("Callback paiement {} traité, statut={}",
                        orderRef, request.status()))
                .map(request -> Map.of(
                        "reference", request.externalRef(),
                        "status", request.status().name()));
    }

    private String extractReference(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        for (String key : REFERENCE_KEYS) {
            Object value = payload.get(key);
            if (value instanceof String str && !str.isBlank()) {
                return str;
            }
        }
        // Certains agrégateurs imbriquent la charge utile sous "data" ou "transaction".
        for (String nested : List.of("data", "transaction", "order", "payment")) {
            if (payload.get(nested) instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                String found = extractReference((Map<String, Object>) map);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
