package com.yowyob.loyalty.infrastructure.kernelcore.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le défaut « changeme » d'application.yml laisse démarrer un déploiement sans credentials :
 * l'application paraît saine et ne révèle le problème qu'à la première connexion d'un
 * utilisateur. Ces tests figent le refus de démarrer, et sa tolérance en dev/test.
 */
public class KernelCoreCredentialsValidatorTest {

    private static KernelCoreProperties properties(String clientId, String clientSecret) {
        KernelCoreProperties properties = new KernelCoreProperties();
        properties.setServiceClientId(clientId);
        properties.setServiceClientSecret(clientSecret);
        properties.setTenantId("11111111-1111-1111-1111-111111111111");
        properties.setOrganizationCode("ORG-TEST-1");
        return properties;
    }

    private static StandardEnvironment environment(String... profiles) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }

    private static void validate(KernelCoreProperties properties, StandardEnvironment environment) {
        new KernelCoreCredentialsValidator(properties, environment).afterPropertiesSet();
    }

    @Test
    void acceptsRealCredentials() {
        assertDoesNotThrow(() -> validate(properties("loyality-program", "a-real-api-key"), environment("prod")));
    }

    @Test
    void refusesToStartOnTheExampleSecret() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> validate(properties("loyality-program", "changeme"), environment("prod")));
        assertTrue(error.getMessage().contains("KERNEL_SERVICE_CLIENT_SECRET"));
    }

    @Test
    void refusesToStartWithoutClientId() {
        assertThrows(IllegalStateException.class,
                () -> validate(properties("  ", "a-real-api-key"), environment("prod")));
    }

    /** Le profil « stub » est actif en production sur Render : il ne doit pas désarmer le garde-fou. */
    @Test
    void stillRefusesUnderTheStubProfile() {
        assertThrows(IllegalStateException.class,
                () -> validate(properties("loyality-program", "changeme"), environment("no-kafka", "stub")));
    }

    /** Dev et test tournent sans Kernel Core : leur imposer de vrais secrets bloquerait le local. */
    @Test
    void toleratesMissingCredentialsInDevelopment() {
        assertDoesNotThrow(() -> validate(properties(null, null), environment("dev")));
        assertDoesNotThrow(() -> validate(properties("loyalty-service", "changeme"), environment("test")));
    }

    /** Tenant et organisation manquants dégradent une fonctionnalité, ils ne bloquent pas le service. */
    @Test
    void doesNotBlockStartupOnMissingTenantOrOrganization() {
        KernelCoreProperties properties = properties("loyality-program", "a-real-api-key");
        properties.setTenantId(null);
        properties.setOrganizationCode(null);
        assertDoesNotThrow(() -> validate(properties, environment("prod")));
    }
}
