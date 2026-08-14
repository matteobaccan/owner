/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.validation;

import jakarta.validation.Constraint;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * A constraint of the <code>jakarta</code> family, in the shape of <code>&#64;NotNull</code>. Its only job
 * here is to be recognised under the other name.
 *
 * @author Matteo Baccan
 */
@Documented
@Target({METHOD, TYPE_USE})
@Retention(RUNTIME)
@Constraint
public @interface Required {
}
