/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.validation;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.unmodifiableList;

/**
 * Thrown when a configuration is created and its values do not satisfy the constraints its methods declare.
 *
 * <p>
 * It names <b>every</b> violation, for the same reason
 * {@link org.aeonbits.owner.MissingMandatoryPropertyException} names every missing key: a configuration is
 * read once, at startup, and a reader who is told one thing wrong at a time restarts once per mistake.
 * </p>
 *
 * @author Matteo Baccan
 * @since 2.0.0
 */
public class ConfigValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** The violations, in the order they were reported. See {@link #getViolations()}. */
    private final List<Violation> violations;

    /**
     * Builds an exception over the violations found in a configuration.
     *
     * @param configClass the interface the configuration was created from, named in the message.
     * @param violations  the violations found, never empty.
     */
    public ConfigValidationException(Class<?> configClass, List<Violation> violations) {
        super(buildMessage(configClass, violations));
        // copied rather than wrapped: the caller keeps a reference to the list it handed over, and what an
        // exception reports has to stay what it was thrown for
        this.violations = unmodifiableList(new ArrayList<Violation>(violations));
    }

    /**
     * Lists the violations found.
     *
     * @return an unmodifiable list of the violations, never empty.
     */
    public List<Violation> getViolations() {
        return violations;
    }

    /**
     * Lists the keys of the properties that failed a constraint, which is what a caller most often wants:
     * the same key it would have read the value with. A key appears once per violation, so a property
     * failing two constraints appears twice.
     *
     * @return an unmodifiable list of the keys.
     */
    public List<String> getKeys() {
        List<String> keys = new ArrayList<String>(violations.size());
        for (Violation violation : violations)
            keys.add(violation.key());
        return unmodifiableList(keys);
    }

    private static String buildMessage(Class<?> configClass, List<Violation> violations) {
        StringBuilder result = new StringBuilder(configClass.getName());
        result.append(violations.size() == 1
                ? ": one property does not satisfy the constraints declared on it: "
                : ": " + violations.size() + " properties do not satisfy the constraints declared on them: ");
        for (int i = 0; i < violations.size(); i++) {
            if (i > 0)
                result.append("; ");
            result.append(violations.get(i));
        }
        return result.toString();
    }
}
