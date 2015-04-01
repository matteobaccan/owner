package org.aeonbits.owner.loaders;

import static org.aeonbits.owner.Config.LoadType.FIRST;
import static org.aeonbits.owner.Config.LoadType.MERGE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.junit.After;
import org.junit.Test;

/**
 * Date: Mar 31, 2015 Copyright 2015 Motif Investing, Inc.
 *
 * @author ting-kuanwu
 */
public class SystemLoaderTest {

    private SystemPropertiesHelper systemPropertiesHelper = new SystemPropertiesHelper();

    @Config.Sources({"system:properties",
            "system:env"})
    @Config.LoadPolicy(FIRST)
    public static interface FirstConfig extends Config {
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
    public void first_loadSystemProperties() {
        systemPropertiesHelper.setProperty("foo", "FOO");
        systemPropertiesHelper.setProperty("bar", "BAR");

        FirstConfig config = ConfigFactory.create(FirstConfig.class);

        assertEquals("FOO", config.foo());
        assertEquals("BAR", config.bar());
    }

    @Test
    public void first_nullProp() {
        FirstConfig config = ConfigFactory.create(FirstConfig.class);
        assertNull(config.nullProp());
    }

    @Test
    public void first_loadSysPropFirst_ignoreEnvVars() {
        FirstConfig config = ConfigFactory.create(FirstConfig.class);
        assertNull(config.path());
    }

    @Config.Sources({"system:properties",
            "system:env"})
    @Config.LoadPolicy(MERGE)
    public static interface MergeConfig extends Config {
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
    public void merge_loadSystemProperties() {
        systemPropertiesHelper.setProperty("foo", "FOO");
        systemPropertiesHelper.setProperty("bar", "BAR");

        MergeConfig config = ConfigFactory.create(MergeConfig.class);

        assertEquals("FOO", config.foo());
        assertEquals("BAR", config.bar());
    }

    @Test
    public void merge_includeDefaultSystemProperties() {
        MergeConfig config = ConfigFactory.create(MergeConfig.class);
        assertNotNull(config.userHome());
    }

    @Test
    public void merge_defaultValueFromConfig() {
        MergeConfig config = ConfigFactory.create(MergeConfig.class);
        assertEquals("theDefaultValue", config.useDefault());
    }

    @Test
    public void merge_nullProp() {
        MergeConfig config = ConfigFactory.create(MergeConfig.class);
        assertNull(config.nullProp());
    }

    @Test
    public void merge_pathEnvVariable() {
        MergeConfig config = ConfigFactory.create(MergeConfig.class);
        assertNotNull(config.path());
    }

    @After
    public void cleanSystemProperties() {
        systemPropertiesHelper.cleanNonDefaultSysProps();
    }

    private class SystemPropertiesHelper {

        private List<String> sysProps = new ArrayList<String>();

        public void setProperty(String key, String val) {
            System.setProperty(key, val);
            sysProps.add(key);
        }

        public void cleanNonDefaultSysProps() {
            for (String prop : sysProps) {
                System.clearProperty(prop);
            }
            sysProps.clear();
        }
    }
}
