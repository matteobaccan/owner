/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
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

    /** Two sources that both exist, so that reading the second one would show. */
    @Sources({"classpath:org/aeonbits/owner/first.properties",
            "classpath:org/aeonbits/owner/second.properties"})
    public static interface TwoSourcesThatBothExist extends Config {
        String foo();

        String bar();
    }

    /**
     * <b>The default is FIRST</b>, and this is what saying so requires: the interface above declares no
     * {@link LoadPolicy}, and only its first source is read. The proof is the property that is
     * <em>missing</em> — <code>bar</code> exists in the second file and nowhere else, so a null is the one
     * observation that tells FIRST from MERGE. Asserting a value that both strategies produce says nothing,
     * which is what this test did until it was noticed that another one had exactly the same body.
     */
    @Test
    public void firstIsTheDefaultLoadStrategy() throws Exception {
        TwoSourcesThatBothExist config = ConfigFactory.create(TwoSourcesThatBothExist.class);

        assertEquals("first", config.foo());
        assertNull("the second source was never read, and that is what FIRST means", config.bar());
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

    /**
     * Reading the source that was named — the second of the four declared above, the others being absent.
     * It used to be written twice, letter for letter, once under this name and once under
     * {@code firstIsTheDefaultLoadStrategy}; the duplicate is what pointed at the other one saying less
     * than its name.
     */
    @Test
    public void shouldLoadPropertiesFromSpecifiedSource() throws Exception {
        SampleConfigWithSource sample = ConfigFactory.create(SampleConfigWithSource.class);
        assertEquals("Hello World!", sample.helloWorld());
    }
}
