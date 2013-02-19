/*
 * Copyright (c) 2012, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.Config.Sources;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.Properties;

import static org.aeonbits.owner.PropertiesLoader.doLoad;
import static org.aeonbits.owner.PropertiesLoader.load;
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

    /**
     * @author Luigi R. Viggiano
     */
    public static interface SampleConfig extends Config {
        String testKey();

        String hello(String param);

        @DefaultValue("Bohemian Rapsody - Queen")
        String favoriteSong();

        String unspecifiedProperty();

        @Key("server.http.port")
        int httpPort();

        @Key("salutation.text")
        @DefaultValue("Good Morning")
        String salutation();

        void list(PrintStream out);
        void list(PrintWriter out);
    }

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

        doLoad(SampleConfig.class, spy);
        URL expected =
                new URL(null, "classpath:org/aeonbits/owner/ConfigTest$SampleConfig.properties", handler);
        verify(spy, times(1)).openConnection(eq(expected));
    }

    @Test
    public void shouldReturnThePropertiesForTheClass() {
        Properties props = load(SampleConfig.class);
        assertNotNull(props);
        assertEquals("testValue", props.getProperty("testKey"));
    }

    @Test
    public void shouldDoReplacements() {
        SampleConfig config = ConfigFactory.create(SampleConfig.class);
        assertEquals("Hello Luigi.", config.hello("Luigi"));
    }

    /**
     * @author Luigi R. Viggiano
     */
    @Sources({"classpath:foo/bar/baz.properties",
            "file:~/.testfoobar.blahblah",
            "file:/etc/testfoobar.blahblah",
            "classpath:org/aeonbits/owner/FooBar.properties",
            "file:~/blahblah.properties"})
    public static interface SampleConfigWithSource extends Config {
        //  @Key("hello.world");
        //  @DefaultValue("Hello World");
        String helloWorld();

        @DefaultValue("Hello Mr. %s!")
        String helloMr(String name);

        @DefaultValue("42")
        int answerToLifeUniverseAndEverything();

        @DefaultValue("3.141592653589793")
        double pi();

        @DefaultValue("0.5")
        float half();

        @DefaultValue("false")
        boolean worldIsFlat();

        @DefaultValue("7")
        Integer daysInWeek();
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

        doLoad(SampleConfigWithSource.class, handler);
        URL expected = new URL(null, "classpath:org/aeonbits/owner/FooBar.properties",
                handler);
        assertEquals(expected, lastURL[0]);
    }

    @Test
    public void shouldLoadPropertiesFromSpecifiedSource() throws Exception {
        SampleConfigWithSource sample = ConfigFactory.create(SampleConfigWithSource.class);
        assertEquals("Hello World!", sample.helloWorld());
    }

    /**
     * @author Luigi R. Viggiano
     */
    @Sources("classpath:foo/bar/thisDoesntExists.properties")
    public static interface InvalidSourceConfig extends Config {
        public String someProperty();
    }

    @Test
    public void shouldReturnNullProperty() {
        InvalidSourceConfig config = ConfigFactory.create(InvalidSourceConfig.class);
        assertNull(config.someProperty());
    }

    /**
     * @author Luigi R. Viggiano
     */
    public static interface UnassociatedConfig extends Config {
        String someProperty();
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

    /**
     * @author Luigi R. Viggiano
     */
    @Sources({"file:${user.dir}/src/test/resources/test.properties"})
    public static interface SampleConfigWithExpansion extends Config {
        public String favoriteColor();
    }

    @Test
    public void testPropertyWithExpansion() {
        SampleConfigWithExpansion config = ConfigFactory.create(SampleConfigWithExpansion.class);
        assertEquals("pink", config.favoriteColor());
    }

    @Test
    public void testListPrintStream() throws IOException {
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        load(SampleConfig.class).list(new PrintStream(expected, true));

        SampleConfig config = ConfigFactory.create(SampleConfig.class);
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        config.list(new PrintStream(result, true));

        assertEquals(expected.toString(), result.toString());
    }

    @Test
    public void testListPrintWriter() throws IOException {
        StringWriter expected = new StringWriter();
        load(SampleConfig.class).list(new PrintWriter(expected, true));

        SampleConfig config = ConfigFactory.create(SampleConfig.class);
        StringWriter result = new StringWriter();
        config.list(new PrintWriter(result, true));

        assertEquals(expected.toString(), result.toString());
    }

    public static interface SubstituteAndFormat extends Config {
        @DefaultValue("Hello ${mister}")
        String salutation(String name);

        @DefaultValue("Mr. %s")
        String mister(String name);
    }

    @Test
    public void testSubstitutionAndFormat() {
        SubstituteAndFormat cfg = ConfigFactory.create(SubstituteAndFormat.class);
        assertEquals("Hello Mr. Luigi", cfg.salutation("Luigi"));
        assertEquals("Mr. Luigi", cfg.mister("Luigi"));
    }


}
