/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
/**
 * The Bean Validation provider the core looks for: what makes <code>&#64;Min</code>,
 * <code>&#64;NotNull</code> and the rest mean something on a method named the way this library teaches.
 * <p>
 * They did not, and quietly. <code>Validator.validate(object)</code> walks JavaBean properties, so
 * <code>&#64;Min(12) int getPort()</code> was checked and <code>&#64;Min(12) int port()</code> was not -
 * an annotation that reads like a guarantee and is not one.
 * {@link org.aeonbits.owner.extras.validation.BeanValidator} checks the value each method resolves to,
 * and {@link org.aeonbits.owner.extras.validation.ViolationPath} reports it under the key the property is
 * read with rather than under the name of a getter nobody wrote.
 * </p>
 * <p>
 * <b>Both spellings of the specification are supported and neither is required</b>:
 * {@link org.aeonbits.owner.extras.validation.JakartaBeanValidation} and
 * {@link org.aeonbits.owner.extras.validation.JavaxBeanValidation} are the only classes that name
 * <code>jakarta.validation</code> and <code>javax.validation</code>, so that the validator itself can be
 * discovered and instantiated on a class path that has neither.
 * </p>
 */
package org.aeonbits.owner.extras.validation;
