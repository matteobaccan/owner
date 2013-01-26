/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

/**
 * @author luigi
 */
public class ValueOf {
    private final String text;

    private ValueOf(String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return super.toString();
    }

    public ValueOf valueOf(String text) {
        return new ValueOf(text);
    }
}
