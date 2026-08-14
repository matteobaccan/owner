/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
/**
 * The seam through which a Bean Validation provider checks a configuration, and the vocabulary the core uses
 * to talk about constraints without depending on a validation API.
 * <p>
 * The core recognises a constraint annotation by name and can therefore say that one is there; it cannot
 * check it, which needs <code>javax.validation</code> or <code>jakarta.validation</code> and a provider.
 * That work is done by a {@link org.aeonbits.owner.validation.ConfigValidator} found on the class path -
 * <code>owner-extras</code> ships one - and everything in this package exists so that the two can talk.
 * </p>
 */
package org.aeonbits.owner.validation;
