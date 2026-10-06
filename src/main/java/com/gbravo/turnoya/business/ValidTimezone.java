package com.gbravo.turnoya.business;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.ZoneId;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/** Valida que el string sea una zona horaria IANA (ej: America/Argentina/Cordoba). */
@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidTimezone.Validator.class)
public @interface ValidTimezone {

    String message() default "zona horaria inválida";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidTimezone, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || ZoneId.getAvailableZoneIds().contains(value);
        }
    }
}
