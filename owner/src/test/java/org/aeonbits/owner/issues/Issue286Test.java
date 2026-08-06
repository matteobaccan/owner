/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.issues;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Converter;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

/**
 * See: https://github.com/lviggiano/owner/issues/286
 * <p>
 * The question here is one property whose <em>value</em> holds the name/value pairs, which is a different shape
 * from the group of properties sharing a prefix asked for in
 * <a href="https://github.com/matteobaccan/owner/issues/41">#41</a> and read by
 * {@link org.aeonbits.owner.PropertiesAggregatorTest}. A {@code @ConverterClass} answers this one: it receives
 * the whole value and returns whatever map it likes, and its presence is what tells the two shapes apart.
 */
public class Issue286Test {

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

    interface SettingsConfig extends Config {
        @ConverterClass(PairsConverter.class)
        @DefaultValue("host=localhost, port=8080, mode=debug")
        Map<String, String> settings();
    }

    @Test
    public void aMapIsReadThroughAConverterClass() {
        Map<String, String> settings = ConfigFactory.create(SettingsConfig.class).settings();

        assertEquals(3, settings.size());
        assertEquals("localhost", settings.get("host"));
        assertEquals("8080", settings.get("port"));
        assertEquals("debug", settings.get("mode"));
    }

    @Test
    public void theConverterKeepsWhateverMapItReturns() {
        // insertion order preserved: the converter chose LinkedHashMap, and OWNER does not touch it
        assertEquals("[host, port, mode]",
                ConfigFactory.create(SettingsConfig.class).settings().keySet().toString());
    }

    public static class PortsConverter implements Converter<Map<String, Integer>> {
        @Override
        public Map<String, Integer> convert(Method method, String input) {
            Map<String, Integer> result = new LinkedHashMap<>();
            for (String pair : input.split(",", -1)) {
                String[] entry = pair.split("=", 2);
                result.put(entry[0].trim(), Integer.valueOf(entry[1].trim()));
            }
            return result;
        }
    }

    interface PortsConfig extends Config {
        @ConverterClass(PortsConverter.class)
        @DefaultValue("http=80, https=443")
        Map<String, Integer> ports();
    }

    /** The values are not limited to Strings: the converter decides both sides of the entry. */
    @Test
    public void theValuesCanBeOfAnyType() {
        Map<String, Integer> ports = ConfigFactory.create(PortsConfig.class).ports();

        assertEquals(Integer.valueOf(80), ports.get("http"));
        assertEquals(Integer.valueOf(443), ports.get("https"));
    }

    interface AuthorsConfig extends Config {
        @Separator(";")
        @ConverterClass(PairsConverter.class)
        @DefaultValue("name=Dante Alighieri, book=Divine Comedy; name=Alessandro Manzoni, book=The Betrothed")
        Map<String, String>[] authors();
    }

    /**
     * An array of Maps works too: the value is split by the separator first, and the converter is handed one
     * chunk at a time — the shape of the {@code MapPropertyExample} shipped in the sources, which is a
     * {@code main()} and therefore never exercised by the build.
     */
    @Test
    public void anArrayOfMaps() {
        Map<String, String>[] authors = ConfigFactory.create(AuthorsConfig.class).authors();

        assertEquals(2, authors.length);
        assertEquals("Dante Alighieri", authors[0].get("name"));
        assertEquals("Divine Comedy", authors[0].get("book"));
        assertEquals("Alessandro Manzoni", authors[1].get("name"));
        assertEquals("The Betrothed", authors[1].get("book"));
    }

    interface NoConverterConfig extends Config {
        Map<String, String> settings();
    }

    /**
     * Without a converter the method reads the group of properties under its key, which is the other shape of
     * the same question and is covered by {@link org.aeonbits.owner.PropertiesAggregatorTest}. The converter is
     * therefore what distinguishes "this one property holds the pairs" from "the pairs are properties of their
     * own".
     */
    @Test
    public void withoutAConverterTheGroupBelowTheKeyIsRead() {
        Map<String, String> settings = ConfigFactory.create(NoConverterConfig.class, new Properties() {{
            setProperty("settings.host", "localhost");
            setProperty("settings.port", "8080");
        }}).settings();

        assertEquals(2, settings.size());
        assertEquals("localhost", settings.get("host"));
    }
}
