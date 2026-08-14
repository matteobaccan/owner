/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.validation;

import javax.validation.Constraint;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * A constraint of the <code>javax</code> family, in the shape of <code>&#64;Min</code>: a lower bound on a
 * number, writable on the method or on a type argument of its return type.
 *
 * <p>
 * The tests of the core check constraints of their own rather than Hibernate Validator's, because what the
 * core claims is not "it understands <code>&#64;Min</code>" but "it recognises anything annotated as a
 * constraint, whoever wrote it". A constraint written by hand is the ordinary case in an application of any
 * size, and here it is also the only way to test the claim without the dependency the core does not have.
 * </p>
 *
 * @author Matteo Baccan
 */
@Documented
@Target({METHOD, TYPE_USE})
@Retention(RUNTIME)
@Constraint
public @interface AtLeast {

    /**
     * The lower bound the value has to reach.
     *
     * @return the minimum acceptable value.
     */
    int value();

    /**
     * The container of a repeated constraint, which by convention is a member type of the constraint it
     * repeats and carries no <code>&#64;Constraint</code> of its own - so recognising it is a rule about the
     * shape rather than about the annotation.
     */
    @Documented
    @Target({METHOD, TYPE_USE})
    @Retention(RUNTIME)
    @interface List {
        /**
         * The repeated constraints.
         *
         * @return the constraints.
         */
        AtLeast[] value();
    }
}
