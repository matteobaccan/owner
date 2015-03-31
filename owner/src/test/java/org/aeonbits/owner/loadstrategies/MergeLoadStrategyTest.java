/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.loadstrategies;

import static org.aeonbits.owner.Config.LoadType.MERGE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Properties;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Luigi R. Viggiano
 */
public class MergeLoadStrategyTest {

    private Properties defaultProps;

    @Test
    public void testPropertyMerge() {
        MergeConfig cfg = ConfigFactory.create(MergeConfig.class);
        assertEquals("first", cfg.foo());
        assertEquals("second", cfg.bar());
        assertEquals("first", cfg.foo());
        assertEquals("third", cfg.qux());
        assertNull(cfg.quux());
        assertEquals("theDefaultValue", cfg.fubar());
    }

    @Sources({"classpath:org/aeonbits/owner/first.properties",
              "classpath:foo/bar/thisDoesntExists.properties",
              "classpath:org/aeonbits/owner/second.properties",
              "file:${user.dir}/src/test/resources/foo/bar/thisDoesntExists.properties",
              "file:${user.dir}/src/test/resources/org/aeonbits/owner/third.properties"})
    @LoadPolicy(MERGE)
    public static interface MergeConfig extends Config {
        @DefaultValue("this should be ignored")
        String foo();

        @DefaultValue("this should be ignored")
        String bar();

        @DefaultValue("this should be ignored")
        String baz();

        @DefaultValue("this should be ignored")
        String qux();

        String quux(); // this should return null;

        @DefaultValue("theDefaultValue")
        String fubar();
    }

    @Sources("httpz://foo.bar.baz")
    @LoadPolicy(MERGE)
    interface InvalidURLConfig extends Config {

    }

    @Test(expected = UnsupportedOperationException.class)
    public void testWhenURLIsInvalid() {
        ConfigFactory.create(InvalidURLConfig.class);
    }

    @Sources({"system:properties",
            "system:env"})
    @LoadPolicy(MERGE)
    public static interface SystemPropertiesConfig extends Config {
        @DefaultValue("this should be ignored")
        String foo();

        @DefaultValue("this should be ignored")
        String bar();

        @DefaultValue("user.home")
        String userHome();

        @Key("PATH")
        String path();
        
        String nullProp();

        @DefaultValue("theDefaultValue")
        String useDefault();
    }

    @Test
    public void loadSystemProperties() {
        System.setProperty("foo", "FOO");
        System.setProperty("bar", "BAR");

        SystemPropertiesConfig config = ConfigFactory.create(SystemPropertiesConfig.class);

        assertEquals("FOO", config.foo());
        assertEquals("BAR", config.bar());
    }

    @Test
    public void includeDefaultSystemProperties() {
        SystemPropertiesConfig config = ConfigFactory.create(SystemPropertiesConfig.class);
        assertNotNull(config.userHome());
    }

    @Test
    public void defaultValueFromConfig() {
        SystemPropertiesConfig config = ConfigFactory.create(SystemPropertiesConfig.class);
        assertEquals("theDefaultValue", config.useDefault());
    }

    @Test
    public void nullProp() {
        SystemPropertiesConfig config = ConfigFactory.create(SystemPropertiesConfig.class);
        assertNull(config.nullProp());
    }

    @Test
    public void pathEnvVariable(){
        SystemPropertiesConfig config = ConfigFactory.create(SystemPropertiesConfig.class);
        assertNotNull(config.path());
    }

    @Before
    public void storeDefaultSysProps(){
        defaultProps = new Properties(System.getProperties());
    }
    
    @After
    public void cleanSystemProperties() {
        System.setProperties(defaultProps);
    }
}
