/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.Config.DefaultValue;
import org.aeonbits.owner.Config.Key;
import org.aeonbits.owner.Config.Prefix;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;

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

        assertTrue(ConfigFactory.create(FlatConfig.class, values).group() instanceof LinkedHashMap);
        assertTrue(ConfigFactory.create(SortedConfig.class, values).group() instanceof java.util.TreeMap);
        assertTrue(ConfigFactory.create(ConcreteConfig.class, values).group() instanceof HashMap);

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
