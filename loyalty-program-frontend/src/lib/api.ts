/**
 * api.ts — Couche d'accès centralisée au backend Loyalty Spring Boot
 *
 * Toutes les requêtes passent par /backend/* (proxy Next.js → http://localhost:8081)
 * Le token JWT est automatiquement injecté depuis sessionStorage.
 */

const BASE = "/backend";

// ─── Utilitaires de base ────────────────────────────────────────────────────

/** Corps RFC 7807 renvoyé par le GlobalExceptionHandler du backend. */
export interface ProblemDetails {
    type?: string;
    /** Code d'erreur applicatif (ex: ORGANIZATION_SELECTION_REQUIRED). */
    title?: string;
    status?: number;
    detail?: string;
    /** Propriétés additionnelles de l'erreur (ex: organizations pour la sélection). */
    errors?: Record<string, unknown>;
}

export class ApiError extends Error {
    constructor(
        public readonly status: number,
        message: string,
        public readonly problem?: ProblemDetails
    ) {
        super(message);
        this.name = "ApiError";
    }
}

/**
 * Le backend traite un JWT admin (claim organization_id) et une clé API tenant
 * (X-Api-Key) comme deux credentials équivalents, résolus tous deux vers
 * ROLE_TENANT_ADMIN sur le tenant appelant. On envoie le JWT s'il est présent
 * (login email/mot de passe), sinon la clé API (portail développeur "coller sa clé").
 */
function getAuthHeaders(): HeadersInit {
    const token =
        typeof window !== "undefined"
            ? sessionStorage.getItem("loyalty_jwt_token")
            : null;
    const apiKey =
        typeof window !== "undefined"
            ? sessionStorage.getItem("loyalty_dev_api_key")
            : null;
    const organizationId =
        typeof window !== "undefined"
            ? sessionStorage.getItem("loyalty_organization_id")
            : null;
    return {
        "Content-Type": "application/json",
        ...(token
            ? { Authorization: `Bearer ${token}` }
            : apiKey
                ? { "X-Api-Key": apiKey }
                : {}),
        ...(organizationId ? { "X-Organization-Id": organizationId } : {}),
    };
}

async function requestWithHeaders<T>(
    getHeaders: () => HeadersInit,
    method: string,
    path: string,
    body?: unknown,
    extraHeaders?: HeadersInit
): Promise<T> {
    const res = await fetch(`${BASE}${path}`, {
        method,
        headers: { ...getHeaders(), ...(extraHeaders ?? {}) },
        body: body !== undefined ? JSON.stringify(body) : undefined,
    });

    if (!res.ok) {
        const text = await res.text().catch(() => res.statusText);
        let problem: ProblemDetails | undefined;
        try {
            problem = JSON.parse(text);
        } catch {
            // corps non JSON (proxy, HTML d'erreur…) : on garde le texte brut
        }
        // Le backend detaille les champs invalides dans `errors` (ex:
        // {"email":"must not be blank"}). Sans ca l'utilisateur ne lit que
        // "Erreur de validation des champs", sans savoir lequel corriger.
        // On ignore les valeurs non-textuelles : `errors` transporte aussi des
        // structures (ex: `organizations` sur ORGANIZATION_SELECTION_REQUIRED).
        const fieldErrors = Object.entries(problem?.errors ?? {})
            .filter(([, msg]) => typeof msg === "string")
            .map(([field, msg]) => `${field} : ${msg}`);

        throw new ApiError(
            res.status,
            [problem?.detail ?? `[${res.status}] ${text}`, ...fieldErrors].join(" — "),
            problem
        );
    }

    // Certaines réponses (ex: Spring Boot Actuator) utilisent un content-type
    // "application/vnd.spring-boot.actuator.v3+json" et non "application/json" strict.
    const contentType = res.headers.get("content-type");
    if (contentType?.includes("json")) {
        return res.json();
    }
    return null as T;
}

const request = <T>(method: string, path: string, body?: unknown, extraHeaders?: HeadersInit) =>
    requestWithHeaders<T>(getAuthHeaders, method, path, body, extraHeaders);

const get = <T>(path: string) => request<T>("GET", path);
const post = <T>(path: string, body: unknown, headers?: HeadersInit) =>
    request<T>("POST", path, body, headers);
const patch = <T>(path: string, body?: unknown) =>
    request<T>("PATCH", path, body);

/**
 * Console plateforme (/api/v1/admin/platform/**) : credential distinct, non
 * rattaché à un tenant (secret statique X-Platform-Admin-Key), donc pas
 * mélangé avec getAuthHeaders (JWT/clé API tenant).
 */
function getPlatformAdminHeaders(): HeadersInit {
    const key =
        typeof window !== "undefined"
            ? sessionStorage.getItem("loyalty_platform_admin_key")
            : null;
    return {
        "Content-Type": "application/json",
        ...(key ? { "X-Platform-Admin-Key": key } : {}),
    };
}

const getPlatform = <T>(path: string) =>
    requestWithHeaders<T>(getPlatformAdminHeaders, "GET", path);
const put = <T>(path: string, body?: unknown) =>
    request<T>("PUT", path, body);
const del = <T>(path: string) => request<T>("DELETE", path);

// ─── Types partagés ─────────────────────────────────────────────────────────

export interface WalletResponse {
    id: string;
    memberId: string;
    tenantId: string;
    balance: number;
    currencyCode: string;
    currencySymbol: string;
    status: "ACTIVE" | "PENDING_KYC" | "FROZEN" | "CLOSED";
    dailySpendCap: number | null;
    maxBalance: number | null;
    otpThreshold: number | null;
    kycRequiredForWithdrawal: boolean;
    createdAt: string;
    updatedAt: string;
}

export interface WalletTransaction {
    id: string;
    type: string;
    source: string;
    amount: number;
    balanceBefore: number;
    balanceAfter: number;
    status: string;
    createdAt: string;
}

export interface PointsAccountResponse {
    availablePoints: number;
    lifetimeEarned: number;
    lifetimeSpent: number;
    tierLevel: TierLevel;
    tierMultiplier: number;
}

export type TierLevel = "BRONZE" | "SILVER" | "GOLD" | "PLATINUM";

export interface PointsTransactionResponse {
    id: string;
    type: string;
    amount: number;
    balanceAfter: number;
    source: string;
    ruleId: string | null;
    createdAt: string;
    metadata: Record<string, unknown>;
}

export interface MemberTierResponse {
    tierLevel: TierLevel;
    multiplier: number;
    reachedAt: string;
    validUntil: string | null;
}

export interface RuleConditionDto {
    type: string;
    operator: string;
    thresholdValue: unknown;
    windowType?: string | null;
    counterKey?: string | null;
}

export interface RuleEffectDto {
    type: string;
    params: Record<string, unknown>;
}

export interface RuleTriggerDto {
    eventType: string;
}

export interface RuleResponse {
    id: string;
    name: string;
    description: string;
    trigger: RuleTriggerDto;
    conditions: RuleConditionDto[];
    effects: RuleEffectDto[];
    priority: number;
    status: "DRAFT" | "ACTIVE" | "SUSPENDED" | "ARCHIVED";
    validFrom: string;
    validUntil: string | null;
    tenantId: string;
    createdAt: string;
}

export interface CreateRuleRequest {
    name: string;
    description: string;
    trigger: RuleTriggerDto;
    conditions: RuleConditionDto[];
    effects: RuleEffectDto[];
    priority: number;
    validFrom: string;
    validUntil?: string | null;
}

export interface IncomingEventRequest {
    eventType: string;
    memberId: string;
    /** ISO-8601 ; obligatoire côté backend (IncomingEventRequest.occurredAt est @NotNull). */
    occurredAt: string;
    /** Sac libre de données métier, ex. { amount: 49.90 } — pas de champs top-level comme "amount". */
    payload?: Record<string, unknown>;
}

export interface EventProcessingResponse {
    eventId: string;
    memberId: string;
    pointsAwarded: number;
    rulesApplied: string[];
    processed: boolean;
    message: string;
}

export interface AdjustPointsRequest {
    amount: number;
    debit: boolean;
    reason: string;
}

export interface AdjustPointsResponse {
    availablePoints: number;
    lifetimeEarned: number;
    lifetimeSpent: number;
    tierLevel: string;
    tierMultiplier: number;
}

export interface HealthResponse {
    status: string;
    components?: Record<string, { status: string }>;
}

export type ApiKeyMode = "LIVE" | "TEST";

export interface ApiKeyResponse {
    id: string;
    name: string;
    keyPrefix: string;
    mode: ApiKeyMode;
    active: boolean;
    createdAt: string;
    lastUsedAt: string | null;
    rawKey?: string;
    ownerId: string | null;
}

export interface CreateApiKeyRequest {
    name: string;
    mode?: ApiKeyMode;
}

export interface AccessResponse {
    tenantAdmin: boolean;
}

export type WebhookDeliveryStatus = "PENDING" | "SUCCEEDED" | "FAILED" | "EXHAUSTED";

export interface WebhookEndpointResponse {
    id: string;
    url: string;
    description: string | null;
    eventTypes: string[];
    active: boolean;
    createdAt: string;
    updatedAt: string;
    secret?: string;
}

export interface CreateWebhookEndpointRequest {
    url: string;
    description?: string;
    eventTypes: string[];
}

export interface UpdateWebhookEndpointRequest {
    url?: string;
    description?: string;
    eventTypes?: string[];
    active?: boolean;
}

export interface WebhookDeliveryResponse {
    id: string;
    endpointId: string;
    eventType: string;
    status: WebhookDeliveryStatus;
    httpStatusCode: number | null;
    responseSnippet: string | null;
    attemptCount: number;
    createdAt: string;
    deliveredAt: string | null;
}

export interface TestPingResponse {
    success: boolean;
    httpStatus: number | null;
    responseSnippet: string | null;
}

// ─── Types Applications d'intégration (clé publique + clé privée + callback) ─

export interface ApplicationResponse {
    id: string;
    name: string;
    description: string | null;
    websiteUrl: string | null;
    logoUrl: string | null;
    publicKey: string;
    privateKeyPrefix: string | null;
    mode: ApiKeyMode;
    active: boolean;
    webhookEndpointId: string | null;
    callbackUrl: string | null;
    createdAt: string;
    updatedAt: string;
    /** Clé privée complète — renvoyée UNE SEULE FOIS à la création / rotation. */
    privateKey?: string | null;
    /** Secret webhook complet — renvoyé UNE SEULE FOIS à la création du callback / rotation. */
    webhookSecret?: string | null;
}

export interface CreateApplicationRequest {
    name: string;
    description?: string;
    websiteUrl?: string;
    logoUrl?: string;
    mode?: ApiKeyMode;
    callbackUrl?: string;
    eventTypes?: string[];
}

export interface UpdateApplicationRequest {
    name?: string;
    description?: string;
    websiteUrl?: string;
    logoUrl?: string;
    /** Chaîne vide = supprimer le callback ; URL = créer/mettre à jour l'endpoint. */
    callbackUrl?: string;
    eventTypes?: string[];
}

// ─── API Wallet ──────────────────────────────────────────────────────────────

export interface WalletPolicyResponse {
    currencyName: string;
    currencySymbol: string;
    /** Correspondance des points : 1 point = exchangeRate unité(s) de monnaie */
    exchangeRate: number;
    dailySpendCap: number | null;
    maxBalance: number | null;
    maxTopupPerTransaction: number | null;
    minWithdrawal: number | null;
    withdrawalDelayHours: number;
    otpThreshold: number | null;
    kycRequiredForWithdrawal: boolean;
    expiryDays: number | null;
}

export const walletApi = {
    /** GET /api/v1/wallet — Consulter le wallet du membre connecté */
    getWallet: () => get<WalletResponse>("/api/v1/wallet"),

    /** GET /api/v1/wallet/transactions?page=&size= — Historique des transactions */
    getTransactions: (page = 0, size = 20) =>
        get<WalletTransaction[]>(
            `/api/v1/wallet/transactions?page=${page}&size=${size}`
        ),

    /** GET /api/v1/wallet/policy — Politique wallet du tenant (correspondance des points) */
    getPolicy: () => get<WalletPolicyResponse>("/api/v1/wallet/policy"),

    /** PUT /api/v1/wallet/policy/points-conversion — Modifier la correspondance des points */
    updatePointsConversion: (body: {
        currencyName: string;
        currencySymbol: string;
        exchangeRate: number;
    }) => put<WalletPolicyResponse>("/api/v1/wallet/policy/points-conversion", body),
};

// ─── API Members / Loyalty ───────────────────────────────────────────────────

export const memberApi = {
    /** GET /api/v1/members/{id}/points — Solde de points + tier */
    getPoints: (memberId: string) =>
        get<PointsAccountResponse>(`/api/v1/members/${memberId}/points`),

    /** GET /api/v1/members/{id}/points/history?page=&size= — Historique des points */
    getPointsHistory: (memberId: string, page = 0, size = 20) =>
        get<PointsTransactionResponse[]>(
            `/api/v1/members/${memberId}/points/history?page=${page}&size=${size}`
        ),

    /** GET /api/v1/members/{id}/tier — Niveau de fidélité */
    getTier: (memberId: string) =>
        get<MemberTierResponse>(`/api/v1/members/${memberId}/tier`),

    /** GET /api/v1/members/{id}/wallet — Wallet d'un membre (admin ou propriétaire) */
    getWallet: (memberId: string) =>
        get<WalletResponse>(`/api/v1/members/${memberId}/wallet`),

    /** GET /api/v1/members/{id}/wallet/transactions?page=&size= — Historique du wallet */
    getWalletTransactions: (memberId: string, page = 0, size = 20) =>
        get<WalletTransaction[]>(
            `/api/v1/members/${memberId}/wallet/transactions?page=${page}&size=${size}`
        ),

    /** POST /api/v1/members/{id}/wallet/freeze — Geler le wallet (admin) */
    freezeWallet: (memberId: string, reason: string) =>
        post<WalletResponse>(`/api/v1/members/${memberId}/wallet/freeze`, { reason }),

    /** POST /api/v1/members/{id}/wallet/unfreeze — Dégeler le wallet (admin) */
    unfreezeWallet: (memberId: string) =>
        post<WalletResponse>(`/api/v1/members/${memberId}/wallet/unfreeze`, {}),
};

// ─── API Règles de fidélité (Admin) ─────────────────────────────────────────

export const rulesApi = {
    /** GET /api/v1/admin/rules — Lister toutes les règles du tenant */
    listRules: () => get<RuleResponse[]>("/api/v1/admin/rules"),

    /** GET /api/v1/admin/rules/{id} — Détail d'une règle */
    getRule: (ruleId: string) =>
        get<RuleResponse>(`/api/v1/admin/rules/${ruleId}`),

    /** POST /api/v1/admin/rules — Créer une règle */
    createRule: (data: CreateRuleRequest) =>
        post<RuleResponse>("/api/v1/admin/rules", data),

    /** PATCH /api/v1/admin/rules/{id}/activate — Activer une règle */
    activateRule: (ruleId: string) =>
        patch<RuleResponse>(`/api/v1/admin/rules/${ruleId}/activate`),

    /** PATCH /api/v1/admin/rules/{id}/archive — Archiver (désactiver) une règle */
    archiveRule: (ruleId: string) =>
        patch<RuleResponse>(`/api/v1/admin/rules/${ruleId}/archive`),
};

// ─── API Événements ──────────────────────────────────────────────────────────

export const eventsApi = {
    /**
     * POST /api/v1/events — Soumettre un événement déclenchant le moteur de règles
     * Supporte un header Idempotency-Key optionnel
     */
    processEvent: (data: IncomingEventRequest, idempotencyKey?: string) =>
        post<EventProcessingResponse>("/api/v1/events", data, {
            ...(idempotencyKey ? { "Idempotency-Key": idempotencyKey } : {}),
        }),
};

// ─── API Bonification (ajustement interne des points) ───────────────────────

export const bonificationApi = {
    /** POST /api/v1/members/{id}/points/adjust — Crédit/débit manuel de points (admin) */
    adjustPoints: (memberId: string, data: AdjustPointsRequest) =>
        post<AdjustPointsResponse>(
            `/api/v1/members/${memberId}/points/adjust`,
            data
        ),
};

// ─── API Système / Santé ─────────────────────────────────────────────────────

export const systemApi = {
    /** GET /actuator/health — Santé de l'application */
    health: () => get<HealthResponse>("/actuator/health"),
};

// ─── Types Codes Promo ───────────────────────────────────────────────────────

export interface PromoCampaignResponse {
    id: string;
    tenantId: string;
    code: string;
    name: string;
    discountType: "PERCENTAGE" | "FIXED_AMOUNT" | "FREE_ITEM";
    discountValue: number;
    minOrderAmount: number | null;
    maxUses: number;
    perMemberLimit: number;
    startDate: string;
    endDate: string | null;
    active: boolean;
    createdAt: string;
}

export interface CreatePromoRequest {
    code: string;
    name: string;
    discountType: string;
    discountValue: number;
    minOrderAmount?: number;
    maxUses: number;
    perMemberLimit: number;
    startDate: string;
    endDate?: string;
}

export interface ValidatePromoResponse {
    valid: boolean;
    discountApplied: number;
    campaignName: string;
    message: string;
}

// ─── Types Campagnes ─────────────────────────────────────────────────────────

export interface CampaignResponse {
    id: string;
    tenantId: string;
    name: string;
    description: string | null;
    campaignType: "BONUS_MULTIPLIER" | "FLAT_BONUS";
    targetEventType: string | null;
    bonusMultiplier: number | null;
    bonusPoints: number | null;
    startDate: string;
    endDate: string | null;
    status: "DRAFT" | "ACTIVE" | "PAUSED" | "COMPLETED" | "CANCELLED";
    createdAt: string;
}

export interface CreateCampaignRequest {
    name: string;
    description?: string;
    campaignType: string;
    targetEventType?: string;
    bonusMultiplier?: number;
    bonusPoints?: number;
    startDate: string;
    endDate?: string;
}

// ─── Types Abonnements ───────────────────────────────────────────────────────

export interface PlanFeaturesResponse {
    maxRules: number;
    maxMembers: number;
    maxEventsPerMonth: number;
    referralEnabled: boolean;
    campaignsEnabled: boolean;
    promoCodesEnabled: boolean;
    analyticsEnabled: boolean;
}

export interface SubscriptionPlanResponse {
    id: string;
    code: string;
    name: string;
    description: string | null;
    priceMonthly: number;
    priceYearly: number;
    currency: string;
    features: PlanFeaturesResponse;
    active: boolean;
    createdAt: string;
}

export interface TenantSubscriptionResponse {
    id: string;
    tenantId: string;
    planId: string;
    status: "TRIAL" | "ACTIVE" | "PAST_DUE" | "CANCELLED" | "EXPIRED";
    billingCycle: "MONTHLY" | "YEARLY";
    currentPeriodStart: string;
    currentPeriodEnd: string;
    trialEndDate: string | null;
    cancelledAt: string | null;
    createdAt: string;
}

export interface InvoiceResponse {
    id: string;
    tenantId: string;
    subscriptionId: string;
    planId: string;
    amount: number;
    currency: string;
    status: "PENDING" | "PAID" | "FAILED" | "VOID";
    periodStart: string;
    periodEnd: string;
    dueDate: string;
    paidAt: string | null;
    createdAt: string;
}

// ─── API Codes Promo ─────────────────────────────────────────────────────────

export const promoApi = {
    listAll: () => get<PromoCampaignResponse[]>("/api/v1/promo/admin/campaigns"),
    listActive: () => get<PromoCampaignResponse[]>("/api/v1/promo/campaigns"),
    create: (data: CreatePromoRequest) =>
        post<PromoCampaignResponse>("/api/v1/promo/admin/campaigns", data),
    activate: (id: string) =>
        patch<PromoCampaignResponse>(`/api/v1/promo/admin/campaigns/${id}/activate`),
    deactivate: (id: string) =>
        patch<PromoCampaignResponse>(`/api/v1/promo/admin/campaigns/${id}/deactivate`),
    validate: (code: string, orderAmount: number) =>
        post<ValidatePromoResponse>("/api/v1/promo/validate", { code, orderAmount }),
};

// ─── API Campagnes ───────────────────────────────────────────────────────────

export const campaignApi = {
    listAll: () => get<CampaignResponse[]>("/api/v1/campaigns"),
    listActive: () => get<CampaignResponse[]>("/api/v1/campaigns/active"),
    create: (data: CreateCampaignRequest) =>
        post<CampaignResponse>("/api/v1/campaigns", data),
    activate: (id: string) =>
        patch<CampaignResponse>(`/api/v1/campaigns/${id}/activate`),
    pause: (id: string) =>
        patch<CampaignResponse>(`/api/v1/campaigns/${id}/pause`),
    cancel: (id: string) =>
        patch<CampaignResponse>(`/api/v1/campaigns/${id}/cancel`),
};

// ─── API Abonnements ─────────────────────────────────────────────────────────

export const subscriptionApi = {
    listPlans: () => get<SubscriptionPlanResponse[]>("/api/v1/subscription-plans"),
    getMySubscription: () => get<TenantSubscriptionResponse>("/api/v1/subscriptions/me"),
    getMyInvoices: () => get<InvoiceResponse[]>("/api/v1/subscriptions/me/invoices"),
    subscribe: (planId: string, billingCycle: string) =>
        post<TenantSubscriptionResponse>("/api/v1/subscriptions", { planId, billingCycle }),
    startTrial: (planId: string, trialDays = 14) =>
        post<TenantSubscriptionResponse>("/api/v1/subscriptions/trial", { planId, trialDays }),
    changePlan: (newPlanId: string) =>
        patch<TenantSubscriptionResponse>("/api/v1/subscriptions/me/plan", { newPlanId }),
    cancel: () => request<void>("DELETE", "/api/v1/subscriptions/me"),
};

// ─── API Console Plateforme (cross-tenant, secret statique) ──────────────────

export interface PlatformTenantResponse {
    tenantId: string;
    tenantName: string;
    subscriptionStatus: string;
    planCode: string;
    planName: string;
    trialEndDate: string | null;
    currentPeriodEnd: string | null;
    totalPaidAmount: number;
    currency: string;
    totalPointsGenerated: number;
}

export const platformApi = {
    /** GET /api/v1/admin/platform/tenants — Organisations abonnées au service loyalty */
    listTenants: () => getPlatform<PlatformTenantResponse[]>("/api/v1/admin/platform/tenants"),
};

// ─── API Auth (Portail Admin) ─────────────────────────────────────────────────

export interface LoginRequest {
    email: string;
    password: string;
    /** Organisation KernelCore à sélectionner ; requis si l'acteur en a plusieurs (voir 400 ORGANIZATION_SELECTION_REQUIRED). */
    organizationId?: string;
}

export interface LoginResponse {
    token: string;
    /** À renvoyer sur chaque appel suivant via le header X-Organization-Id. */
    organizationId: string;
    organizationCode: string;
    organizationName: string;
    /** true si le compte a le MFA actif : un code a été envoyé par email, à confirmer via confirmMfa. */
    mfaRequired?: boolean;
    mfaToken?: string;
    mfaChannel?: string;
}

export interface ConfirmMfaRequest {
    mfaToken: string;
    code: string;
    organizationId?: string;
}

export interface RegisterRequest {
    firstName: string;
    lastName: string;
    email: string;
    password: string;
}

export interface RegisterResponse {
    email: string;
    /** Typiquement "EMAIL_VERIFICATION_REQUIRED" : le login échouera tant que l'email n'est pas confirmé. */
    status: string;
    emailVerified: boolean;
}

export const authApi = {
    /** POST /api/v1/auth/login — Connexion admin par identifiant/mot de passe (KernelCore).
     *  Peut retourner mfaRequired=true + mfaToken au lieu du token. */
    login: (data: LoginRequest) => post<LoginResponse>("/api/v1/auth/login", data),

    /** POST /api/v1/auth/login/mfa — Deuxième étape : confirme le code OTP reçu par email */
    confirmMfa: (data: ConfirmMfaRequest) => post<LoginResponse>("/api/v1/auth/login/mfa", data),

    /** POST /api/v1/auth/register — Inscription admin (crée le compte via KernelCore, vérification email requise avant login) */
    register: (data: RegisterRequest) => post<RegisterResponse>("/api/v1/auth/register", data),
};

// ─── API Accès (rôle de l'utilisateur courant) ────────────────────────────────

export const accessApi = {
    /** GET /api/access/me — Indique si l'utilisateur courant est admin du tenant ou développeur */
    me: () => get<AccessResponse>("/api/access/me"),
};

// ─── API Clés API (auto-service tenant — JWT ou clé API) ─────────────────────

export const apiKeyApi = {
    /** GET /api/v1/admin/api-keys — Lister les clés API du tenant */
    list: () => get<ApiKeyResponse[]>("/api/v1/admin/api-keys"),

    /** POST /api/v1/admin/api-keys — Créer une clé API (rawKey affichée une seule fois) */
    create: (data: CreateApiKeyRequest) =>
        post<ApiKeyResponse>("/api/v1/admin/api-keys", data),

    /** DELETE /api/v1/admin/api-keys/{id} — Révoquer une clé API */
    revoke: (id: string) => del<void>(`/api/v1/admin/api-keys/${id}`),
};

// ─── API Invitation développeur (admin uniquement — via Kernel Core) ─────────

export interface InviteDeveloperRequest {
    firstName: string;
    lastName: string;
    email: string;
}

export const developerInviteApi = {
    /** POST /api/v1/admin/developers/invite — Crée le compte sur Kernel Core + email de définition de mot de passe */
    invite: (data: InviteDeveloperRequest) =>
        post<void>("/api/v1/admin/developers/invite", data),
};

// ─── API Applications d'intégration (auto-service tenant) ────────────────────

export const applicationApi = {
    /** GET /api/v1/admin/applications — Lister les applications du tenant */
    list: () => get<ApplicationResponse[]>("/api/v1/admin/applications"),

    /** POST /api/v1/admin/applications — Créer une application (secrets affichés une seule fois) */
    create: (data: CreateApplicationRequest) =>
        post<ApplicationResponse>("/api/v1/admin/applications", data),

    /** GET /api/v1/admin/applications/{id} — Détail d'une application */
    get: (id: string) => get<ApplicationResponse>(`/api/v1/admin/applications/${id}`),

    /** PATCH /api/v1/admin/applications/{id} — Mettre à jour une application */
    update: (id: string, data: UpdateApplicationRequest) =>
        patch<ApplicationResponse>(`/api/v1/admin/applications/${id}`, data),

    /** DELETE /api/v1/admin/applications/{id} — Supprimer (révoque la clé + supprime le webhook) */
    remove: (id: string) => del<void>(`/api/v1/admin/applications/${id}`),

    /** POST /api/v1/admin/applications/{id}/rotate-private-key — Nouvelle clé privée (une seule fois) */
    rotatePrivateKey: (id: string) =>
        post<ApplicationResponse>(`/api/v1/admin/applications/${id}/rotate-private-key`, {}),

    /** POST /api/v1/admin/applications/{id}/rotate-webhook-secret — Nouveau secret webhook (une seule fois) */
    rotateWebhookSecret: (id: string) =>
        post<ApplicationResponse>(`/api/v1/admin/applications/${id}/rotate-webhook-secret`, {}),
};

// ─── API Webhooks (auto-service tenant — JWT ou clé API) ─────────────────────

export const webhookApi = {
    /** GET /api/v1/admin/webhooks — Lister les webhooks du tenant */
    list: () => get<WebhookEndpointResponse[]>("/api/v1/admin/webhooks"),

    /** POST /api/v1/admin/webhooks — Créer un webhook (secret affiché une seule fois) */
    create: (data: CreateWebhookEndpointRequest) =>
        post<WebhookEndpointResponse>("/api/v1/admin/webhooks", data),

    /** PATCH /api/v1/admin/webhooks/{id} — Mettre à jour un webhook */
    update: (id: string, data: UpdateWebhookEndpointRequest) =>
        patch<WebhookEndpointResponse>(`/api/v1/admin/webhooks/${id}`, data),

    /** DELETE /api/v1/admin/webhooks/{id} — Supprimer un webhook */
    remove: (id: string) => del<void>(`/api/v1/admin/webhooks/${id}`),

    /** POST /api/v1/admin/webhooks/{id}/rotate-secret — Régénérer le secret */
    rotateSecret: (id: string) =>
        post<WebhookEndpointResponse>(`/api/v1/admin/webhooks/${id}/rotate-secret`, {}),

    /** POST /api/v1/admin/webhooks/{id}/test — Envoyer un ping de test */
    sendTestPing: (id: string) =>
        post<TestPingResponse>(`/api/v1/admin/webhooks/${id}/test`, {}),

    /** GET /api/v1/admin/webhooks/deliveries?page=&size= — Journal des livraisons */
    listDeliveries: (page = 0, size = 20) =>
        get<WebhookDeliveryResponse[]>(
            `/api/v1/admin/webhooks/deliveries?page=${page}&size=${size}`
        ),
};

// ─── Types Annuaire Membres / Politique de Tier / Journal (Admin) ────────────

export interface MemberSummaryResponse {
    memberId: string;
    balance: number;
    currencyCode: string;
    status: string;
    createdAt: string;
}

export interface TierThreshold {
    level: string;
    threshold: number;
    multiplier: number;
}

export interface TierPolicyResponse {
    tenantId: string;
    criterion: string;
    thresholds: TierThreshold[];
    maintainPeriod: string;
    maintainThresholdPoints: number;
    downgradeGraceDays: number;
}

export interface TierPolicyRequest {
    criterion: string;
    thresholds: TierThreshold[];
    maintainPeriod: string;
    maintainThresholdPoints: number;
    downgradeGraceDays: number;
}

export interface PointsTransactionLogResponse {
    id: string;
    pointsAccountId: string;
    memberId: string | null;
    type: string;
    amount: number;
    balanceAfter: number;
    source: string;
    ruleId: string | null;
    reason: string | null;
    createdAt: string;
}

// ─── API Annuaire Membres (Admin) ────────────────────────────────────────────

export const adminMembersApi = {
    /** GET /api/v1/admin/members?page=&size= — Lister les membres (portefeuilles) du tenant */
    list: (page = 0, size = 20) =>
        get<MemberSummaryResponse[]>(`/api/v1/admin/members?page=${page}&size=${size}`),
};

// ─── API Politique de Tier (Admin — Establishment) ───────────────────────────

export const tierPolicyApi = {
    /** GET /api/v1/admin/tier-policies — Politique de tier du tenant */
    get: () => get<TierPolicyResponse>("/api/v1/admin/tier-policies"),

    /** PUT /api/v1/admin/tier-policies — Créer/mettre à jour la politique de tier */
    upsert: (data: TierPolicyRequest) =>
        put<TierPolicyResponse>("/api/v1/admin/tier-policies", data),
};

// ─── API Journal des transactions de points (Admin — Logs) ──────────────────

export interface ApiKeyPointsFlowResponse {
    apiKeyId: string;
    credited: number;
    debited: number;
}

export const adminLogsApi = {
    /** GET /api/v1/admin/points-transactions?page=&size= — Journal tenant-wide */
    listPointsTransactions: (page = 0, size = 20) =>
        get<PointsTransactionLogResponse[]>(
            `/api/v1/admin/points-transactions?page=${page}&size=${size}`
        ),

    /** GET /api/v1/admin/points-transactions/flow-by-api-key — Flux de points agrégé par clé API */
    flowByApiKey: () =>
        get<ApiKeyPointsFlowResponse[]>(
            "/api/v1/admin/points-transactions/flow-by-api-key"
        ),
};
