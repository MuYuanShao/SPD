package com.hospital.spd.common;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.*;
import java.math.BigDecimal;

/** Validates whole-valued quantity fields while leaving monetary precision unrestricted. */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = WholeQuantity.Validator.class)
public @interface WholeQuantity {
    String message() default "数量必须为整数，不支持小数";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    final class Validator implements ConstraintValidator<WholeQuantity, BigDecimal> {
        @Override public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
            if (value == null) return true;
            try { QuantityRules.whole(value, "数量"); return true; }
            catch (IllegalArgumentException ex) { return false; }
        }
    }
}
