/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.Config.DefaultValue;
import org.aeonbits.owner.Config.Sources;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Properties;

import static org.aeonbits.owner.TestConstants.RESOURCES_DIR;
import static org.aeonbits.owner.util.UtilTest.fileFromURI;
import static org.aeonbits.owner.util.UtilTest.save;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Once the properties are loaded, a value that came from a source and one that came from a
 * {@link DefaultValue} are the same property and nothing distinguishes them. This is the one thing kept
 * from the moment at which they could still be told apart: see
 * {@link PropertiesManager#anythingWrittenUnder}.
 * <p>
 * Nothing in the resolution of a property consults it. It is the mechanism, proved to work, waiting for the
 * questions that need it — an absent section, a configuration that explains itself, and the origin of a
 * value.
 * </p>
 *
 * @author Matteo Baccan
 */
public class WrittenOrDefaultedTest {

    public interface ServerConfig extends Config {
        String host();

        @DefaultValue("8080")
        int port();
    }

    public interface AppConfig extends Config {
        ServerConfig server();
    }

    public interface MutableAppConfig extends Config, Mutable {
        ServerConfig server();
    }

    private static PropertiesManager managerOf(Config cfg) {
        return ((PropertiesInvocationHandler) Proxy.getInvocationHandler(cfg)).propertiesManager;
    }

    private static <T extends Config> T create(Class<T> type, String... pairs) {
        Properties props = new Properties();
        for (int i = 0; i + 1 < pairs.length; i += 2)
            props.setProperty(pairs[i], pairs[i + 1]);
        return ConfigFactory.create(type, props);
    }

    @Test
    public void whatWasWrittenIsStillTellableFromWhatWasDefaulted() {
        AppConfig defaulted = create(AppConfig.class);
        assertEquals("the default is among the properties like any other value", 8080,
                defaulted.server().port());
        assertFalse(managerOf(defaulted).anythingWrittenUnder("server."));

        AppConfig written = create(AppConfig.class, "server.host", "localhost");
        assertTrue(managerOf(written).anythingWrittenUnder("server."));
    }

    /**
     * The case an inverted set gets wrong if it is built carelessly: a source writing exactly the value the
     * annotation already gave. The key is among the defaults <b>and</b> among the values read, and it is the
     * source that decides — otherwise "was this written?" would answer no for a line somebody typed.
     */
    @Test
    public void aSourceWritingExactlyTheDefaultStillCountsAsWritten() {
        AppConfig cfg = create(AppConfig.class, "server.port", "8080");
        assertEquals(8080, cfg.server().port());
        assertTrue(managerOf(cfg).anythingWrittenUnder("server."));
    }

    private static final String SPEC = "file:" + RESOURCES_DIR + "/WrittenOrDefaultedConfig.properties";

    @Sources(SPEC)
    public interface FileBackedConfig extends Config {
        ServerConfig server();
    }

    /**
     * The same edge case arriving by the other road. A value reaches the properties from a source or from an
     * import, the two are merged by different lines, and each line has to be answered for: without this test
     * the one about the sources can be deleted and nothing anywhere goes red.
     */
    @Test
    public void aFileWritingExactlyTheDefaultStillCountsAsWritten() throws Throwable {
        save(fileFromURI(SPEC), new Properties() {{
            setProperty("server.port", "8080");
        }});

        FileBackedConfig cfg = ConfigFactory.create(FileBackedConfig.class);
        assertEquals(8080, cfg.server().port());
        assertTrue(managerOf(cfg).anythingWrittenUnder("server."));
    }

    @Test
    public void onlyWhatLiesBelowThePrefixIsLookedAt() {
        AppConfig cfg = create(AppConfig.class, "elsewhere.host", "written over there");

        assertFalse("the section has its default and nothing else",
                managerOf(cfg).anythingWrittenUnder("server."));
        assertTrue(managerOf(cfg).anythingWrittenUnder("elsewhere."));
    }

    @Test
    public void aValueWrittenAtRunTimeCountsAsWritten() {
        MutableAppConfig cfg = create(MutableAppConfig.class);
        assertFalse(managerOf(cfg).anythingWrittenUnder("server."));

        cfg.setProperty("server.host", "localhost");
        assertTrue(managerOf(cfg).anythingWrittenUnder("server."));

        cfg.removeProperty("server.host");
        assertFalse(managerOf(cfg).anythingWrittenUnder("server."));
    }
}
