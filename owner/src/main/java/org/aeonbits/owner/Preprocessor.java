/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

/**
 * Preprocessor interface specifies how to pre-process an input string coming from a property value before being used by
 * OWNER.
 *
 * @author Luigi R. Viggiano
 * @since 1.0.9
 */
public interface Preprocessor {
    String process(String input);
}
