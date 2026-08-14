/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.extras.validation;

import org.aeonbits.owner.validation.ConstrainedProperty;
import org.aeonbits.owner.validation.Violation;

import javax.validation.ConstraintViolation;
import javax.validation.NoProviderFoundException;
import javax.validation.UnexpectedTypeException;
import javax.validation.Validation;
import javax.validation.ValidationException;
import javax.validation.ValidatorFactory;
import javax.validation.executable.ExecutableValidator;
import java.util.ArrayList;
import java.util.List;

import static org.aeonbits.owner.util.Util.unsupported;

/**
 * Everything in {@link BeanValidator} that touches <code>javax.validation</code>, and the only class here
 * that names it. {@link JakartaBeanValidation} is the same class for the other spelling of the same
 * specification, and the two are kept apart rather than merged because merging them would need one of the
 * two APIs to be present for the other one to work.
 *
 * <p>
 * <b>The separation from {@link BeanValidator} is load-bearing</b>, for the reason set out in
 * {@link JakartaBeanValidation}: the validator is instantiated by {@link java.util.ServiceLoader} on every
 * class path that has this artifact, and this class cannot be loaded without an API that most of those class
 * paths will not have.
 * </p>
 *
 * @author Matteo Baccan
 * @since 2.0.0
 */
final class JavaxBeanValidation {

    /** Built once and kept, for the reasons given in {@link JakartaBeanValidation}. */
    private static volatile ValidatorFactory factory;

    /** Don't let anyone instantiate this class */
    private JavaxBeanValidation() {
    }

    /**
     * Whether a provider for this namespace is on the class path.
     *
     * @return <code>true</code> if a validator can be built.
     */
    static boolean hasProvider() {
        return factory() != null;
    }

    private static ValidatorFactory factory() {
        ValidatorFactory built = factory;
        if (built != null)
            return built;
        synchronized (JavaxBeanValidation.class) {
            if (factory == null)
                factory = build();
            return factory;
        }
    }

    /** As {@link JakartaBeanValidation}: absent is not the same as present and unhappy. */
    private static ValidatorFactory build() {
        try {
            return Validation.buildDefaultValidatorFactory();
        } catch (NoProviderFoundException nobodyImplementsIt) {
            return null;
        } catch (ValidationException providerIsUnhappy) {
            throw unsupported(providerIsUnhappy,
                    "A javax.validation provider is on the class path and could not be started: %s",
                    providerIsUnhappy.getMessage());
        }
    }

    /**
     * Checks the given properties, each against the constraints of the method that answered it.
     *
     * @param properties the properties to check.
     * @return the violations found.
     */
    static List<Violation> check(List<ConstrainedProperty> properties) {
        ExecutableValidator validator = factory().getValidator().forExecutables();
        List<Violation> violations = new ArrayList<>();
        for (ConstrainedProperty property : properties)
            collect(validator, property, violations);
        return violations;
    }

    private static void collect(ExecutableValidator validator, ConstrainedProperty property,
                                List<Violation> violations) {
        for (ConstraintViolation<Object> violation : validate(validator, property))
            violations.add(new Violation(property.key(), property.method().getName(),
                    ViolationPath.describe(String.valueOf(violation.getPropertyPath()),
                            property.method().getName(), violation.getMessage())));
    }

    /** As {@link JakartaBeanValidation}: a constraint the provider cannot apply names the method. */
    private static Iterable<ConstraintViolation<Object>> validate(ExecutableValidator validator,
                                                                  ConstrainedProperty property) {
        try {
            return validator.validateReturnValue(property.config(), property.method(), property.value());
        } catch (UnexpectedTypeException noValidatorForThatType) {
            throw unsupported(noValidatorForThatType,
                    "'%s()' carries a constraint that cannot be applied to what it returns (%s). A "
                            + "constraint on a method returning Optional<T> or a collection applies to the "
                            + "container: write it on the value, as Optional<@Min(12) Integer>.",
                    property.method().getName(), noValidatorForThatType.getMessage());
        }
    }
}
