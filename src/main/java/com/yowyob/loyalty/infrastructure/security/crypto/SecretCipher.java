package com.yowyob.loyalty.infrastructure.security.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Chiffrement des secrets par tenant stockés en base (aujourd'hui les identifiants
 * Bonification dans {@code tenants.config}, jusqu'ici écrits en clair dans le JSONB).
 *
 * <p>AES-256-GCM, IV aléatoire par chiffrement, sortie préfixée {@code enc:v1:}. Le préfixe
 * n'est pas décoratif : il permet de relire les valeurs historiques restées en clair sans
 * migration de données, chacune étant chiffrée à sa prochaine écriture.
 *
 * <p>Sans clé configurée, la lecture continue de fonctionner mais l'écriture d'un nouveau
 * secret est refusée. C'est délibéré : accepter d'écrire en clair reconduirait exactement
 * le problème que cette classe corrige, alors qu'un démarrage bloqué immobiliserait un
 * déploiement existant pour une fonctionnalité qu'il n'utilise peut-être pas.
 */
@Component
public class SecretCipher {

    private static final Logger log = LoggerFactory.getLogger(SecretCipher.class);

    private static final String PREFIX = "enc:v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(@Value("${app.security.credential-encryption-key:}") String base64Key) {
        this.key = parseKey(base64Key);
        if (this.key == null) {
            log.warn("CREDENTIAL_ENCRYPTION_KEY absente : les secrets par tenant déjà stockés restent "
                    + "lisibles, mais l'enregistrement de nouveaux identifiants sera refusé. "
                    + "Générer une clé avec : openssl rand -base64 32");
        }
    }

    private static SecretKeySpec parseKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            return null;
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "app.security.credential-encryption-key n'est pas du base64 valide", e);
        }
        if (raw.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException("app.security.credential-encryption-key doit faire "
                    + KEY_LENGTH_BYTES + " octets une fois décodée (AES-256), reçu " + raw.length);
        }
        return new SecretKeySpec(raw, "AES");
    }

    public boolean isEnabled() {
        return key != null;
    }

    /**
     * @throws IllegalStateException si aucune clé n'est configurée : plutôt que de stocker
     *                               le secret en clair, l'appelant doit remonter l'erreur.
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return plaintext;
        }
        if (isEncrypted(plaintext)) {
            return plaintext;
        }
        if (key == null) {
            throw new IllegalStateException("Impossible de chiffrer : app.security.credential-encryption-key "
                    + "n'est pas configurée (générer avec : openssl rand -base64 32)");
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Échec du chiffrement d'un secret de tenant", e);
        }
    }

    /**
     * Valeur non préfixée = secret historique écrit en clair avant l'introduction du
     * chiffrement : rendu tel quel, pour ne pas casser les tenants déjà configurés.
     */
    public String decrypt(String stored) {
        if (stored == null || stored.isBlank() || !isEncrypted(stored)) {
            return stored;
        }
        if (key == null) {
            throw new IllegalStateException("Secret chiffré en base mais aucune clé configurée : "
                    + "restaurer app.security.credential-encryption-key (la perdre rend ces secrets illisibles)");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(payload, IV_LENGTH, payload.length - IV_LENGTH);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Échec du déchiffrement d'un secret de tenant "
                    + "(clé changée ou donnée corrompue)", e);
        }
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }
}
