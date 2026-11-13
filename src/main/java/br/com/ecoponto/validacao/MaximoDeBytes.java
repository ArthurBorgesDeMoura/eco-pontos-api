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
import java.nio.charset.StandardCharsets;

@Documented
@Constraint(validatedBy = MaximoDeBytes.Validador.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaximoDeBytes {

    int value();

    String message();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<MaximoDeBytes, String> {

        private int maximo;

        @Override
        public void initialize(MaximoDeBytes anotacao) {
            maximo = anotacao.value();
        }

        @Override
        public boolean isValid(String texto, ConstraintValidatorContext contexto) {
            return texto == null || texto.getBytes(StandardCharsets.UTF_8).length <= maximo;
        }
    }
}
