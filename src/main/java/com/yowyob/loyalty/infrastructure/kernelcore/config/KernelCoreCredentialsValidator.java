package com.yowyob.loyalty.infrastructure.kernelcore.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Refuse de démarrer quand les credentials d'intégration Kernel Core manquent ou sont restés
 * sur leur valeur d'exemple.
 *
 * <p>Sans ce garde-fou, un déploiement sans {@code KERNEL_SERVICE_CLIENT_SECRET} démarre
 * normalement avec le défaut {@code changeme} d'application.yml : l'application semble en
 * bonne santé (actuator vert), mais chaque appel Kernel Core est refusé et le symptôme
 * n'apparaît qu'à la première connexion d'un utilisateur, sous la forme d'un « KernelCore
 * indisponible » qui ne désigne pas la cause. Mieux vaut un échec de démarrage explicite.
 *
 * <p>Le blocage ne s'applique qu'hors profils de développement et de test : ces profils
 * tournent sans Kernel Core (stubs, tenant injecté), leur imposer de vrais secrets rendrait
 * le projet inutilisable en local. Ils reçoivent un avertissement à la place.
 */
@Component
public class KernelCoreCredentialsValidator implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(KernelCoreCredentialsValidator.class);

    /**
     * Valeurs d'exemple de la clé. Volontairement limitée au secret : le client id par défaut
     * d'application.yml, « loyalty-service », est aussi l'identifiant réellement utilisé par le
     * déploiement de production (docker-compose.prod.yml). Le traiter comme une valeur d'exemple
     * y provoquerait un refus de démarrage sur une configuration parfaitement valide.
     */
    private static final List<String> SECRET_PLACEHOLDERS = List.of("changeme", "à-définir", "todo", "secret");

    /**
     * « stub » n'en fait volontairement pas partie : le déploiement Render l'active en
     * production (render.yaml) faute d'adaptateurs R2DBC pour deux dépôts, et l'y tolérer
     * rendrait ce garde-fou inopérant là où il sert précisément.
     */
    private static final List<String> LOCAL_PROFILES = List.of("dev", "test");

    private final KernelCoreProperties properties;
    private final Environment environment;

    public KernelCoreCredentialsValidator(KernelCoreProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        boolean local = Arrays.stream(environment.getActiveProfiles()).anyMatch(LOCAL_PROFILES::contains);

        String clientId = properties.getServiceClientId();
        String clientSecret = properties.getServiceClientSecret();

        // Seul le secret est confronté aux valeurs d'exemple : c'est lui qui atteste d'une
        // configuration réelle. Le client id n'est vérifié que sur sa présence.
        if (isBlank(clientId) || isUnset(clientSecret)) {
            String message = "Credentials Kernel Core non configurés : renseignez KERNEL_SERVICE_CLIENT_ID et "
                    + "KERNEL_SERVICE_CLIENT_SECRET (ClientApplication dédiée à ce déploiement). "
                    + "Sans eux, tout appel Kernel Core — connexion comprise — est refusé.";
            if (!local) {
                throw new IllegalStateException(message);
            }
            log.warn("{} (toléré sur le profil local actif)", message);
        }

        // Ces deux-là dégradent une fonctionnalité sans empêcher l'application de servir :
        // les signaler suffit, les transformer en échec de démarrage bloquerait un
        // déploiement par ailleurs sain.
        if (isBlank(properties.getTenantId())) {
            log.warn("KERNEL_TENANT_ID non configuré : chaque connexion passera par "
                    + "/api/auth/discover-contexts pour retrouver le tenant plateforme (un aller-retour "
                    + "supplémentaire, et un échec si le compte a accès à plusieurs contextes).");
        }
        if (isBlank(properties.getOrganizationCode())) {
            log.warn("KERNEL_ORGANIZATION_CODE non configuré : l'inscription publique est refusée "
                    + "(« organisation cible non configurée »). Le reste du portail fonctionne.");
        }

        if (properties.getPayments().isEnabled() && isBlank(properties.getPayments().getCallbackSecret())) {
            log.warn("Passerelle de paiement active sans KERNEL_PAYMENTS_CALLBACK_SECRET : la route de "
                    + "callback reste fermée (401), donc aucun paiement ne sera jamais confirmé par "
                    + "Kernel Core.");
        }

        log.info("Kernel Core : {} avec le client « {} » (clé {}), tenant plateforme {}",
                properties.getBaseUrl(), clientId, mask(clientSecret),
                isBlank(properties.getTenantId()) ? "découvert à la connexion" : properties.getTenantId());
    }

    /** Empreinte suffisante pour vérifier *quelle* clé est chargée, sans la divulguer dans les logs. */
    private static String mask(String secret) {
        if (isBlank(secret)) return "absente";
        return secret.length() <= 4 ? "****" : secret.substring(0, 4) + "…(" + secret.length() + " car.)";
    }

    private static boolean isUnset(String secret) {
        return isBlank(secret) || SECRET_PLACEHOLDERS.contains(secret.trim().toLowerCase());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
