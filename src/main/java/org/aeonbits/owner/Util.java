/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import java.util.Arrays;
import java.util.Collections;

import static java.lang.System.arraycopy;

/**
 * @author Luigi R. Viggiano
 */
class Util {
    Util() {
        prohibitInstantiation();
    }

    static void prohibitInstantiation() {
        throw new UnsupportedOperationException("This class is not supposed to be instantiated.");
    }

    static <T> T[] reverse(T[] array) {
        T[] copy = array.clone();
        arraycopy(array, 0, copy, 0, array.length);
        Collections.reverse(Arrays.asList(copy));
        return copy;
    }

}
