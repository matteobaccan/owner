package org.aeonbits.owner.loaders;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.util.SystemProviderForTest;
import org.aeonbits.owner.util.UtilTest;
import org.junit.Test;

import java.util.HashMap;
import java.util.Properties;

import static org.aeonbits.owner.Config.LoadType.FIRST;
import static org.aeonbits.owner.Config.LoadType.MERGE;
import static org.junit.Assert.*;

public class SystemLoaderTest {

    @Config.Sources({"system:properties",
            "system:env"})
    @Config.LoadPolicy(FIRST)
    public interface FirstConfig extends Config {
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

        Object save = UtilTest.setSystem(new SystemProviderForTest(
                new Properties() {{
                    setProperty("foo", "FOO");
                    setProperty("bar", "BAR");
                }},
                new HashMap<String, String>()
        ));

        try {
            FirstConfig config = ConfigFactory.create(FirstConfig.class);

            assertEquals("FOO", config.foo());
            assertEquals("BAR", config.bar());

        } finally {
            UtilTest.setSystem(save);
        }
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
    public interface MergeConfig extends Config {
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
        Object save = UtilTest.setSystem(new SystemProviderForTest(
                new Properties() {{
                    setProperty("foo", "FOO");
                    setProperty("bar", "BAR");
                }},
                new HashMap<String, String>()
        ));

        try {
            MergeConfig config = ConfigFactory.create(MergeConfig.class);

            assertEquals("FOO", config.foo());
            assertEquals("BAR", config.bar());
        } finally {
            UtilTest.setSystem(save);
        }
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

}
