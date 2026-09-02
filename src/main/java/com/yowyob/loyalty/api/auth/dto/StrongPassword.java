package com.yowyob.loyalty.api.auth.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;

/**
 * Règle de mot de passe d'auth-core ("at least 10 characters, upper and lower case letters,
 * a digit and a symbol"), factorisée : inscription, réinitialisation et changement doivent
 * appliquer exactement la même, sinon l'utilisateur découvre un refus en anglais après
 * l'aller-retour KernelCore — ou pire, seulement sur un des trois parcours.
 */
@Documented
@NotBlank
@Size(min = 10, message = "Le mot de passe doit contenir au moins 10 caractères")
@Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
        message = "Le mot de passe doit contenir une minuscule, une majuscule, un chiffre et un symbole")
@Constraint(validatedBy = {})
@Target({FIELD, PARAMETER, ANNOTATION_TYPE, RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {
    String message() default "Mot de passe trop faible";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
