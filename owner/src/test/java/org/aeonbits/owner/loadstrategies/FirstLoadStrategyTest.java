/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.loadstrategies;

import static org.aeonbits.owner.Config.LoadType.FIRST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.LoadersManagerForTest;
import org.aeonbits.owner.PropertiesManagerForTest;
import org.aeonbits.owner.VariablesExpanderForTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * @author Luigi R. Viggiano
 */
@RunWith(MockitoJUnitRunner.class)
public class FirstLoadStrategyTest extends LoadStrategyTestBase {
    @Mock
    private ScheduledExecutorService scheduler;
    @Spy
    private final LoadersManagerForTest loaders = new LoadersManagerForTest();
    private final VariablesExpanderForTest expander = new VariablesExpanderForTest(new Properties());


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
        Properties props = new Properties();
        PropertiesManagerForTest manager =
                new PropertiesManagerForTest(SampleConfigWithSource.class, props,
                        scheduler, expander, loaders);
        manager.load();
        verify(loaders, times(1)).findLoader(argThat(uriMatches("org/aeonbits/owner/FooBar.properties")));
        assertEquals("Hello World!", props.getProperty("helloWorld"));
    }

    @Test
    public void shouldLoadPropertiesFromSpecifiedSource() throws Exception {
        SampleConfigWithSource sample = ConfigFactory.create(SampleConfigWithSource.class);
        assertEquals("Hello World!", sample.helloWorld());
    }
}
