package com.yowyob.loyalty.api.bonification;

import com.yowyob.loyalty.api.bonification.dto.BonificationStatusResponse;
import com.yowyob.loyalty.api.bonification.dto.BonificationTransactionResponse;
import com.yowyob.loyalty.api.bonification.dto.SubmitBonificationTransactionRequest;
import com.yowyob.loyalty.application.bonification.BonificationCredentialsResolver;
import com.yowyob.loyalty.application.bonification.handler.SubmitBonificationTransactionHandler;
import com.yowyob.loyalty.domain.bonification.model.BonificationTransactionRequest;
import com.yowyob.loyalty.domain.bonification.port.out.BonificationPort;
import com.yowyob.loyalty.infrastructure.bonification.config.BonificationProperties;
import com.yowyob.loyalty.shared.multitenancy.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.yowyob.loyalty.api.bonification.dto.BonificationCredentialsRequest;
import com.yowyob.loyalty.api.bonification.dto.BonificationCredentialsResponse;
import com.yowyob.loyalty.application.bonification.BonificationCredentialsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/bonification")
@Tag(name = "Bonification", description = "Intégration API bonification externe")
public class BonificationController {

    private final BonificationPort bonificationPort;
    private final BonificationProperties properties;
    private final BonificationCredentialsResolver credentialsResolver;
    private final SubmitBonificationTransactionHandler submitHandler;
    private final BonificationCredentialsService credentialsService;

    public BonificationController(
            BonificationPort bonificationPort,
            BonificationProperties properties,
            BonificationCredentialsResolver credentialsResolver,
            SubmitBonificationTransactionHandler submitHandler,
            BonificationCredentialsService credentialsService
    ) {
        this.bonificationPort = bonificationPort;
        this.properties = properties;
        this.credentialsResolver = credentialsResolver;
        this.submitHandler = submitHandler;
        this.credentialsService = credentialsService;
    }

    // ── Identifiants du tenant (écriture seule) ──────────────────────────────────────

    @GetMapping("/credentials")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Identifiants Bonification du tenant",
            description = "Indique si le tenant a ses propres identifiants et lequel est utilisé. "
                    + "Le mot de passe n'est jamais renvoyé.")
    public Mono<BonificationCredentialsResponse> credentials() {
        return TenantContextHolder.getTenantId()
                .flatMap(credentialsResolver::resolve)
                .zipWith(credentialsService.isConfigured())
                .map(both -> new BonificationCredentialsResponse(both.getT2(), both.getT1().login()));
    }

    @PutMapping("/credentials")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Enregistrer les identifiants Bonification du tenant",
            description = "Le mot de passe est chiffré avant stockage (AES-GCM). Refusé tant "
                    + "qu'aucune clé de chiffrement n'est configurée côté déploiement.")
    public Mono<BonificationCredentialsResponse> saveCredentials(
            @Valid @RequestBody BonificationCredentialsRequest request) {
        return credentialsService.update(request.username(), request.password())
                .map(username -> new BonificationCredentialsResponse(true, username));
    }

    @DeleteMapping("/credentials")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Effacer les identifiants du tenant",
            description = "Le tenant retombe sur les identifiants globaux du déploiement "
                    + "(BONIFICATION_LOGIN/PASSWORD).")
    public Mono<Void> deleteCredentials() {
        return credentialsService.clear();
    }

    @GetMapping("/status")
    public Mono<BonificationStatusResponse> status() {
        if (!properties.isEnabled()) {
            return Mono.just(new BonificationStatusResponse(
                    false, false, properties.getBaseUrl(),
                    "Intégration désactivée (app.bonification.enabled=false)"
            ));
        }

        return TenantContextHolder.getTenantId()
                .flatMap(tenantId -> credentialsResolver.resolve(tenantId)
                        .flatMap(credentials -> {
                            if (!credentials.isConfigured()) {
                                return Mono.just(new BonificationStatusResponse(
                                        true, false, properties.getBaseUrl(),
                                        "Identifiants manquants : BONIFICATION_LOGIN/PASSWORD ou TenantConfig"
                                ));
                            }
                            return bonificationPort.verifyCredentials(credentials)
                                    .map(ok -> new BonificationStatusResponse(
                                            true,
                                            ok,
                                            properties.getBaseUrl(),
                                            ok ? "Authentification API Bonification OK" : "Authentification refusée"
                                    ));
                        }))
                .switchIfEmpty(bonificationPort.checkConnectivity()
                        .map(reachable -> new BonificationStatusResponse(
                                true,
                                reachable,
                                properties.getBaseUrl(),
                                reachable
                                        ? "API joignable (BONIFICATION_LOGIN/PASSWORD dans .env)"
                                        : "API injoignable ou credentials .env manquants"
                        )));
    }

    @PostMapping("/transactions")
    public Mono<BonificationTransactionResponse> submitTransaction(
            @Valid @RequestBody SubmitBonificationTransactionRequest request
    ) {
        BonificationTransactionRequest domainRequest = request.debit()
                ? BonificationTransactionRequest.debit(request.amount(), request.clientLogin())
                : BonificationTransactionRequest.credit(request.amount(), request.clientLogin());

        return submitHandler.submit(domainRequest)
                .map(result -> new BonificationTransactionResponse(
                        result.transactionId(),
                        result.amount(),
                        result.clientLogin(),
                        result.debit(),
                        result.status(),
                        result.message()
                ));
    }
}
