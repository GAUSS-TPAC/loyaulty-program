package com.yowyob.loyalty.infrastructure.kernelcore.adapter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Les erreurs Kernel Core arrivent en enveloppe JSON. Concaténer le corps brut affichait à
 * l'utilisateur « Inscription refusée: {"success":false,"data":null,"message":"A user already
 * exists…"} » : la seule information exploitable y était noyée.
 */
public class KernelCoreErrorMessageTest {

    @Test
    void keepsOnlyTheMessageOfAKernelCoreErrorBody() {
        String body = "{\"success\":false,\"data\":null,"
                + "\"message\":\"A user already exists with email: a@b.com\","
                + "\"errorCode\":\"EMAIL_DUPLICATE\",\"timestamp\":\"2026-09-02T14:32:23Z\"}";
        assertEquals("A user already exists with email: a@b.com", KernelCoreAuthAdapter.explain(body));
    }

    /** Page d'erreur d'un proxy, corps HTML : on rend le texte tel quel plutôt que rien. */
    @Test
    void fallsBackToTheRawBodyWhenItIsNotJson() {
        assertEquals("<html>502 Bad Gateway</html>",
                KernelCoreAuthAdapter.explain("<html>502 Bad Gateway</html>"));
    }

    @Test
    void reportsAnEmptyBodyExplicitly() {
        assertEquals("réponse vide", KernelCoreAuthAdapter.explain(""));
        assertEquals("réponse vide", KernelCoreAuthAdapter.explain(null));
    }

    /** Un corps JSON sans champ message reste rendu, tronqué : mieux que rien du tout. */
    @Test
    void keepsTheBodyWhenThereIsNoMessageField() {
        assertTrue(KernelCoreAuthAdapter.explain("{\"errorCode\":\"X\"}").contains("errorCode"));
    }

    @Test
    void truncatesAnOversizedBody() {
        assertTrue(KernelCoreAuthAdapter.explain("x".repeat(500)).endsWith("…"));
    }
}
