/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.importedprops;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.util.SystemProviderForTest;
import org.aeonbits.owner.util.UtilTest;
import org.junit.Test;

import java.util.HashMap;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

/**
 * @author Luigi R. Viggiano
 */
public class WithImportedPropertiesTest {

    @Test
    public void testSubstituteWithImports() {
        Properties propsFromTest = new Properties();
        propsFromTest.setProperty("external", "propsFromTest");
        WithImportedProperties conf = ConfigFactory.create
                (WithImportedProperties.class, propsFromTest);
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
        Object save = UtilTest.setSystem(
                new SystemProviderForTest(
                        new Properties() {{
                            setProperty("user.home", "/home/foobar");
                        }},
                        new HashMap<String, String>() {{
                            put("HOME", "/home/foobar");
                        }}
                ));
        try {
            String envHome = UtilTest.getenv("HOME");
            WithImportedProperties conf = ConfigFactory.create(WithImportedProperties.class, UtilTest.getenv());
            assertEquals(envHome, conf.envHome());
        } finally {
            UtilTest.setSystem(save);
        }
    }

    @Test
    public void testMultipleImports() {
        Object save = UtilTest.setSystem(
                new SystemProviderForTest(
                        new Properties() {{
                            setProperty("user.home", "/home/foobar");
                        }},
                        new HashMap<String, String>() {{
                            put("HOME", "/home/foobar");
                        }}
                ));
        try {
            Properties propsFromTest = new Properties();
            propsFromTest.setProperty("external", "propsFromTest");

            String userHome = UtilTest.getSystemProperty("user.home");
            String envHome = UtilTest.getenv("HOME");
            WithImportedProperties conf =
                    ConfigFactory.create(WithImportedProperties.class,
                            propsFromTest, UtilTest.getSystemProperties(), UtilTest.getenv());
            assertEquals(userHome, conf.userHome());
            assertEquals(envHome, conf.envHome());
            assertEquals("testing replacement from propsFromTest properties file.", conf.someValue());
        } finally {
            UtilTest.setSystem(save);
        }
    }

    @Test
    public void testBackSlash() {
        Properties propsFromTest = new Properties();
        propsFromTest.setProperty("external", "propsFromTest");
        String winPath = "C:\\windows\\path";
        propsFromTest.setProperty("value.with.backslash", winPath);

        WithImportedProperties conf =
                ConfigFactory.create(WithImportedProperties.class,
                        propsFromTest);

        assertEquals(winPath, conf.valueWithBackslash());
    }

    @Test
    public void testPropertyComingFromExternalObject() {
        Properties propsFromTest = new Properties();
        propsFromTest.setProperty("external", "propsFromTest");

        WithImportedProperties conf =
                ConfigFactory.create(WithImportedProperties.class,
                        propsFromTest);

        assertEquals("propsFromTest", conf.external());
    }

    public static interface WithImportedProperties extends Config {
        String someValue();

        @DefaultValue("${user.home}")
        String userHome();

        @DefaultValue("${HOME}")
        String envHome();

        @DefaultValue("${value.with.backslash}")
        String valueWithBackslash();

        String external();
    }
}
