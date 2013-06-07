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
 * @author Luigi R. Viggiano
 */
public class MutableConfigTest {

    interface MutableConfig extends Config, Mutable {
        @DefaultValue("18")
        public Integer minAge();
        public Integer maxAge();
    }

    @Test
    public void testSetProperty() {
        MutableConfig cfg = ConfigFactory.create(MutableConfig.class);
        assertEquals(Integer.valueOf(18), cfg.minAge());
        String oldValue = cfg.setProperty("minAge", "21");
        assertEquals("18", oldValue);
        assertEquals(Integer.valueOf(21), cfg.minAge());
    }

    @Test
    public void testSetPropertyThatWasNull() {
        MutableConfig cfg = ConfigFactory.create(MutableConfig.class);
        assertNull(cfg.maxAge());
        String oldValue = cfg.setProperty("maxAge", "999");
        assertNull(oldValue);
        assertEquals(Integer.valueOf(999), cfg.maxAge());
    }

    @Test
    public void testSetPropertyWithNull() {
        MutableConfig cfg = ConfigFactory.create(MutableConfig.class);
        assertEquals(Integer.valueOf(18), cfg.minAge());
        String oldValue = cfg.setProperty("minAge", null);
        assertEquals("18", oldValue);
        assertNull(cfg.minAge());
    }

    @Test
    public void testRemoveProperty() {
        MutableConfig cfg = ConfigFactory.create(MutableConfig.class);
        assertEquals(Integer.valueOf(18), cfg.minAge());
        String oldValue = cfg.removeProperty("minAge");
        assertEquals("18", oldValue);
        assertNull(cfg.minAge());
    }

    @Test
    public void testClear() {
        MutableConfig cfg = ConfigFactory.create(MutableConfig.class);
        assertEquals(Integer.valueOf(18), cfg.minAge());
        cfg.clear();
        assertNull(cfg.minAge());
    }

}
