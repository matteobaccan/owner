/*
 * Copyright (c) 2012-2016, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.util.bytesize;

/**
 * Represents the possible standards that a {@link ByteSizeUnit} can have. Different standards represent different
 * "power of" values for which byte sizes are defined in.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Binary_prefix">https://en.wikipedia.org/wiki/Binary_prefix</a>
 *
 * @author Stefan Freyr Stefansson
 */
public enum ByteSizeStandard {

    /**
     * The International System of Units (SI) standard. Base of 1000.
     */
    SI(1000),

    /**
     * The International Electrotechnical Commission (IEC) standard. Base of 1024.
     */
    IEC(1024);

    final int powerOf;

    ByteSizeStandard(int powerOf){
        this.powerOf = powerOf;
    }
}
