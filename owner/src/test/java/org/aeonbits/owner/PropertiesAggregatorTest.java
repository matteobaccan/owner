/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.Config.DecryptorClass;
import org.aeonbits.owner.Config.DefaultValue;
import org.aeonbits.owner.Config.EncryptedValue;
import org.aeonbits.owner.Config.Key;
import org.aeonbits.owner.Config.Prefix;
import org.aeonbits.owner.Config.PreprocessorClasses;
import org.aeonbits.owner.crypto.Decryptor;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for reading a group of properties sharing a prefix as a {@link Map}, the shape asked for in
 * <a href="https://github.com/matteobaccan/owner/issues/41">#41</a>.
 *
 * @author Matteo Baccan
 */
public class PropertiesAggregatorTest {

    private static Properties something() {
        return new Properties() {{
            setProperty("something.foo", "1");
            setProperty("something.bar", "2");
            setProperty("something.baz", "3");
            setProperty("something", "not an entry of the group");
            setProperty("somethingelse.foo", "4");
            setProperty("unrelated", "9");
        }};
    }

    interface SomethingConfig extends Config {
        Map<String, Integer> something();
    }

    @Test
    public void thePropertiesBelowTheKeyAreCollected() {
        Map<String, Integer> map = ConfigFactory.create(SomethingConfig.class, something()).something();

        assertEquals(3, map.size());
        assertEquals(Integer.valueOf(1), map.get("foo"));
        assertEquals(Integer.valueOf(2), map.get("bar"));
        assertEquals(Integer.valueOf(3), map.get("baz"));
    }

    @Test
    public void whatIsNotBelowTheKeyStaysOut() {
        Map<String, Integer> map = ConfigFactory.create(SomethingConfig.class, something()).something();

        assertEquals(false, map.containsKey("unrelated"));
        assertEquals(false, map.containsKey(""));               // the bare "something" property
        assertEquals(false, map.containsKey("elsefoo"));        // "somethingelse.foo" only shares a text prefix
    }

    @Test
    public void nothingMatchingGivesAnEmptyMap() {
        assertTrue(ConfigFactory.create(SomethingConfig.class).something().isEmpty());
    }

    // -- both sides go through the type conversion ---------------------------------------------------

    interface StatusConfig extends Config {
        @Key("server.reasons")
        Map<Integer, String> reasons();
    }

    @Test
    public void theKeysAreConvertedToo() {
        Map<Integer, String> reasons = ConfigFactory.create(StatusConfig.class, new Properties() {{
            setProperty("server.reasons.200", "OK");
            setProperty("server.reasons.201", "Created");
        }}).reasons();

        assertEquals("OK", reasons.get(200));
        assertEquals("Created", reasons.get(201));
    }

    enum Colour { GREEN, RED }

    interface QueueConfig extends Config {
        Map<Colour, String> queue();
    }

    @Test
    public void anythingOwnerCanConvertWorksAsAKey() {
        Map<Colour, String> queues = ConfigFactory.create(QueueConfig.class, new Properties() {{
            setProperty("queue.GREEN", "jms/QueueA");
            setProperty("queue.RED", "jms/QueueB");
        }}).queue();

        assertEquals("jms/QueueA", queues.get(Colour.GREEN));
        assertEquals("jms/QueueB", queues.get(Colour.RED));
    }

    // -- the key is named the usual way --------------------------------------------------------------

    @Prefix("servers.")
    interface PrefixedConfig extends Config {
        @Key("known.hosts")
        Map<String, String> hosts();
    }

    @Test
    public void thePrefixAndTheKeyAnnotationNameTheGroup() {
        Map<String, String> hosts = ConfigFactory.create(PrefixedConfig.class, new Properties() {{
            setProperty("servers.known.hosts.alpha", "10.0.0.1");
            setProperty("servers.known.hosts.beta", "10.0.0.2");
            setProperty("known.hosts.gamma", "10.0.0.3");
        }}).hosts();

        assertEquals(2, hosts.size());
        assertEquals("10.0.0.1", hosts.get("alpha"));
    }

    interface ExpandedConfig extends Config {
        @Key("servers.${env}")
        Map<String, String> servers();
    }

    @Test
    public void theGroupCanBeSelectedWithAVariable() {
        Map<String, String> servers = ConfigFactory.create(ExpandedConfig.class, new Properties() {{
            setProperty("env", "dev");
            setProperty("servers.dev.host", "devhost");
            setProperty("servers.uat.host", "uathost");
        }}).servers();

        assertEquals(1, servers.size());
        assertEquals("devhost", servers.get("host"));
    }

    // -- nested keys stay flat -----------------------------------------------------------------------

    @Test
    public void aNestedKeyBecomesADottedEntryKey() {
        Map<String, String> map = ConfigFactory.create(FlatConfig.class, new Properties() {{
            setProperty("group.a", "1");
            setProperty("group.a.b", "2");
            setProperty("group.a.b.c", "3");
        }}).group();

        assertEquals(3, map.size());
        assertEquals("1", map.get("a"));
        assertEquals("2", map.get("a.b"));
        assertEquals("3", map.get("a.b.c"));
    }

    interface FlatConfig extends Config {
        Map<String, String> group();
    }

    // -- the map that comes back ---------------------------------------------------------------------

    interface SortedConfig extends Config {
        SortedMap<String, String> group();
    }

    interface ConcreteConfig extends Config {
        HashMap<String, String> group();
    }

    @Test
    public void theDeclaredMapTypeIsHonoured() {
        Properties values = new Properties() {{
            setProperty("group.b", "2");
            setProperty("group.a", "1");
        }};

        // the exact class, not instanceof: LinkedHashMap is a HashMap, so an instanceof test would pass
        // even if the declared concrete type were quietly replaced by the default one
        assertEquals(LinkedHashMap.class, ConfigFactory.create(FlatConfig.class, values).group().getClass());
        assertEquals(TreeMap.class, ConfigFactory.create(SortedConfig.class, values).group().getClass());
        assertEquals(HashMap.class, ConfigFactory.create(ConcreteConfig.class, values).group().getClass());

        assertEquals("[a, b]", ConfigFactory.create(SortedConfig.class, values).group().keySet().toString());
    }

    interface RawConfig extends Config {
        @SuppressWarnings("rawtypes")
        Map group();
    }

    @Test
    public void aRawMapIsReadAsStringToString() {
        assertEquals("1", ConfigFactory.create(RawConfig.class, new Properties() {{
            setProperty("group.a", "1");
        }}).group().get("a"));
    }

    // -- values follow the usual pipeline -------------------------------------------------------------

    interface ExpandingValuesConfig extends Config {
        Map<String, String> group();
    }

    @Test
    public void theValuesAreExpandedLikeAnyOtherValue() {
        Map<String, String> map = ConfigFactory.create(ExpandingValuesConfig.class, new Properties() {{
            setProperty("host", "devhost");
            setProperty("group.url", "http://${host}/api");
        }}).group();

        assertEquals("http://devhost/api", map.get("url"));
    }

    public static class Unwrap implements Preprocessor {
        @Override
        public String process(String input) {
            return input.replace("[", "").replace("]", "");
        }
    }

    @PreprocessorClasses(Unwrap.class)
    interface PreprocessedConfig extends Config {
        Map<String, String> group();
    }

    /** Every value of the group goes through the preprocessors of the method, one entry at a time. */
    @Test
    public void theValuesArePreprocessed() {
        Map<String, String> map = ConfigFactory.create(PreprocessedConfig.class, new Properties() {{
            setProperty("group.a", "[one]");
            setProperty("group.b", "[two]");
        }}).group();

        assertEquals("one", map.get("a"));
        assertEquals("two", map.get("b"));
    }

    public static class Unscramble implements Decryptor {
        @Override
        public String decrypt(String value) {
            return new StringBuilder(value).reverse().toString();
        }

        @Override
        public String decrypt(String value, String defaultValue) {
            return value == null ? defaultValue : decrypt(value);
        }
    }

    @DecryptorClass(Unscramble.class)
    interface EncryptedGroupConfig extends Config {
        @EncryptedValue
        Map<String, String> group();
    }

    /** {@code @EncryptedValue} applies to the group as well, decrypting each value on its own. */
    @Test
    public void theValuesAreDecrypted() {
        Map<String, String> map = ConfigFactory.create(EncryptedGroupConfig.class, new Properties() {{
            setProperty("group.a", "tsohlacol");
            setProperty("group.b", "0808");
        }}).group();

        assertEquals("localhost", map.get("a"));
        assertEquals("8080", map.get("b"));
    }

    // -- the edges of the declared type ----------------------------------------------------------------

    interface WildcardConfig extends Config {
        Map<String, ? extends Number> group();
    }

    /**
     * A type argument that is not a plain class - a wildcard here, a type variable elsewhere - cannot name a
     * conversion target, so it falls back to String, exactly as a raw Map does.
     */
    @Test
    public void aWildcardTypeArgumentFallsBackToString() {
        Map<String, ?> map = ConfigFactory.create(WildcardConfig.class, new Properties() {{
            setProperty("group.a", "1");
        }}).group();

        // the declared ? extends Number is not what ends up in the map: erasure lets the String through,
        // which is the point of the fallback and the reason to assert the runtime class as well
        Object value = map.get("a");
        assertEquals(String.class, value.getClass());
        assertEquals("1", value);
    }

    interface EnumKeyedConfig extends Config {
        EnumMap<Colour, String> group();
    }

    /**
     * This used to assert the opposite - that an EnumMap could not be instantiated - and it was right at the
     * time: an EnumMap is the one map in the JDK with no no-argument constructor, since it has to be told
     * the class of its keys. That class was always in hand, though, having been read off the return type in
     * order to convert the keys, so the limitation was a few lines rather than a fact about the world.
     */
    @Test
    public void anEnumMapIsBuiltFromTheKeyTypeItDeclares() {
        EnumMap<Colour, String> group = ConfigFactory.create(EnumKeyedConfig.class, new Properties() {{
            setProperty("group.GREEN", "jms/QueueA");
        }}).group();

        assertEquals(1, group.size());
        assertEquals("jms/QueueA", group.get(Colour.GREEN));
    }

    interface NoUsableConstructorConfig extends Config {
        SizedMap<String, String> group();
    }

    /** A concrete map type with no no-argument constructor is reported, rather than failing obscurely. */
    @Test
    public void aMapTypeThatCannotBeInstantiatedIsReported() {
        try {
            ConfigFactory.create(NoUsableConstructorConfig.class, new Properties() {{
                setProperty("group.a", "1");
            }}).group();
            fail("UnsupportedOperationException is expected");
        } catch (UnsupportedOperationException e) {
            assertTrue("unexpected message: " + e.getMessage(),
                    e.getMessage().contains("Cannot instantiate map of type"));
            assertTrue("unexpected message: " + e.getMessage(), e.getMessage().contains("SizedMap"));
        }
    }

    public static class SizedMap<K, V> extends java.util.HashMap<K, V> {
        private static final long serialVersionUID = 1L;

        public SizedMap(int size) {
            super(size);
        }
    }

    interface NumericValuesConfig extends Config {
        Map<String, Integer> group();
    }

    /**
     * The entry that fails to convert names itself, not the group: with a group of fifty properties, being
     * told that 'group' failed leaves the offending line to be found by hand.
     */
    @Test
    public void anEntryThatCannotBeConvertedNamesItself() {
        try {
            ConfigFactory.create(NumericValuesConfig.class, new Properties() {{
                setProperty("group.first", "1");
                setProperty("group.second", "8O80");
            }}).group();
            fail("UnsupportedOperationException is expected");
        } catch (UnsupportedOperationException e) {
            assertEquals("Cannot convert '8O80' to java.lang.Integer for property 'group.second'", e.getMessage());
        }
    }

    // -- what is refused ------------------------------------------------------------------------------

    interface WithDefaultValueConfig extends Config {
        @DefaultValue("a=1")
        Map<String, String> group();
    }

    @Test
    public void aDefaultValueIsRefused() {
        try {
            ConfigFactory.create(WithDefaultValueConfig.class).group();
            fail("UnsupportedOperationException is expected");
        } catch (UnsupportedOperationException e) {
            assertTrue("unexpected message: " + e.getMessage(),
                    e.getMessage().contains("@DefaultValue cannot be used on 'group'"));
        }
    }

    // -- the same map, written in two ways -------------------------------------------------------------

    /** The pairs are properties of their own: nothing to declare, the group below the key is read. */
    interface AsSeparatePropertiesConfig extends Config {
        Map<String, String> server();
    }

    /** The pairs live inside one property value: the converter says how that value is written. */
    interface AsSingleValueConfig extends Config {
        @ConverterClass(PairsConverter.class)
        Map<String, String> server();
    }

    /**
     * The two readings are two ways of writing the same configuration, and they produce the same map. What
     * decides which one applies is the shape of the properties file, and the converter that goes with it.
     */
    @Test
    public void theSameMapCanBeWrittenInTwoWays() {
        Map<String, String> asProperties = ConfigFactory.create(AsSeparatePropertiesConfig.class,
                new Properties() {{
                    setProperty("server.host", "localhost");
                    setProperty("server.port", "8080");
                }}).server();

        Map<String, String> asOneValue = ConfigFactory.create(AsSingleValueConfig.class,
                new Properties() {{
                    setProperty("server", "host=localhost, port=8080");
                }}).server();

        assertEquals(asProperties, asOneValue);
        assertEquals("localhost", asProperties.get("host"));
        assertEquals("8080", asProperties.get("port"));
    }

    // -- an explicit converter still wins --------------------------------------------------------------

    public static class PairsConverter implements Converter<Map<String, String>> {
        @Override
        public Map<String, String> convert(Method method, String input) {
            Map<String, String> result = new LinkedHashMap<>();
            for (String pair : input.split(",", -1)) {
                String[] entry = pair.split("=", 2);
                result.put(entry[0].trim(), entry[1].trim());
            }
            return result;
        }
    }

    interface ConverterConfig extends Config {
        @ConverterClass(PairsConverter.class)
        @DefaultValue("host=localhost, port=8080")
        Map<String, String> group();
    }

    @Test
    public void aConverterClassTakesPrecedenceOverTheAggregation() {
        Map<String, String> map = ConfigFactory.create(ConverterConfig.class, new Properties() {{
            setProperty("group.ignored", "should not be collected");
        }}).group();

        assertEquals(2, map.size());
        assertEquals("localhost", map.get("host"));
        assertEquals("8080", map.get("port"));
    }
}
