package com.yowyob.loyalty.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class OpenApiConfig {

    /**
     * Aucun `servers` n'est declare volontairement : springdoc derive alors l'URL
     * depuis la requete entrante. Combine au `forward-headers-strategy: framework`
     * (application.yml), le "Try it out" vise https://loyalty.yowyob.com/loyalty-api
     * en prod et http://localhost:8082 en dev, sans configuration par environnement.
     * Une URL codee en dur ici renverrait les appels du Swagger en ligne vers la
     * machine de l'utilisateur.
     */
    @Bean
    public OpenAPI loyaltyOpenAPI(Environment environment) {
        final String securitySchemeName = "bearerAuth";
        final String apiKeySchemeName = "apiKeyAuth";

        OpenAPI openAPI = new OpenAPI()
                .info(new Info()
                        .title("Loyalty Program API")
                        .description("API du programme de fidélité Yowyob — wallet, règles, points et événements.")
                        .version("v1")
                        .contact(new Contact().name("Yowyob").email("dev@yowyob.com")))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT Keycloak/YowAuth0. Claim tenant : organization_id"))
                        .addSecuritySchemes(apiKeySchemeName,
                                new SecurityScheme()
                                        .name("X-Api-Key")
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .description("Clé API tenant pour l'intégration machine-à-machine (portail développeur)")));

        if (!environment.matchesProfiles("dev")) {
            openAPI.addSecurityItem(new SecurityRequirement().addList(securitySchemeName));
            openAPI.addSecurityItem(new SecurityRequirement().addList(apiKeySchemeName));
        }
        return openAPI;
    }
}
