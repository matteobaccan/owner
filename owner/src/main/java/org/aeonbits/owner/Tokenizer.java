/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

/**
 * Tokenizer interface that specifies how to split a single value into tokens to be used as elements for arrays and
 * collections.
 *
 * @author Luigi R. Viggiano
 * @since 1.0.4
 */
public interface Tokenizer {

    /**
     * Splits the given string, into tokens that identify single elements.
     *
     * @since 1.0.4
     * @param values the string representation for the properties values
     * @return the items identifying single elements to convert.
     */
    String[] tokens(String values);

}
