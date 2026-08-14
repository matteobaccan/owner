/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package jakarta.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * The other spelling of {@link javax.validation.Constraint}, which is the same annotation after the Jakarta
 * rename, declared here for the same reason: the core recognises both by name and depends on neither.
 *
 * <p>
 * Two of them rather than one because the two names are the whole of the compatibility question this
 * feature had to answer, and a rename that quietly stopped being recognised would put every
 * <code>jakarta</code> configuration back into the silence issue #201 is about.
 * </p>
 *
 * @author Matteo Baccan
 */
@Documented
@Target(ANNOTATION_TYPE)
@Retention(RUNTIME)
public @interface Constraint {
}
