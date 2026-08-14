/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.extras.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.NoProviderFoundException;
import jakarta.validation.UnexpectedTypeException;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.executable.ExecutableValidator;
import org.aeonbits.owner.validation.ConstrainedProperty;
import org.aeonbits.owner.validation.Violation;

import java.util.ArrayList;
import java.util.List;

import static org.aeonbits.owner.util.Util.unsupported;

/**
 * Everything in {@link BeanValidator} that touches <code>jakarta.validation</code>, and the only class here
 * that names it. {@link JavaxBeanValidation} is the same class for the other spelling of the same
 * specification.
 *
 * <p>
 * <b>The separation is load-bearing and it is not a matter of taste.</b> {@link BeanValidator} is found by
 * {@link java.util.ServiceLoader}, which instantiates every service it discovers before anybody asks for
 * one, and both validation APIs are optional dependencies that most users of <code>owner-extras</code> will
 * not have. A class is loaded without resolving the classes its method bodies mention, so
 * <code>BeanValidator</code> loads and instantiates with them absent - and this class, which cannot, is not
 * touched until there is a constrained property to check. The same arrangement, for the same reason, as
 * <code>HoconLoader</code> and <code>HoconReader</code> in this artifact.
 * </p>
 *
 * @author Matteo Baccan
 * @since 2.0.0
 */
final class JakartaBeanValidation {

    /**
     * Built once and kept: a {@link ValidatorFactory} costs a good fraction of a second to build - it reads
     * the class path, the XML configuration, the message bundles - while being thread-safe and stateless
     * afterwards, which is why the specification's own advice is to have one per application. A
     * configuration object, on the other hand, is created wherever an application feels like it.
     */
    private static volatile ValidatorFactory factory;

    /** Don't let anyone instantiate this class */
    private JakartaBeanValidation() {
    }

    /**
     * Whether a provider for this namespace is on the class path, which is a question that can only be
     * answered by trying: the API is a set of interfaces and says nothing about who implements them.
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
        synchronized (JakartaBeanValidation.class) {
            if (factory == null)
                factory = build();
            return factory;
        }
    }

    /**
     * Builds the factory, or answers <code>null</code> when this namespace has an API and no provider.
     * <p>
     * Only {@link NoProviderFoundException} is treated as "not here": every other
     * {@link ValidationException} is a provider that is present and unhappy - a broken
     * <code>validation.xml</code>, a message interpolator that needs an expression language implementation
     * it cannot find - and passing over that as though nothing were installed would put the configuration
     * back in exactly the silence this feature exists to end.
     * </p>
     */
    private static ValidatorFactory build() {
        try {
            return Validation.buildDefaultValidatorFactory();
        } catch (NoProviderFoundException nobodyImplementsIt) {
            return null;
        } catch (ValidationException providerIsUnhappy) {
            throw unsupported(providerIsUnhappy,
                    "A jakarta.validation provider is on the class path and could not be started: %s",
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

    /**
     * The one call this whole class exists to make.
     * <p>
     * A constraint the provider has no validator for - <code>&#64;Min</code> on a <code>String</code>, or on
     * an <code>Optional</code> that should have carried it inside the angle brackets - is a mistake in the
     * configuration interface, not a failure of the value, and it is said as one: the message a provider
     * gives names its own path and not the method the reader has to go and fix.
     * </p>
     */
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
