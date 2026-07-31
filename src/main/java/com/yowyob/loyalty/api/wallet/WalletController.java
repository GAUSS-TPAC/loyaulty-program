package com.yowyob.loyalty.api.wallet;

import com.yowyob.loyalty.api.wallet.dto.request.ConfirmOtpRequest;
import com.yowyob.loyalty.api.wallet.dto.request.CreateWalletRequest;
import com.yowyob.loyalty.api.wallet.dto.request.CreditRequest;
import com.yowyob.loyalty.api.wallet.dto.request.DebitRequest;
import com.yowyob.loyalty.api.wallet.dto.request.FreezeRequest;
import com.yowyob.loyalty.api.wallet.dto.request.TopUpRequest;
import com.yowyob.loyalty.api.wallet.dto.response.DebitResponse;
import com.yowyob.loyalty.api.wallet.dto.response.TopUpResponse;
import com.yowyob.loyalty.api.wallet.dto.response.TopUpStatusResponse;
import com.yowyob.loyalty.api.wallet.dto.response.WalletResponse;
import com.yowyob.loyalty.api.wallet.dto.response.WalletTransactionResponse;
import com.yowyob.loyalty.domain.shared.model.UserId;
import com.yowyob.loyalty.domain.wallet.model.TransactionSource;
import com.yowyob.loyalty.domain.wallet.model.TransactionType;
import com.yowyob.loyalty.domain.wallet.port.in.*;
import com.yowyob.loyalty.domain.wallet.port.out.WalletPolicyRepository;
import com.yowyob.loyalty.shared.exception.ForbiddenException;
import com.yowyob.loyalty.shared.multitenancy.TenantContextHolder;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/members/{memberId}/wallet")
@Tag(name = "Wallet", description = "Portefeuille et soldes")
public class WalletController {

    private final CreateWalletUseCase createWalletUseCase;
    private final GetWalletUseCase getWalletUseCase;
    private final CreditWalletUseCase creditWalletUseCase;
    private final DebitWalletUseCase debitWalletUseCase;
    private final ConfirmOtpUseCase confirmOtpUseCase;
    private final FreezeWalletUseCase freezeWalletUseCase;
    private final UnfreezeWalletUseCase unfreezeWalletUseCase;
    private final GetTransactionHistoryUseCase getTransactionHistoryUseCase;
    private final InitiateTopUpUseCase initiateTopUpUseCase;
    private final ConfirmTopUpUseCase confirmTopUpUseCase;
    private final WalletPolicyRepository policyRepo;
    private final String defaultPaymentProvider;

    public WalletController(
            @Qualifier("createWalletHandler") CreateWalletUseCase createWalletUseCase,
            @Qualifier("getWalletHandler") GetWalletUseCase getWalletUseCase,
            @Qualifier("creditWalletHandler") CreditWalletUseCase creditWalletUseCase,
            @Qualifier("debitWalletHandler") DebitWalletUseCase debitWalletUseCase,
            @Qualifier("confirmOtpHandler") ConfirmOtpUseCase confirmOtpUseCase,
            @Qualifier("freezeWalletHandler") FreezeWalletUseCase freezeWalletUseCase,
            @Qualifier("unfreezeWalletHandler") UnfreezeWalletUseCase unfreezeWalletUseCase,
            @Qualifier("getTransactionHistoryHandler") GetTransactionHistoryUseCase getTransactionHistoryUseCase,
            @Qualifier("initiateTopUpHandler") InitiateTopUpUseCase initiateTopUpUseCase,
            @Qualifier("confirmTopUpHandler") ConfirmTopUpUseCase confirmTopUpUseCase,
            WalletPolicyRepository policyRepo,
            @Value("${app.kernel-core.payments.default-provider:MYCOOLPAY}") String defaultPaymentProvider) {
        this.createWalletUseCase = createWalletUseCase;
        this.getWalletUseCase = getWalletUseCase;
        this.creditWalletUseCase = creditWalletUseCase;
        this.debitWalletUseCase = debitWalletUseCase;
        this.confirmOtpUseCase = confirmOtpUseCase;
        this.freezeWalletUseCase = freezeWalletUseCase;
        this.unfreezeWalletUseCase = unfreezeWalletUseCase;
        this.getTransactionHistoryUseCase = getTransactionHistoryUseCase;
        this.initiateTopUpUseCase = initiateTopUpUseCase;
        this.confirmTopUpUseCase = confirmTopUpUseCase;
        this.policyRepo = policyRepo;
        this.defaultPaymentProvider = defaultPaymentProvider;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@memberOwnershipValidator.isOwnerOrAdmin(#memberId.toString())")
    public Mono<WalletResponse> createWallet(
            @PathVariable UUID memberId,
            @Valid @RequestBody CreateWalletRequest request) {
        UserId userId = UserId.of(memberId.toString());
        return TenantContextHolder.getTenantId()
                .flatMap(tenantId -> createWalletUseCase.createWallet(
                        tenantId, userId, request.currencyCode(), request.autoActivate(), null)
                        .flatMap(wallet -> policyRepo.findByTenant(tenantId)
                                .map(policy -> WalletResponse.from(wallet, policy))));
    }

    @GetMapping
    @PreAuthorize("@memberOwnershipValidator.isOwnerOrAdmin(#memberId.toString())")
    public Mono<WalletResponse> getWallet(@PathVariable UUID memberId) {
        UserId userId = UserId.of(memberId.toString());
        return TenantContextHolder.getTenantId()
                .flatMap(tenantId -> getWalletUseCase.getWallet(tenantId, userId)
                        .flatMap(wallet -> policyRepo.findByTenant(tenantId)
                                .map(policy -> WalletResponse.from(wallet, policy))));
    }

    @PostMapping("/credit")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public Mono<WalletResponse> credit(
            @PathVariable UUID memberId,
            @Valid @RequestBody CreditRequest request) {
        UserId userId = UserId.of(memberId.toString());
        TransactionSource source = TransactionSource.valueOf(request.source());
        return TenantContextHolder.getTenantId()
                .flatMap(tenantId -> creditWalletUseCase.credit(
                        tenantId, userId, request.amount(), source, request.referenceId(), request.idempotencyKey())
                        .flatMap(result -> policyRepo.findByTenant(tenantId)
                                .map(policy -> WalletResponse.from(result.updatedWallet(), policy))));
    }

    @PostMapping("/debit")
    @PreAuthorize("@memberOwnershipValidator.isOwnerOrAdmin(#memberId.toString())")
    public Mono<DebitResponse> debit(
            @PathVariable UUID memberId,
            @Valid @RequestBody DebitRequest request) {
        UserId userId = UserId.of(memberId.toString());
        return TenantContextHolder.getTenantId()
                .flatMap(tenantId -> debitWalletUseCase.debit(
                        tenantId, userId, request.amount(), request.description(),
                        request.orderReference(), request.idempotencyKey())
                        .flatMap(result -> policyRepo.findByTenant(tenantId)
                                .map(policy -> DebitResponse.from(result, policy))));
    }

    @PostMapping("/confirm-otp")
    @PreAuthorize("@memberOwnershipValidator.isOwnerOrAdmin(#memberId.toString())")
    public Mono<WalletResponse> confirmOtp(
            @PathVariable UUID memberId,
            @Valid @RequestBody ConfirmOtpRequest request) {
        UserId userId = UserId.of(memberId.toString());
        return TenantContextHolder.getTenantId()
                .flatMap(tenantId -> confirmOtpUseCase.confirmOtp(
                        tenantId, userId, request.challengeId(), request.otpCode(), request.idempotencyKey())
                        .flatMap(result -> policyRepo.findByTenant(tenantId)
                                .map(policy -> WalletResponse.from(result.updatedWallet(), policy))));
    }

    @PostMapping("/freeze")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public Mono<WalletResponse> freeze(
            @PathVariable UUID memberId,
            @Valid @RequestBody FreezeRequest request) {
        UserId userId = UserId.of(memberId.toString());
        return TenantContextHolder.getTenantId()
                .flatMap(tenantId -> freezeWalletUseCase.freeze(tenantId, userId, request.reason(), null)
                        .flatMap(wallet -> policyRepo.findByTenant(tenantId)
                                .map(policy -> WalletResponse.from(wallet, policy))));
    }

    @PostMapping("/unfreeze")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public Mono<WalletResponse> unfreeze(@PathVariable UUID memberId) {
        UserId userId = UserId.of(memberId.toString());
        return TenantContextHolder.getTenantId()
                .flatMap(tenantId -> unfreezeWalletUseCase.unfreeze(tenantId, userId, null)
                        .flatMap(wallet -> policyRepo.findByTenant(tenantId)
                                .map(policy -> WalletResponse.from(wallet, policy))));
    }

    @PostMapping("/top-up")
    @PreAuthorize("@memberOwnershipValidator.isOwnerOrAdmin(#memberId.toString())")
    public Mono<TopUpResponse> topUp(
            @PathVariable UUID memberId,
            @Valid @RequestBody TopUpRequest request) {
        UserId userId = UserId.of(memberId.toString());
        String provider = (request.provider() == null || request.provider().isBlank())
                ? defaultPaymentProvider
                : request.provider();
        return TenantContextHolder.getTenantId()
                .flatMap(tenantId -> initiateTopUpUseCase.initiateTopUp(
                        tenantId, userId, request.amount(), provider, request.method(),
                        request.payerReference(), request.idempotencyKey()))
                .map(TopUpResponse::from);
    }

    /**
     * Réconcilie une recharge avec la passerelle et crédite le wallet si le paiement est
     * confirmé. Sert de repli manuel quand le callback de la passerelle n'est pas arrivé ;
     * rejouable sans risque de double crédit.
     */
    @PostMapping("/top-ups/{reference}/confirm")
    @PreAuthorize("@memberOwnershipValidator.isOwnerOrAdmin(#memberId.toString())")
    public Mono<TopUpStatusResponse> confirmTopUp(
            @PathVariable UUID memberId,
            @PathVariable String reference) {
        return confirmTopUpUseCase.confirm(reference)
                // La recharge est toujours créditée à son propriétaire légitime, mais renvoyer
                // l'état d'une recharge tierce exposerait montant et numéro de payeur d'autrui.
                .flatMap(request -> request.memberId().value().equals(memberId)
                        ? Mono.just(TopUpStatusResponse.from(request))
                        : Mono.error(new ForbiddenException("Cette recharge n'appartient pas à ce membre")));
    }

    @GetMapping("/transactions")
    @PreAuthorize("@memberOwnershipValidator.isOwnerOrAdmin(#memberId.toString())")
    public Flux<WalletTransactionResponse> getTransactions(
            @PathVariable UUID memberId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UserId userId = UserId.of(memberId.toString());
        TransactionType typeFilter = type != null ? TransactionType.valueOf(type) : null;
        TransactionSource sourceFilter = source != null ? TransactionSource.valueOf(source) : null;
        return TenantContextHolder.getTenantId()
                .flatMapMany(tenantId -> getTransactionHistoryUseCase.getHistory(
                        tenantId, userId, typeFilter, sourceFilter, from, to, page, size))
                .map(WalletTransactionResponse::from);
    }
}
