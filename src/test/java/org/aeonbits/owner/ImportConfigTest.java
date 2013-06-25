/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertEquals;

/**
 * @author luigi
 */
public class ImportConfigTest {

    @Test
    public void testImport() {
        Properties props = new Properties();
        props.setProperty("foo", "pineapple");
        props.setProperty("bar", "lime");
        ImportConfig cfg = ConfigFactory.create(ImportConfig.class, props); // props imported!
        assertEquals("pineapple", cfg.foo());
        assertEquals("lime", cfg.bar());
        assertEquals("orange", cfg.baz());
    }

    @Test
    public void testImportOrder() {
        Properties p1 = new Properties();
        p1.setProperty("foo", "pineapple");
        p1.setProperty("bar", "lime");

        Properties p2 = new Properties();
        p2.setProperty("bar", "grapefruit");
        p2.setProperty("baz", "blackberry");

        ImportConfig cfg = ConfigFactory.create(ImportConfig.class, p1, p2); // props imported!

        assertEquals("pineapple", cfg.foo());
        assertEquals("lime", cfg.bar()); // p1 prevails, so this is lime and not grapefruit
        assertEquals("blackberry", cfg.baz());
    }

    interface ImportedPropertiesHaveHigherPriority extends Config {
        Integer minAge();
    }

    @Test
    public void testImportedPropertiesShouldOverrideSources() {
        ImportedPropertiesHaveHigherPriority cfg = ConfigFactory.create(ImportedPropertiesHaveHigherPriority.class);
        assertEquals(Integer.valueOf(18), cfg.minAge());

        ImportedPropertiesHaveHigherPriority cfg2 = ConfigFactory.create(ImportedPropertiesHaveHigherPriority.class,
                new Properties() {{
                    setProperty("minAge", "21");
                }},

                new Properties() {{
                    setProperty("minAge", "22");
                }}

        );

        assertEquals(Integer.valueOf(21), cfg2.minAge());
    }
}
