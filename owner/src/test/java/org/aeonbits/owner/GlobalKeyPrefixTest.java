/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.Config.DisableFeature;
import org.aeonbits.owner.Config.Key;
import org.aeonbits.owner.Config.Mandatory;
import org.aeonbits.owner.Config.Prefix;
import org.aeonbits.owner.Config.Sources;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.aeonbits.owner.Config.DisableableFeature.PREFIX;
import static org.aeonbits.owner.TestConstants.RESOURCES_DIR;
import static org.aeonbits.owner.util.UtilTest.fileFromURI;
import static org.aeonbits.owner.util.UtilTest.save;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

/**
 * Tests for the prefix configured on the {@link Factory} rather than written in the source code: see
 * {@link KeyPrefix}.
 * <p>
 * The interfaces below live in <code>org.aeonbits.owner</code>, so that is the prefix the package derived form
 * produces.
 *
 * @author Matteo Baccan
 */
public class GlobalKeyPrefixTest {

    private static final String PACKAGE = "org.aeonbits.owner.";

    public interface ServerConfig extends Config, Accessible {
        @DefaultValue("8080")
        int port();

        @Key("host.name")
        @DefaultValue("localhost")
        String host();
    }

    @Prefix("server.")
    public interface AnnotatedConfig extends Config {
        @DefaultValue("8080")
        int port();
    }

    public interface DisabledPerMethodConfig extends Config, Accessible {
        @DisableFeature(PREFIX)
        @DefaultValue("8080")
        int port();
    }

    @DisableFeature(PREFIX)
    public interface DisabledPerInterfaceConfig extends Config, Accessible {
        @DefaultValue("8080")
        int port();
    }

    private static Factory factoryWith(String... keysAndValues) {
        if (keysAndValues.length % 2 != 0)
            throw new IllegalArgumentException("keys and values come in pairs");
        Factory factory = ConfigFactory.newInstance();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2)
            factory.setProperty(keysAndValues[i], keysAndValues[i + 1]);
        return factory;
    }

    private static Map<String, String> given(String key, String value) {
        Map<String, String> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    @Test
    public void theKeyIsDerivedFromThePackage() {
        Factory factory = factoryWith(KeyPrefix.FROM_PACKAGE, "true");

        ServerConfig cfg = factory.create(ServerConfig.class, given(PACKAGE + "port", "9090"));

        assertEquals(9090, cfg.port());
    }

    /**
     * The trap of the whole change: the defaults are registered by the same {@code key()} the lookup uses, so
     * they must land under the prefixed key. Were the prefix applied on the reading side only, every
     * {@code @DefaultValue} would silently stop being found.
     */
    @Test
    public void theDefaultIsRegisteredUnderThePrefixedKey() {
        Factory factory = factoryWith(KeyPrefix.FROM_PACKAGE, "true");

        ServerConfig cfg = factory.create(ServerConfig.class);

        assertEquals(8080, cfg.port());
        assertEquals("8080", cfg.getProperty(PACKAGE + "port"));
        assertNull(cfg.getProperty("port"));
    }

    @Test
    public void theKeyAnnotationIsPrefixedToo() {
        Factory factory = factoryWith(KeyPrefix.FROM_PACKAGE, "true");

        ServerConfig cfg = factory.create(ServerConfig.class, given(PACKAGE + "host.name", "example.com"));

        assertEquals("example.com", cfg.host());
    }

    @Test
    public void aLiteralPrefixIsPrependedToEveryKey() {
        Factory factory = factoryWith(KeyPrefix.LITERAL, "myapp.");

        ServerConfig cfg = factory.create(ServerConfig.class, given("myapp.port", "9090"));

        assertEquals(9090, cfg.port());
        assertEquals("9090", cfg.getProperty("myapp.port"));
    }

    /** The two forms compose, the literal first: it is a root under which the packages are laid out. */
    @Test
    public void theLiteralAndThePackageCompose() {
        Factory factory = factoryWith(KeyPrefix.LITERAL, "myapp.", KeyPrefix.FROM_PACKAGE, "true");

        ServerConfig cfg = factory.create(ServerConfig.class, given("myapp." + PACKAGE + "port", "9090"));

        assertEquals(9090, cfg.port());
    }

    /** The prefix is part of the key, so it goes through variable expansion as the rest of the key does. */
    @Test
    public void theLiteralPrefixIsExpandedLikeTheRestOfTheKey() {
        Factory factory = factoryWith(KeyPrefix.LITERAL, "servers.${env}.");

        Map<String, String> values = given("servers.dev.port", "9090");
        values.put("env", "dev");

        assertEquals(9090, factory.create(ServerConfig.class, values).port());
    }

    /** The annotation is the explicit statement of the two, so it wins rather than being appended to. */
    @Test
    public void thePrefixAnnotationWinsOverTheFactory() {
        Factory factory = factoryWith(KeyPrefix.FROM_PACKAGE, "true", KeyPrefix.LITERAL, "myapp.");

        AnnotatedConfig cfg = factory.create(AnnotatedConfig.class, given("server.port", "9090"));

        assertEquals(9090, cfg.port());
    }

    @Test
    public void disableFeatureSwitchesOffTheFactoryPrefixOnAMethod() {
        Factory factory = factoryWith(KeyPrefix.FROM_PACKAGE, "true");

        DisabledPerMethodConfig cfg = factory.create(DisabledPerMethodConfig.class, given("port", "9090"));

        assertEquals(9090, cfg.port());
        assertNull(cfg.getProperty(PACKAGE + "port"));
    }

    @Test
    public void disableFeatureSwitchesOffTheFactoryPrefixOnAnInterface() {
        Factory factory = factoryWith(KeyPrefix.LITERAL, "myapp.");

        DisabledPerInterfaceConfig cfg = factory.create(DisabledPerInterfaceConfig.class, given("port", "9090"));

        assertEquals(9090, cfg.port());
        assertNull(cfg.getProperty("myapp.port"));
    }

    /**
     * The reason for putting this on the factory instead of on a system property: a library can build its own
     * factory and be unaffected by what the application does, and the other way round.
     */
    @Test
    public void twoFactoriesDoNotInterfere() {
        Factory prefixed = factoryWith(KeyPrefix.FROM_PACKAGE, "true");
        Factory plain = ConfigFactory.newInstance();

        Map<String, String> both = given(PACKAGE + "port", "9090");
        both.put("port", "7070");

        assertEquals(9090, prefixed.create(ServerConfig.class, both).port());
        assertEquals(7070, plain.create(ServerConfig.class, both).port());
    }

    @Test
    public void theStaticFactoryIsUnaffectedByAnotherOne() {
        factoryWith(KeyPrefix.FROM_PACKAGE, "true");

        ServerConfig cfg = ConfigFactory.create(ServerConfig.class, given("port", "7070"));

        assertEquals(7070, cfg.port());
    }

    /**
     * The prefix is captured when the object is created: reconfiguring the factory afterwards cannot rename
     * the keys of what already exists, while what is created next follows the new setting.
     */
    @Test
    public void reconfiguringTheFactoryDoesNotMoveTheKeysOfWhatExists() {
        Factory factory = ConfigFactory.newInstance();
        Map<String, String> both = given(PACKAGE + "port", "9090");
        both.put("port", "7070");

        ServerConfig before = factory.create(ServerConfig.class, both);
        factory.setProperty(KeyPrefix.FROM_PACKAGE, "true");
        ServerConfig after = factory.create(ServerConfig.class, both);

        assertEquals(7070, before.port());
        assertEquals(9090, after.port());
    }

    /** Being kept in the object rather than looked up globally, the mapping travels with it. */
    @Test
    public void theMappingSurvivesSerialization() throws Exception {
        Factory factory = factoryWith(KeyPrefix.FROM_PACKAGE, "true");
        ServerConfig cfg = factory.create(ServerConfig.class, given(PACKAGE + "port", "9090"));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(cfg);
        }
        ServerConfig deserialized;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            deserialized = (ServerConfig) in.readObject();
        }

        assertEquals(9090, deserialized.port());
    }

    /** The conversion error names the key the value was actually read with, prefix included. */
    @Test
    public void theConversionErrorNamesThePrefixedKey() {
        Factory factory = factoryWith(KeyPrefix.FROM_PACKAGE, "true");
        ServerConfig cfg = factory.create(ServerConfig.class, given(PACKAGE + "port", "abc"));

        try {
            cfg.port();
            fail("an UnsupportedOperationException was expected");
        } catch (UnsupportedOperationException e) {
            assertEquals("Cannot convert 'abc' to int for property '" + PACKAGE + "port'", e.getMessage());
        }
    }

    // -- the rest of the library, seen through a prefixed factory ---------------------------------------

    private static final String SPEC = "file:" + RESOURCES_DIR + "/GlobalKeyPrefixConfig.properties";

    @Sources(SPEC)
    public interface FileBackedConfig extends Config, Accessible, Reloadable {
        Integer minimumAge();

        @DefaultValue("8080")
        int port();
    }

    /**
     * A real properties file, and a reload on top of it: the keys must be the same ones after the reload as
     * before it, defaults included - the defaults are registered again on every load, so this is where they
     * would silently move back to their bare key.
     */
    @Test
    public void aReloadResolvesTheSameKeys() throws Throwable {
        File target = fileFromURI(SPEC);
        save(target, new Properties() {{
            setProperty(PACKAGE + "minimumAge", "18");
        }});

        Factory factory = factoryWith(KeyPrefix.FROM_PACKAGE, "true");
        FileBackedConfig cfg = factory.create(FileBackedConfig.class);

        assertEquals(Integer.valueOf(18), cfg.minimumAge());
        assertEquals(8080, cfg.port());
        assertEquals("8080", cfg.getProperty(PACKAGE + "port"));

        save(target, new Properties() {{
            setProperty(PACKAGE + "minimumAge", "21");
        }});
        cfg.reload();

        assertEquals(Integer.valueOf(21), cfg.minimumAge());
        assertEquals(8080, cfg.port());
        assertEquals("8080", cfg.getProperty(PACKAGE + "port"));
        assertNull(cfg.getProperty("port"));
    }

    /** A key that is not prefixed is not read, which is the other half of the same statement. */
    @Test
    public void aBareKeyInTheFileIsNotRead() throws Throwable {
        File target = fileFromURI(SPEC);
        save(target, new Properties() {{
            setProperty("minimumAge", "18");
        }});

        Factory factory = factoryWith(KeyPrefix.FROM_PACKAGE, "true");

        assertNull(factory.create(FileBackedConfig.class).minimumAge());
    }

    public interface MandatoryConfig extends Config {
        @Mandatory
        String required();
    }

    /** The key that could not be resolved is reported the way it was looked up. */
    @Test
    public void aMissingMandatoryPropertyIsReportedByItsPrefixedKey() {
        Factory factory = factoryWith(KeyPrefix.FROM_PACKAGE, "true");

        try {
            factory.create(MandatoryConfig.class);
            fail("a MissingMandatoryPropertyException was expected");
        } catch (MissingMandatoryPropertyException e) {
            assertEquals("Missing mandatory property: '" + PACKAGE + "required'", e.getMessage());
        }
    }

    public interface GroupConfig extends Config {
        Map<String, Integer> group();
    }

    /** A group of properties read as a Map is looked up below the prefixed key too. */
    @Test
    public void aMapReadsTheGroupBelowThePrefixedKey() {
        Factory factory = factoryWith(KeyPrefix.FROM_PACKAGE, "true");

        Map<String, String> values = given(PACKAGE + "group.first", "1");
        values.put(PACKAGE + "group.second", "2");
        values.put("group.third", "3");

        Map<String, Integer> group = factory.create(GroupConfig.class, values).group();

        assertEquals(2, group.size());
        assertEquals(Integer.valueOf(1), group.get("first"));
        assertEquals(Integer.valueOf(2), group.get("second"));
    }

    public interface ParametrizedConfig extends Config {
        @Key("servers.%s.host")
        String host(String name);
    }

    /** A key completed at call time keeps the prefix in front of it. */
    @Test
    public void aParametrizedKeyIsPrefixedAsWell() {
        Factory factory = factoryWith(KeyPrefix.FROM_PACKAGE, "true");

        ParametrizedConfig cfg =
                factory.create(ParametrizedConfig.class, given(PACKAGE + "servers.web.host", "example.com"));

        assertEquals("example.com", cfg.host("web"));
    }

    /**
     * An instance taken from the cache is the one created the first time, so it keeps the mapping it was born
     * with even when the second caller hands over a factory that declares no prefix.
     */
    @Test
    public void anInstanceFromTheCacheKeepsTheMappingItWasCreatedWith() {
        Factory prefixed = factoryWith(KeyPrefix.FROM_PACKAGE, "true");
        Map<String, String> both = given(PACKAGE + "port", "9090");
        both.put("port", "7070");

        try {
            ServerConfig first = ConfigCache.getOrCreate(prefixed, ServerConfig.class, both);
            assertEquals(9090, first.port());

            ServerConfig cached = ConfigCache.getOrCreate(ConfigFactory.newInstance(), ServerConfig.class, both);

            assertSame(first, cached);
            assertEquals(9090, cached.port());
        } finally {
            ConfigCache.clear();
        }
    }

    /**
     * A class in the default package has no package name to build a prefix out of, so it is left alone rather
     * than being prefixed with a dot.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void aClassInTheDefaultPackageHasNothingToDeriveFrom() throws Exception {
        Factory factory = factoryWith(KeyPrefix.FROM_PACKAGE, "true");
        Class<? extends Config> clazz = Class.forName("DefaultPackageConfig").asSubclass(Config.class);

        Config cfg = factory.create(clazz, given("port", "9090"));

        assertEquals(Integer.valueOf(9090), clazz.getMethod("port").invoke(cfg));
    }
}
