/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * An annotation that is not a constraint, so that "carries constraints" can be told from "carries
 * annotations". A configuration interface is usually covered in the second kind - this library's own
 * annotations are all of them - and none of it must be mistaken for the first.
 *
 * @author Matteo Baccan
 */
@Documented
@Target({METHOD, TYPE_USE})
@Retention(RUNTIME)
public @interface Decorative {
}
