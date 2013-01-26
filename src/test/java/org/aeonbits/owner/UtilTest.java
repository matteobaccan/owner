/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertTrue;

/**
 * @author luigi
 */
public class UtilTest {
    @Test(expected = UnsupportedOperationException.class)
    public void testConstructor() {
        new Util(){};
    }

    @Test
    public void testReverse() {
        Integer[] i = { 1, 2, 3, 4, 5};
        Integer[] result = Util.reverse(i);
        assertTrue(Arrays.equals(new Integer[]{1, 2, 3, 4, 5}, i));
        assertTrue(Arrays.equals(new Integer[]{5, 4, 3, 2, 1}, result));
    }
}
