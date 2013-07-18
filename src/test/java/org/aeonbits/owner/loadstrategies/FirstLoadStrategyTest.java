/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.loadstrategies;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.ConfigURLStreamHandlerForTest;
import org.aeonbits.owner.PropertiesManagerForTest;
import org.aeonbits.owner.VariablesExpanderForTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

import static org.aeonbits.owner.Config.LoadType.FIRST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Luigi R. Viggiano
 */
@RunWith(MockitoJUnitRunner.class)
public class FirstLoadStrategyTest {
    @Mock
    private ScheduledExecutorService scheduler;
    private VariablesExpanderForTest expander = new VariablesExpanderForTest(new Properties());


    @Sources({"classpath:foo/bar/baz.properties",
            "file:~/.testfoobar.blahblah",
            "file:/etc/testfoobar.blahblah",
            "classpath:org/aeonbits/owner/FooBar.properties",  // it will be loaded from here
            "file:~/blahblah.properties"})
    public static interface SampleConfigWithSource extends Config {
        String helloWorld(); 
    }

    @Test
    public void firstIsTheDefaultLoadStrategy() throws Exception {
        SampleConfigWithSource sample = ConfigFactory.create(SampleConfigWithSource.class);
        assertEquals("Hello World!", sample.helloWorld());
    }

    @Sources({"classpath:foo/bar/baz.properties",
            "file:~/.testfoobar.blahblah",
            "file:/etc/testfoobar.blahblah",
            "classpath:org/aeonbits/owner/FooBar.properties",  // it will be loaded from here
            "file:~/blahblah.properties"})
    @LoadPolicy(FIRST)
    public static interface SampleConfigrationWithFirstLoadStrategy extends Config {
        String helloWorld();
    }

    @Test
    public void shouldLoadFromTheFirstAvailableResource() throws Exception {
        SampleConfigrationWithFirstLoadStrategy sample = ConfigFactory.create(SampleConfigrationWithFirstLoadStrategy.class);
        assertEquals("Hello World!", sample.helloWorld());
    }


    @Sources("httpz://foo.bar.baz")
    interface InvalidURLConfig extends Config {

    }

    @Test(expected = UnsupportedOperationException.class)
    public void testWhenURLIsInvalid() {
        ConfigFactory.create(InvalidURLConfig.class);
    }

    @Sources("classpath:foo/bar/thisDoesntExists.properties")
    public static interface InvalidSourceConfig extends Config {
        public String someProperty();
    }

    @Test
    public void shouldReturnNullProperty() {
        InvalidSourceConfig config = ConfigFactory.create(InvalidSourceConfig.class);
        assertNull(config.someProperty());
    }

    @Test
    public void shouldLoadURLFromSpecifiedSource() throws IOException {
        final URL[] lastURL = { null };
        ConfigURLStreamHandlerForTest handler = new ConfigURLStreamHandlerForTest(SampleConfigWithSource.class.getClassLoader(),
                new VariablesExpanderForTest(new Properties())) {
            @Override
            public URLConnection openConnection(URL url) throws IOException {
                lastURL[0] = url;
                return super.openConnection(url);
            }
        };
        PropertiesManagerForTest manager = new PropertiesManagerForTest(SampleConfigWithSource.class, new Properties(), scheduler, expander);
        manager.doLoad(handler);
        URL expected = new URL(null, "classpath:org/aeonbits/owner/FooBar.properties",
                handler);
        assertEquals(expected, lastURL[0]);
    }

    @Test
    public void shouldLoadPropertiesFromSpecifiedSource() throws Exception {
        SampleConfigWithSource sample = ConfigFactory.create(SampleConfigWithSource.class);
        assertEquals("Hello World!", sample.helloWorld());
    }

}
