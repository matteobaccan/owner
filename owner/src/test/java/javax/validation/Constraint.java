/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package javax.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Bean Validation's <code>&#64;Constraint</code>, declared here in the test sources of the core, where
 * <b>no validation API is a dependency and none is going to become one</b>.
 *
 * <p>
 * {@link org.aeonbits.owner.validation.Constraints} recognises a constraint by the name of this annotation
 * and reads nothing out of it, precisely so that the dependency-free core can see that a configuration
 * carries constraints. A test written against the real artifact would prove that a validation API works; this
 * one proves what is actually claimed, which is that the name is all that is needed.
 * </p>
 *
 * <p>
 * Only what is read is declared. The real annotation has a <code>validatedBy</code> naming the classes that
 * implement the check, and nothing here looks at it.
 * </p>
 *
 * @author Matteo Baccan
 */
@Documented
@Target(ANNOTATION_TYPE)
@Retention(RUNTIME)
public @interface Constraint {
}
