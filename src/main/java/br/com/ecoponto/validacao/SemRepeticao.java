package br.com.ecoponto.validacao;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

@Documented
@Constraint(validatedBy = SemRepeticao.Validador.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SemRepeticao {

    String message();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<SemRepeticao, List<?>> {

        @Override
        public boolean isValid(List<?> lista, ConstraintValidatorContext contexto) {
            return lista == null
                    || lista.stream().allMatch(Objects::nonNull) && new HashSet<>(lista).size() == lista.size();
        }
    }
}
