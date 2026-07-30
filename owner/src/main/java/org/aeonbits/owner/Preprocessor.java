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
@FunctionalInterface
public interface Preprocessor {
    /**
     * Pre-processes the given input string before it is used by the library.
     *
     * @param input the raw property value.
     * @return the processed property value.
     */
    String process(String input);
}
