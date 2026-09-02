/**
 * session.ts — source unique de vérité de la session du portail.
 *
 * Avant ce module, le JWT était lu et écrit directement en sessionStorage depuis quatre
 * endroits, sans date d'expiration : la session ne mourait donc jamais côté client, elle
 * se manifestait par un 401 au milieu d'un écran déjà affiché. KernelCore renvoie au login
 * un refreshToken et la durée de vie de l'access token : on les stocke, on rafraîchit avant
 * expiration, et on ne purge qu'une fois le refresh réellement refusé.
 *
 * Deux credentials coexistent et restent distincts :
 * - la session admin (JWT + refresh token), gérée ici ;
 * - la clé API développeur, collée à la main, sans expiration ni rafraîchissement.
 */

const TOKEN_KEY = "loyalty_jwt_token";
const REFRESH_KEY = "loyalty_refresh_token";
const EXPIRES_AT_KEY = "loyalty_token_expires_at";
const ORG_KEY = "loyalty_organization_id";
const API_KEY = "loyalty_dev_api_key";
/** Marqueur "se souvenir de moi" : décide de localStorage vs sessionStorage. */
const PERSIST_KEY = "loyalty_session_persistent";

/**
 * Marge avant expiration : on rafraîchit un peu à l'avance pour qu'une requête partie
 * juste avant l'échéance n'arrive pas avec un jeton périmé.
 */
const REFRESH_MARGIN_MS = 60_000;

export interface SessionTokens {
    token: string;
    refreshToken?: string;
    /** Secondes de validité de l'access token, telles que renvoyées par KernelCore. */
    expiresInSeconds?: number;
    organizationId?: string;
}

function hasWindow(): boolean {
    return typeof window !== "undefined";
}

/**
 * « Se souvenir de moi » persiste la session au-delà de l'onglet ; sans cette case, elle
 * meurt avec lui. Le choix est lui-même dans localStorage, sinon on ne saurait plus, au
 * rechargement, où la session a été rangée.
 */
function isPersistent(): boolean {
    if (!hasWindow()) return false;
    try {
        return localStorage.getItem(PERSIST_KEY) === "1";
    } catch {
        return false;
    }
}

function store(): Storage | null {
    if (!hasWindow()) return null;
    try {
        return isPersistent() ? localStorage : sessionStorage;
    } catch {
        return null;
    }
}

/** Lecture tolérante : après un changement de mode, la valeur peut être dans l'autre stockage. */
function read(key: string): string | null {
    if (!hasWindow()) return null;
    try {
        return sessionStorage.getItem(key) ?? localStorage.getItem(key);
    } catch {
        return null;
    }
}

function write(key: string, value: string | null) {
    const target = store();
    if (!target) return;
    try {
        // Toujours purger l'autre stockage : sans ça, une session "oubliée" survit dans
        // localStorage et ressuscite au rechargement suivant.
        sessionStorage.removeItem(key);
        localStorage.removeItem(key);
        if (value !== null) target.setItem(key, value);
    } catch {
        // Navigation privée ou stockage bloqué : la session reste en mémoire du temps de l'onglet.
    }
}

export function getAccessToken(): string | null {
    return read(TOKEN_KEY);
}

export function getRefreshToken(): string | null {
    return read(REFRESH_KEY);
}

export function getOrganizationId(): string | null {
    return read(ORG_KEY);
}

export function getApiKey(): string | null {
    return read(API_KEY);
}

/** Une session existe dès qu'un credential est présent — JWT admin ou clé API développeur. */
export function hasSession(): boolean {
    return Boolean(getAccessToken() || getApiKey());
}

export function saveApiKey(apiKey: string) {
    write(API_KEY, apiKey);
}

export function setRemember(remember: boolean) {
    if (!hasWindow()) return;
    try {
        if (remember) localStorage.setItem(PERSIST_KEY, "1");
        else localStorage.removeItem(PERSIST_KEY);
    } catch {
        // Stockage indisponible : on retombe sur une session d'onglet, comportement par défaut.
    }
}

export function saveSession(tokens: SessionTokens) {
    write(TOKEN_KEY, tokens.token);
    write(REFRESH_KEY, tokens.refreshToken ?? null);
    write(ORG_KEY, tokens.organizationId ?? null);
    write(
        EXPIRES_AT_KEY,
        tokens.expiresInSeconds ? String(Date.now() + tokens.expiresInSeconds * 1000) : null
    );
}

export function clearSession() {
    [TOKEN_KEY, REFRESH_KEY, EXPIRES_AT_KEY, ORG_KEY, API_KEY].forEach((key) => {
        try {
            sessionStorage.removeItem(key);
            localStorage.removeItem(key);
        } catch {
            // rien à purger
        }
    });
}

/**
 * KernelCore ne renvoie pas toujours de durée de vie (confirmation MFA, anciens déploiements).
 * Sans échéance connue, on ne rafraîchit pas préventivement : le 401 reste le filet de sécurité.
 */
function expiresSoon(): boolean {
    const raw = read(EXPIRES_AT_KEY);
    if (!raw) return false;
    const expiresAt = Number(raw);
    return Number.isFinite(expiresAt) && Date.now() > expiresAt - REFRESH_MARGIN_MS;
}

/**
 * Un seul rafraîchissement à la fois : KernelCore fait tourner le refresh token à chaque
 * échange, donc deux appels concurrents en invalideraient un et déconnecteraient l'utilisateur
 * au milieu d'un écran qui charge plusieurs ressources en parallèle.
 */
let inFlightRefresh: Promise<string | null> | null = null;

export function refreshSession(): Promise<string | null> {
    if (inFlightRefresh) return inFlightRefresh;

    const refreshToken = getRefreshToken();
    if (!refreshToken) return Promise.resolve(null);

    inFlightRefresh = (async () => {
        try {
            const res = await fetch("/backend/api/v1/auth/refresh", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ refreshToken }),
            });
            if (!res.ok) return null;
            const data = (await res.json()) as {
                token: string;
                refreshToken?: string;
                expiresInSeconds?: number;
            };
            if (!data?.token) return null;
            saveSession({
                token: data.token,
                refreshToken: data.refreshToken ?? refreshToken,
                expiresInSeconds: data.expiresInSeconds,
                organizationId: getOrganizationId() ?? undefined,
            });
            return data.token;
        } catch {
            // Hors ligne : ce n'est pas une session invalide, on garde les jetons en place.
            return null;
        } finally {
            inFlightRefresh = null;
        }
    })();

    return inFlightRefresh;
}

/** Rafraîchit si l'échéance approche. Retourne le jeton à utiliser pour la requête. */
export async function ensureFreshToken(): Promise<string | null> {
    if (!getAccessToken()) return null;
    if (!expiresSoon()) return getAccessToken();
    return (await refreshSession()) ?? getAccessToken();
}

/**
 * Fin de session : purge et retour à la page de connexion, avec la raison en paramètre pour
 * que l'écran explique la déconnexion au lieu de la subir sans message.
 */
export function endSession(reason: "expired" | "manual" = "manual") {
    clearSession();
    if (!hasWindow()) return;
    const locale = window.location.pathname.split("/")[1] || "fr";
    const query = reason === "expired" ? "?expired=1" : "";
    window.location.href = `/${locale}/login${query}`;
}
