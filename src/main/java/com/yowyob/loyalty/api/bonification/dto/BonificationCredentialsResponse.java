package com.yowyob.loyalty.api.bonification.dto;

/**
 * État des identifiants du tenant, en écriture seule : {@code username} est rendu pour que
 * l'écran affiche quel compte est configuré, jamais le mot de passe. Un secret qu'une API
 * peut relire finit dans un cache, un log ou un onglet ouvert.
 *
 * @param configured false = le tenant utilise les identifiants globaux du déploiement.
 */
public record BonificationCredentialsResponse(boolean configured, String username) {}
