package com.yowyob.loyalty.infrastructure.security.crypto;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le mot de passe Bonification du tenant était écrit en clair dans le JSONB tenants.config.
 * Ces tests figent le chiffrement qui le remplace, et surtout ses deux propriétés de survie :
 * relire les valeurs historiques en clair, et échouer bruyamment si la clé change.
 */
public class SecretCipherTest {

    private static String key() {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        return Base64.getEncoder().encodeToString(raw);
    }

    @Test
    void roundTripsASecret() {
        SecretCipher cipher = new SecretCipher(key());
        String encrypted = cipher.encrypt("s3cr3t-bonification");
        assertTrue(SecretCipher.isEncrypted(encrypted));
        assertFalse(encrypted.contains("s3cr3t-bonification"));
        assertEquals("s3cr3t-bonification", cipher.decrypt(encrypted));
    }

    /** IV aléatoire : deux chiffrements du même secret ne doivent pas se ressembler. */
    @Test
    void producesADifferentCiphertextEachTime() {
        SecretCipher cipher = new SecretCipher(key());
        assertNotEquals(cipher.encrypt("meme-secret"), cipher.encrypt("meme-secret"));
    }

    /** Les valeurs écrites en clair avant le chiffrement doivent rester lisibles sans migration. */
    @Test
    void readsLegacyPlaintextUnchanged() {
        SecretCipher cipher = new SecretCipher(key());
        assertEquals("ancien-mot-de-passe", cipher.decrypt("ancien-mot-de-passe"));
    }

    @Test
    void doesNotDoubleEncrypt() {
        SecretCipher cipher = new SecretCipher(key());
        String once = cipher.encrypt("secret");
        assertEquals(once, cipher.encrypt(once));
    }

    /** Sans clé, la lecture continue mais l'écriture est refusée plutôt que faite en clair. */
    @Test
    void refusesToEncryptWithoutAKey() {
        SecretCipher cipher = new SecretCipher("");
        assertFalse(cipher.isEnabled());
        assertEquals("en-clair", cipher.decrypt("en-clair"));
        assertThrows(IllegalStateException.class, () -> cipher.encrypt("secret"));
    }

    @Test
    void rejectsAKeyOfTheWrongLength() {
        String tooShort = Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(IllegalStateException.class, () -> new SecretCipher(tooShort));
    }

    /** Une clé changée doit échouer bruyamment, pas rendre un secret silencieusement faux. */
    @Test
    void failsLoudlyWhenTheKeyChanged() {
        String encrypted = new SecretCipher(key()).encrypt("secret");
        SecretCipher other = new SecretCipher(key());
        assertThrows(IllegalStateException.class, () -> other.decrypt(encrypted));
    }
}
