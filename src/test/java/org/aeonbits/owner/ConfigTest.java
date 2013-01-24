/*
 * Copyright (c) 2012, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.Properties;

import static org.aeonbits.owner.ConfigFactory.loadPropertiesFor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Luigi R. Viggiano
 */
public class ConfigTest {

    @Test
    public void shouldNotReturnNull() {
        SampleConfig config = ConfigFactory.create(SampleConfig.class);
        assertNotNull(config);
    }

    @Test
    public void shouldReturnTheValueFromTheAssociatedProperties() {
        SampleConfig config = ConfigFactory.create(SampleConfig.class);
        assertEquals("testValue", config.testKey());
    }

    @Test
    public void shouldReturnTheResourceForAClass() throws IOException {
        ConfigURLStreamHandler handler = new ConfigURLStreamHandler(SampleConfig.class.getClassLoader(),
                new SystemVariablesExpander());
        ConfigURLStreamHandler spy = spy(handler);

        ConfigFactory.getPropertiesFor(SampleConfig.class, spy);
        URL expected =
                new URL(null, "classpath:org/aeonbits/owner/SampleConfig.properties", handler);
        verify(spy, times(1)).openConnection(eq(expected));
    }

    @Test
    public void shouldReturnThePropertiesForTheClass() {
        Properties props = loadPropertiesFor(SampleConfig.class);
        assertNotNull(props);
        assertEquals("testValue", props.getProperty("testKey"));
    }

    @Test
    public void shouldDoReplacements() {
        SampleConfig config = ConfigFactory.create(SampleConfig.class);
        assertEquals("Hello Luigi.", config.hello("Luigi"));
    }

    @Test
    public void shouldLoadURLFromSpecifiedSource() throws IOException {
        final URL[] lastURL = { null };
        ConfigURLStreamHandler handler = new ConfigURLStreamHandler(SampleConfigWithSource.class.getClassLoader(),
                new SystemVariablesExpander()) {
            @Override
            protected URLConnection openConnection(URL url) throws IOException {
                lastURL[0] = url;
                return super.openConnection(url);
            }
        };

        ConfigFactory.getPropertiesFor(SampleConfigWithSource.class, handler);
        URL expected = new URL(null, "classpath:org/aeonbits/owner/FooBar.properties",
                handler);
        assertEquals(expected, lastURL[0]);
    }

    @Test
    public void shouldLoadPropertiesFromSpecifiedSource() throws Exception {
        SampleConfigWithSource sample = ConfigFactory.create(SampleConfigWithSource.class);
        assertEquals("Hello World!", sample.helloWorld());
    }

    @Test
    public void shouldReturnNullProperty() {
        InvalidSourceConfig config = ConfigFactory.create(InvalidSourceConfig.class);
        assertNull(config.someProperty());
    }

    @Test
    public void testConfigWithoutPropertiesAssociated() {
        UnassociatedConfig config = ConfigFactory.create(UnassociatedConfig.class);
        assertNull(config.someProperty());
    }

    @Test
    public void testDefaultStringValue() {
        SampleConfigWithSource config = ConfigFactory.create(SampleConfigWithSource.class);
        assertEquals("Hello Mr. Luigi!", config.helloMr("Luigi"));
    }

    @Test
    public void testDefaultIntValue() {
        SampleConfigWithSource config = ConfigFactory.create(SampleConfigWithSource.class);
        assertEquals(42, config.answerToLifeUniverseAndEverything());
    }

    @Test
    public void testDefautDoubleValue() {
        SampleConfigWithSource config = ConfigFactory.create(SampleConfigWithSource.class);
        assertEquals(3.141592653589793D, config.pi(), 0.000000000000001D) ;
    }

    @Test
    public void testDefautFloatValue() {
        SampleConfigWithSource config = ConfigFactory.create(SampleConfigWithSource.class);
        assertEquals(0.5f, config.half(), 0.01f);
    }

    @Test
    public void testDefautBooleanValue() {
        SampleConfigWithSource config = ConfigFactory.create(SampleConfigWithSource.class);
        assertEquals(false, config.worldIsFlat());
    }

    @Test
    public void testDefaultIntegerValue() {
        SampleConfigWithSource config = ConfigFactory.create(SampleConfigWithSource.class);
        assertEquals(new Integer(7), config.daysInWeek());
    }

    @Test
    public void testDefaultPropertyOverridden() {
        SampleConfig config = ConfigFactory.create(SampleConfig.class);
        assertEquals("Speechless - Lady Gaga", config.favoriteSong());
    }

    @Test
    public void testUnspecifiedProperty() {
        SampleConfig config = ConfigFactory.create(SampleConfig.class);
        assertNull(config.unspecifiedProperty());
    }

    @Test
    public void testPropertyWithCustomizedKey() {
        SampleConfig config = ConfigFactory.create(SampleConfig.class);
        assertEquals(80, config.httpPort());
    }

    @Test
    public void testPropertyWithKeyAndDefaultValue() {
        SampleConfig config = ConfigFactory.create(SampleConfig.class);
        assertEquals("Good Afternoon", config.salutation());
    }

    @Test
    public void testPropertyWithExpansion() {
        SampleConfigWithExpansion config = ConfigFactory.create(SampleConfigWithExpansion.class);
        assertEquals("pink", config.favoriteColor());
    }

    @Test
    public void testListPrintStream() throws IOException {
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        loadPropertiesFor(SampleConfig.class).list(new PrintStream(expected, true));

        SampleConfig config = ConfigFactory.create(SampleConfig.class);
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        config.list(new PrintStream(result, true));

        assertEquals(expected.toString(), result.toString());
    }

    @Test
    public void testListPrintWriter() throws IOException {
        StringWriter expected = new StringWriter();
        loadPropertiesFor(SampleConfig.class).list(new PrintWriter(expected, true));

        SampleConfig config = ConfigFactory.create(SampleConfig.class);
        StringWriter result = new StringWriter();
        config.list(new PrintWriter(result, true));

        assertEquals(expected.toString(), result.toString());
    }
}
