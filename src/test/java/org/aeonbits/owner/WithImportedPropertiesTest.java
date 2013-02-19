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
public class WithImportedPropertiesTest {

    @Test
    public void testSubstituteWithImports() {
        Properties propsFromTest = new Properties();
        propsFromTest.setProperty("external", "propsFromTest");
        WithImportedProperties conf = ConfigFactory.create(WithImportedProperties.class, propsFromTest);
        assertEquals("testing replacement from propsFromTest properties file.", conf.someValue());
    }

    @Test
    public void testSystemProperty() {
        String userHome = System.getProperty("user.home");
        WithImportedProperties conf = ConfigFactory.create(WithImportedProperties.class, System.getProperties());
        assertEquals(userHome, conf.userHome());
    }

    @Test
    public void testSystemEnv() {
        String envHome = System.getenv("HOME");
        WithImportedProperties conf = ConfigFactory.create(WithImportedProperties.class, System.getenv());
        assertEquals(envHome, conf.envHome());
    }

    @Test
    public void testMultipleImports() {
        Properties propsFromTest = new Properties();
        propsFromTest.setProperty("external", "propsFromTest");

        String userHome = System.getProperty("user.home");
        String envHome = System.getenv("HOME");
        WithImportedProperties conf =
                ConfigFactory.create(WithImportedProperties.class,
                        propsFromTest, System.getProperties(), System.getenv());
        assertEquals(userHome, conf.userHome());
        assertEquals(envHome, conf.envHome());
        assertEquals("testing replacement from propsFromTest properties file.", conf.someValue());
    }

    /**
     * @author luigi
     */
    public static interface WithImportedProperties extends Config {
        String someValue();

        @DefaultValue("${user.home}")
        String userHome();

        @DefaultValue("${HOME}")
        String envHome();
    }
}
