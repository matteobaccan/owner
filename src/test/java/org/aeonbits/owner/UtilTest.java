/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

import static org.aeonbits.owner.Util.ignore;
import static org.aeonbits.owner.Util.unreachable;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Luigi R. Viggiano
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

    @Test
    public void testIgnore() {
        ignore();
    }

    @Test
    public void testUnreachable() {
        try {
            unreachable();
        } catch(AssertionError err) {
            assertEquals("this code should never be reached", err.getMessage());
        }
    }

    public static void save(File target, Properties p) throws IOException {
        target.getParentFile().mkdirs();
        p.store(new FileWriter(target), "saved for test");
    }

}
