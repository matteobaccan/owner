/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author Luigi R. Viggiano
 */
@RunWith(MockitoJUnitRunner.class)
public class ConfigTest {

    @Mock
    private ScheduledExecutorService scheduler;

    public interface SampleConfig extends Config {
        String hello(String param);

        @DefaultValue("Bohemian Rapsody - Queen")
        String favoriteSong();

        String unspecifiedProperty();

        @Key("server.http.port")
        int httpPort();

        @Key("salutation.text")
        @DefaultValue("Good Morning")
        String salutation();

        @Key("password")
        @DefaultValue("@#$%^&*()")
        String password();

        @DefaultValue("foo")
        void voidMethodWithValue();

        void voidMethodWithoutValue();

        @Key("environment")
        String env();

        @Key("environments.${environment}.browser")
        @DefaultValue("chrome")
        String usedBrowser();

        @Key("environments.${environment}.webdriver.${environments.${environment}.browser}.switches")
        String webDriverOptions();
    }

    @Test
    public void shouldNotReturnNull() {
        SampleConfig config = ConfigFactory.create(SampleConfig.class);
        assertNotNull(config);
    }

    @Test
    public void shouldDoReplacements() {
        SampleConfig config = ConfigFactory.create(SampleConfig.class, new Properties());
        assertEquals("Hello Luigi.", config.hello("Luigi"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testVoidMethodWithValue() {
        SampleConfig cfg = ConfigFactory.create(SampleConfig.class);
        cfg.voidMethodWithValue();
    }

    @Test
    public void testVoidMethodWithoutValue() {
        SampleConfig cfg = ConfigFactory.create(SampleConfig.class);
        cfg.voidMethodWithoutValue();
    }

    static interface StringSubstitutionConfig extends Config {
        @DefaultValue("Hello Mr. %s!")
        String helloMr(String name);
    }

    @Test
    public void testDefaultStringValue() {
        StringSubstitutionConfig config = ConfigFactory.create(StringSubstitutionConfig.class);
        assertEquals("Hello Mr. Luigi!", config.helloMr("Luigi"));
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

    /**
     * When a property value contains a '%' character but is not a String format,
     * we expect the property value to be returned as-is. Under the covers, the String.format
     * method is used for property expansion. We want to verify a property value that
     * contains a '%' character but is _not_ a format String is, indeed, supported.
     */
    @Test
    public void whenPropertyValueIsNotValidFormatString_thenPropertyValueShouldRemainIntact() {
        SampleConfig config = ConfigFactory.create(SampleConfig.class);

        assertEquals ("@#$%^&*()", config.password());
    }

    @Test
    public void nestedKeyExpansion() {
        Properties values = new Properties() {{
            setProperty("environment", "dev");
            setProperty("environments.dev.browser", "opera");
            setProperty("environments.dev.webdriver.opera.switches", "foo bar");
            setProperty("environments.dev.webdriver.chrome.switches", "foo baz");
        }};

        SampleConfig config = ConfigFactory.create(SampleConfig.class, values);
        assertEquals("foo bar", config.webDriverOptions());
    }
}
