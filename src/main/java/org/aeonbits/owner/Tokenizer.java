/*
 * Copyright (c) 2013, Luigi R. Viggiano
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
 * @author L
 * @since 1.0.4
 */
public interface Tokenizer {
    String[] tokens(String values);
}
