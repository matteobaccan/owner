/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.validation;

import java.io.Serializable;

/**
 * One constraint that a property value did not satisfy.
 *
 * <p>
 * {@link Serializable} because {@link ConfigValidationException} carries a list of these and an exception
 * travels: three strings, which is all this is, cross a wire without difficulty, while the provider's own
 * violation object does not.
 * </p>
 *
 * <p>
 * <b>The value that failed is not here, and that is deliberate.</b> A violation ends up in an exception
 * message and an exception message ends up in a log, which is exactly where a configuration value must not
 * go - it is what {@link org.aeonbits.owner.Config.Sensitive} exists for, and a password too short is
 * precisely the kind of violation this would print. The key says which line to go and look at, which is all
 * the reader needs and all they are owed.
 * </p>
 *
 * @author Matteo Baccan
 * @since 2.0.0
 */
public final class Violation implements Serializable {

    private static final long serialVersionUID = 1L;

    /** The key of the property that failed, which is the line somebody has to go and change. */
    private final String key;

    /** The method the constraint is written on, for finding it in the interface. */
    private final String methodName;

    /** What the provider said, interpolated and localized by it. Never the value: see the class comment. */
    private final String message;

    /**
     * Builds a violation.
     *
     * @param key        the key of the property that failed the constraint.
     * @param methodName the name of the method the constraint is written on.
     * @param message    the message the validation provider produced, interpolated and localized by it.
     */
    public Violation(String key, String methodName, String message) {
        this.key = key;
        this.methodName = methodName;
        this.message = message;
    }

    /**
     * The key of the property that failed.
     *
     * @return the property key, never <code>null</code>.
     */
    public String key() {
        return key;
    }

    /**
     * The name of the method the constraint is written on.
     *
     * @return the method name, never <code>null</code>.
     */
    public String methodName() {
        return methodName;
    }

    /**
     * What the validation provider said about it, in the provider's own words and language.
     *
     * @return the violation message, never <code>null</code>.
     */
    public String message() {
        return message;
    }

    /**
     * The violation as it appears in the exception message: the key first, since that is what has to be
     * changed, then the method it is declared on, then what is wrong with it.
     *
     * @return a one-line description of this violation.
     */
    @Override
    public String toString() {
        return "'" + key + "' (" + methodName + "()): " + message;
    }
}
