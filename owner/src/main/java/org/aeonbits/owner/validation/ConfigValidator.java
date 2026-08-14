/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.validation;

import java.util.List;

/**
 * Checks the constraint annotations of a configuration, which the core can see but cannot check.
 *
 * <p>
 * Implementations are found through {@link java.util.ServiceLoader}, declared in
 * <code>META-INF/services/org.aeonbits.owner.validation.ConfigValidator</code>, exactly as
 * {@link org.aeonbits.owner.loaders.Loader} is. <code>owner-extras</code> ships one for Bean Validation;
 * this interface is here so that it can be shipped from outside the dependency-free core, and it is public
 * so that anyone with another idea of what a constraint means can put one in its place.
 * </p>
 *
 * <h2>What it is called with, and what it is not</h2>
 *
 * <p>
 * Only the properties that <b>carry a constraint</b> and that the library was willing to read arrive here,
 * each with its value already obtained - see {@link ConstrainedProperty}. A configuration with no constraint
 * annotation on it never reaches a validator at all, which is what keeps this free for everybody who is not
 * using it.
 * </p>
 *
 * <p>
 * The shapes that are left out are left out by the library and not by the validator, and none of them is
 * left out quietly: they are reported, and refused outright under <code>owner.strict</code>. A method taking
 * arguments has no key until it is called and nobody knows what to call it with; a <code>default</code>
 * method is the user's own code rather than a property; an accessor returning a nested interface answers
 * with a view of the properties, which is never null and never absent, so a constraint on it could not fail.
 * A constraint written on a method returning an {@link java.util.Optional} or a collection is checked
 * against the <em>container</em>, which is likewise never absent -
 * <code>Optional&lt;&#64;Min(12) Integer&gt;</code> is the spelling that says something - and that too is
 * reported rather than left to look like a check.
 * </p>
 *
 * <h2>What is expected back</h2>
 *
 * <p>
 * Every violation found, not the first: a configuration is read once at startup and being told all of it at
 * once is the difference between one restart and five. That is the shape
 * {@link org.aeonbits.owner.MissingMandatoryPropertyException} already has, and the two are answering the
 * same question about the same file.
 * </p>
 *
 * <p>
 * A validator that <b>cannot run at all</b> - its API is there, since the constraints resolved, but no
 * provider is - must say so by throwing, naming what is missing and what to add, rather than returning no
 * violations. Returning none is the answer that means "these constraints hold", and it is not something to
 * say when nothing was checked.
 * </p>
 *
 * @author Matteo Baccan
 * @since 2.0.0
 */
public interface ConfigValidator {

    /**
     * Checks the given properties against the constraints their methods declare.
     *
     * @param properties the properties to check, never empty.
     * @return the violations found, in no particular order; an empty list when every constraint holds.
     * @throws UnsupportedOperationException if the constraints cannot be checked at all, for instance
     *                                       because no validation provider is on the class path.
     */
    List<Violation> validate(List<ConstrainedProperty> properties);
}
