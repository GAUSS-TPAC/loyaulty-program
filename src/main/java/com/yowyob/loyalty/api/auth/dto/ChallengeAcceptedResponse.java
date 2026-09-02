package com.yowyob.loyalty.api.auth.dto;

/**
 * Réponse volontairement constante des parcours « mot de passe oublié » et « renvoyer la
 * vérification ». Elle ne dit pas si un compte correspond, ni si l'email est réellement
 * parti : sans quoi le formulaire deviendrait un oracle d'énumération d'adresses. Le canal
 * réel (SMTP / PREVIEW_ONLY) reste visible dans les logs du backend.
 */
public record ChallengeAcceptedResponse(String status) {

    public static final ChallengeAcceptedResponse SENT_IF_ACCOUNT_EXISTS =
            new ChallengeAcceptedResponse("SENT_IF_ACCOUNT_EXISTS");
}
