/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author luigi
 */
public class MergeConfigTest {
    @Test
    public void testPropertyMerge() {
        MergeConfig cfg = ConfigFactory.create(MergeConfig.class);
        assertEquals("first", cfg.foo());
        assertEquals("second", cfg.bar());
        assertEquals("first", cfg.foo());
        assertEquals("third", cfg.qux());
        assertNull(cfg.quux());
        assertEquals("theDefaultValue", cfg.fubar());
        cfg.list(System.out);
    }
}
