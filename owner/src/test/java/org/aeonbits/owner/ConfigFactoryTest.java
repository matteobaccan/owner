/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.Config.Sources;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import static org.aeonbits.owner.util.UtilTest.save;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

/**
 * @author Luigi R. Viggiano
 */
public class ConfigFactoryTest implements TestConstants {

    @Sources("file:${mypath}/myconfig.properties")
    interface MyConfig extends Config {
        @DefaultValue("defaultValue")
        String someValue();
    }

    @Before
    public void before() throws IOException {
        ConfigFactory.setProperties(null);
        save(new File(RESOURCES_DIR + "/myconfig.properties"), new Properties() {{
            setProperty("someValue", "foobar");
        }});
    }

    @Test
    public void testSetProperty()  {
        ConfigFactory.setProperty("mypath", RESOURCES_DIR);

        MyConfig cfg = ConfigFactory.create(MyConfig.class);

        assertEquals("foobar", cfg.someValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetPropertyNullKey() {
        ConfigFactory.setProperty(null, "foobar");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetPropertyEmptyKey() {
        ConfigFactory.setProperty("", "foobar");
    }

    @Test
    public void testSetPropertyTwice()  {
        assertNull(ConfigFactory.setProperty("mypath", RESOURCES_DIR));
        assertEquals(RESOURCES_DIR, ConfigFactory.setProperty("mypath", RESOURCES_DIR + "-2"));
        assertEquals(RESOURCES_DIR + "-2", ConfigFactory.getProperty("mypath"));
    }

    @Test
    public void testGetProperties() {
        ConfigFactory.getProperties().setProperty("mypath", RESOURCES_DIR);

        MyConfig cfg = ConfigFactory.create(MyConfig.class);

        assertEquals("foobar", cfg.someValue());
    }

    @Test
    public void testSetProperties() {
        ConfigFactory.setProperties(new Properties() {{
            setProperty("mypath", RESOURCES_DIR);
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
        ConfigFactory.setProperty("mypath", RESOURCES_DIR);
        assertEquals(RESOURCES_DIR, ConfigFactory.getProperty("mypath"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetPropertyNullKey() {
        ConfigFactory.getProperty(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetPropertiesEmptyKey() {
        ConfigFactory.getProperty("");
    }

    @Test
    public void testGetClearProperty() {
        ConfigFactory.setProperty("mypath", RESOURCES_DIR);
        assertEquals(RESOURCES_DIR, ConfigFactory.clearProperty("mypath"));
        assertNull(ConfigFactory.getProperty("mypath"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testClearPropertyNullKey() {
        ConfigFactory.clearProperty(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testClearPropertyEmptyKey() {
        ConfigFactory.clearProperty("");
    }

    @Sources("${myurl}")
    interface MyConfigWithoutProtocol extends Config, Accessible {
        @DefaultValue("defaultValue")
        String someValue();
    }

    @Test
    public void testSetPropertyWithoutProtocol() {
        ConfigFactory.setProperty("mypath", RESOURCES_DIR);
        ConfigFactory.setProperty("myurl", "file:${mypath}/myconfig.properties");

        MyConfigWithoutProtocol cfg = ConfigFactory.create(MyConfigWithoutProtocol.class);

        assertEquals("foobar", cfg.someValue());
    }

    @Test
    public void testSetPropertyWithoutProtocolWhenFileIsNotFound()  {
        ConfigFactory.setProperty("mypath", RESOURCES_DIR);
        ConfigFactory.setProperty("myurl", "file:${mypath}/non-existent.properties");

        MyConfigWithoutProtocol cfg = ConfigFactory.create(MyConfigWithoutProtocol.class);

        assertEquals("defaultValue", cfg.someValue());
        assertThat(cfg.propertyNames(), contains("someValue"));
        assertThat(cfg.propertyNames().size(), is(1));
    }

    @After
    public void after() {
        ConfigFactory.setProperties(null); // clean up things.
    }
}
