/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.interfaces;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Mutable;
import org.aeonbits.owner.util.UtilTest;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Luigi R. Viggiano
 */
public class MutableConfigTest {

    private MutableConfig cfg;

    interface MutableConfig extends Config, Mutable {
        @DefaultValue("18")
        public Integer minAge();
        public Integer maxAge();
    }

    @Before
    public void before() {
        cfg = ConfigFactory.create(MutableConfig.class);
    }

    @Test
    public void testSetProperty() {
        assertEquals(Integer.valueOf(18), cfg.minAge());
        String oldValue = cfg.setProperty("minAge", "21");
        assertEquals("18", oldValue);
        assertEquals(Integer.valueOf(21), cfg.minAge());
    }

    @Test
    public void testSetPropertyThatWasNull() {
        assertNull(cfg.maxAge());
        String oldValue = cfg.setProperty("maxAge", "999");
        assertNull(oldValue);
        assertEquals(Integer.valueOf(999), cfg.maxAge());
    }

    @Test
    public void testSetPropertyWithNull() {
        assertEquals(Integer.valueOf(18), cfg.minAge());
        String oldValue = cfg.setProperty("minAge", null);
        assertEquals("18", oldValue);
        assertNull(cfg.minAge());
    }

    @Test
    public void testRemoveProperty() {
        assertEquals(Integer.valueOf(18), cfg.minAge());
        String oldValue = cfg.removeProperty("minAge");
        assertEquals("18", oldValue);
        assertNull(cfg.minAge());
    }

    @Test
    public void testClear() {
        assertEquals(Integer.valueOf(18), cfg.minAge());
        cfg.clear();
        assertNull(cfg.minAge());
    }

    @Test
    public void testLoadInputStream() throws IOException {
        File temp = File.createTempFile("MutableConfigTest", ".properties");
        UtilTest.save(temp, new Properties() {{
            setProperty("minAge", "19");
            setProperty("maxAge", "99");
        }});

        cfg.load(new FileInputStream(temp));

        assertEquals(Integer.valueOf(19), cfg.minAge());
        assertEquals(Integer.valueOf(99), cfg.maxAge());
    }

    @Test
    public void testLoadReader() throws IOException {
        File temp = File.createTempFile("MutableConfigTest", ".properties");
        UtilTest.save(temp, new Properties() {{
            setProperty("minAge", "19");
            setProperty("maxAge", "99");
        }});

        cfg.load(new FileReader(temp));

        assertEquals(Integer.valueOf(19), cfg.minAge());
        assertEquals(Integer.valueOf(99), cfg.maxAge());
    }
}
