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
public class InvalidValueOf {
    private final String text;

    private InvalidValueOf(String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return super.toString();
    }

    public InvalidValueOf valueOf(String text) {
        return new InvalidValueOf(text);
    }
}
