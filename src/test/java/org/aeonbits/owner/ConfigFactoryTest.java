/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.Config.Sources;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import static org.aeonbits.owner.UtilTest.save;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Luigi R. Viggiano
 */
public class ConfigFactoryTest {

    @Sources("file:${mypath}/myconfig.properties")
    interface MyConfig extends Config {
        @DefaultValue("defaultValue")
        String someValue();
    }

    @Before
    public void before() throws IOException {
        ConfigFactory.setProperties(new Properties());
        save(new File("target/test-generated-resources/myconfig.properties"), new Properties() {{
            setProperty("someValue", "foobar");
        }});
    }

    @Test
    public void testSetProperty()  {
        ConfigFactory.setProperty("mypath", "target/test-generated-resources");

        MyConfig cfg = ConfigFactory.create(MyConfig.class);

        assertEquals("foobar", cfg.someValue());
    }

    @Test(expected = NullPointerException.class)
    public void testSetPropertyNullKey() {
        ConfigFactory.setProperty(null, "foobar");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetPropertyEmptyKey() {
        ConfigFactory.setProperty("", "foobar");
    }

    @Test
    public void testSetPropertyTwice()  {
        assertNull(ConfigFactory.setProperty("mypath", "target/test-generated-resources"));
        assertEquals("target/test-generated-resources", ConfigFactory.setProperty("mypath", "target/test-generated-resources-2"));
        assertEquals("target/test-generated-resources-2", ConfigFactory.getProperty("mypath"));
    }

    @Test
    public void testGetProperties() {
        ConfigFactory.getProperties().setProperty("mypath", "target/test-generated-resources");

        MyConfig cfg = ConfigFactory.create(MyConfig.class);

        assertEquals("foobar", cfg.someValue());
    }

    @Test
    public void testSetProperties() {
        ConfigFactory.setProperties(new Properties() {{
            setProperty("mypath", "target/test-generated-resources");
        }});

        MyConfig cfg = ConfigFactory.create(MyConfig.class);

        assertEquals("foobar", cfg.someValue());
    }

    @Test
    public void testSetPropertiesNullObject() {
        ConfigFactory.setProperties(null);

        MyConfig cfg = ConfigFactory.create(MyConfig.class);

        assertEquals("defaultValue", cfg.someValue());
    }

    @Test
    public void testGetProperty() {
        ConfigFactory.setProperty("mypath", "target/test-generated-resources");
        assertEquals("target/test-generated-resources", ConfigFactory.getProperty("mypath"));
    }

    @Test(expected = NullPointerException.class)
    public void testGetPropertyNullKey() {
        ConfigFactory.getProperty(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetPropertiesEmptyKey() {
        ConfigFactory.getProperty("");
    }

    @Test
    public void testGetClearProperty() {
        ConfigFactory.setProperty("mypath", "target/test-generated-resources");
        assertEquals("target/test-generated-resources", ConfigFactory.clearProperty("mypath"));
        assertNull(ConfigFactory.getProperty("mypath"));
    }

    @Test(expected = NullPointerException.class)
    public void testClearPropertyNullKey() {
        ConfigFactory.clearProperty(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testClearPropertyEmptyKey() {
        ConfigFactory.clearProperty("");
    }

}
