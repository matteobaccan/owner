/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.Config.ConverterClass;
import org.aeonbits.owner.Config.DefaultValue;
import org.aeonbits.owner.Config.Key;
import org.aeonbits.owner.Config.Mandatory;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for {@link Optional} return types: a method declared as <code>Optional&lt;T&gt;</code> converts its
 * value to <code>T</code> exactly like a method returning <code>T</code> does, and comes back empty instead
 * of <code>null</code> when the property is not there.
 *
 * @author Matteo Baccan
 */
public class OptionalTest {

    interface MyConfig extends Config {
        Optional<String> absent();

        Optional<String> present();

        Optional<Integer> port();

        @Key("some.other.name")
        Optional<String> renamed();

        @DefaultValue("8080")
        Optional<Integer> withDefault();
    }

    private static MyConfig config() {
        return ConfigFactory.create(MyConfig.class, new Properties() {{
            setProperty("present", "hello");
            setProperty("port", "80");
            setProperty("some.other.name", "renamed value");
        }});
    }

    @Test
    public void aMissingPropertyIsEmptyInsteadOfNull() {
        Optional<String> value = config().absent();
        assertSame(Optional.empty(), value);
        assertFalse(value.isPresent());
    }

    @Test
    public void aPropertyThatIsThereIsPresent() {
        assertEquals(Optional.of("hello"), config().present());
    }

    @Test
    public void theValueIsConvertedToTheTypeTheOptionalWraps() {
        Optional<Integer> port = config().port();
        assertTrue(port.isPresent());
        assertEquals(Integer.valueOf(80), port.get());
    }

    @Test
    public void theKeyIsResolvedAsUsual() {
        assertEquals(Optional.of("renamed value"), config().renamed());
    }

    @Test
    public void aDefaultValueMakesItAlwaysPresent() {
        assertEquals(Optional.of(8080), config().withDefault());
    }

    /**
     * The wrapper says what happens when the property is <b>absent</b>. A property that is there but cannot be
     * converted is a mistake to report, not an absence to paper over: turning it into an empty Optional would
     * make a typo indistinguishable from a property nobody set.
     */
    @Test
    public void aValueThatCannotBeConvertedStillFails() {
        MyConfig cfg = ConfigFactory.create(MyConfig.class, new Properties() {{
            setProperty("port", "8O80"); // the letter O, not a zero
        }});
        try {
            cfg.port();
            fail("UnsupportedOperationException is expected");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("Cannot convert '8O80'"));
            assertTrue(e.getMessage().contains("'port'"));
        }
    }

    interface WithEmptyValue extends Config {
        Optional<String> text();
    }

    /**
     * An empty value is a value like any other, here as everywhere else: the property is set, so the Optional
     * is present and holds the empty string.
     */
    @Test
    public void anEmptyValueIsPresentAndEmptyRatherThanAbsent() {
        WithEmptyValue cfg = ConfigFactory.create(WithEmptyValue.class, new Properties() {{
            setProperty("text", "");
        }});
        assertEquals(Optional.of(""), cfg.text());
    }

    // -------------------------------------------------------------------------------------------------
    // collections and arrays
    // -------------------------------------------------------------------------------------------------

    interface WithCollections extends Config {
        Optional<List<String>> names();

        Optional<List<Integer>> numbers();

        Optional<String[]> array();

        Optional<List<String>> missingList();
    }

    private static WithCollections collections() {
        return ConfigFactory.create(WithCollections.class, new Properties() {{
            setProperty("names", "foo, bar, baz");
            setProperty("numbers", "1, 2, 3");
            setProperty("array", "a, b");
        }});
    }

    @Test
    public void theCollectionInsideAnOptionalIsTokenizedAndConverted() {
        assertEquals(Optional.of(asList("foo", "bar", "baz")), collections().names());
        assertEquals(Optional.of(asList(1, 2, 3)), collections().numbers());
    }

    @Test
    public void anArrayInsideAnOptionalIsConverted() {
        Optional<String[]> array = collections().array();
        assertTrue(array.isPresent());
        assertArrayEquals(new String[]{"a", "b"}, array.get());
    }

    @Test
    public void aMissingCollectionIsEmptyOptionalRatherThanEmptyCollection() {
        assertFalse(collections().missingList().isPresent());
    }

    // -------------------------------------------------------------------------------------------------
    // raw Optional
    // -------------------------------------------------------------------------------------------------

    @SuppressWarnings("rawtypes")
    interface WithRawOptional extends Config {
        Optional raw();
    }

    /**
     * A raw <code>Optional</code> carries no type to convert to, and falls back on String: the same default a
     * raw collection takes.
     */
    interface WithWildcardOptional extends Config {
        Optional<?> wildcard();
    }

    /**
     * A wildcard names no type to convert to, any more than a raw <code>Optional</code> does, and takes the
     * same default.
     */
    @Test
    public void aWildcardOptionalHoldsAString() {
        WithWildcardOptional cfg = ConfigFactory.create(WithWildcardOptional.class, new Properties() {{
            setProperty("wildcard", "42");
        }});
        assertEquals(Optional.of("42"), cfg.wildcard());
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void aRawOptionalHoldsAString() {
        WithRawOptional cfg = ConfigFactory.create(WithRawOptional.class, new Properties() {{
            setProperty("raw", "42");
        }});
        Optional raw = cfg.raw();
        assertEquals("42", raw.get());
    }

    // -------------------------------------------------------------------------------------------------
    // converters
    // -------------------------------------------------------------------------------------------------

    public static class NullReturningConverter implements Converter<String> {
        @Override
        public String convert(Method method, String input) {
            return null;
        }
    }

    interface WithNullConverter extends Config {
        @ConverterClass(NullReturningConverter.class)
        Optional<String> value();
    }

    /**
     * A converter is free to return null, and an Optional cannot hold one: what it means is that the value
     * could not be turned into anything, which is exactly what an empty Optional says.
     */
    @Test
    public void aConverterReturningNullGivesAnEmptyOptional() {
        WithNullConverter cfg = ConfigFactory.create(WithNullConverter.class, new Properties() {{
            setProperty("value", "anything");
        }});
        assertFalse(cfg.value().isPresent());
    }

    public static class PairConverter implements Converter<Map<String, String>> {
        @Override
        public Map<String, String> convert(Method method, String input) {
            String[] pair = input.split("=", 2);
            Map<String, String> result = new LinkedHashMap<>();
            result.put(pair[0].trim(), pair[1].trim());
            return result;
        }
    }

    interface WithGenericArray extends Config {
        @ConverterClass(PairConverter.class)
        Optional<Map<String, String>[]> pairs();
    }

    /**
     * An array of a generic type is the one shape the reflection API does not hand over as a class: the
     * element type has to be put back together to know what array to build.
     */
    @Test
    public void anArrayOfAGenericTypeInsideAnOptionalIsConverted() {
        WithGenericArray cfg = ConfigFactory.create(WithGenericArray.class, new Properties() {{
            setProperty("pairs", "a=1, b=2");
        }});
        Optional<Map<String, String>[]> pairs = cfg.pairs();
        assertTrue(pairs.isPresent());
        assertEquals(Map[].class, pairs.get().getClass());
        assertEquals(2, pairs.get().length);
        assertEquals("1", pairs.get()[0].get("a"));
        assertEquals("2", pairs.get()[1].get("b"));
    }

    // -------------------------------------------------------------------------------------------------
    // interaction with @Mandatory
    // -------------------------------------------------------------------------------------------------

    interface MandatoryAndOptional extends Config {
        @Mandatory
        Optional<String> contradiction();
    }

    @Test
    public void mandatoryOnAnOptionalMethodIsRejectedWhenTheConfigIsCreated() {
        try {
            ConfigFactory.create(MandatoryAndOptional.class);
            fail("UnsupportedOperationException is expected");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("contradiction"));
            assertTrue(e.getMessage().contains("@Mandatory"));
            assertTrue(e.getMessage().contains("Optional"));
        }
    }

    @Mandatory
    interface AllMandatoryButOne extends Config {
        String required();

        Optional<String> exempted();
    }

    /**
     * Annotating the interface is a way of saying "these are all required", and a method returning an Optional
     * is the exception being made: it does not have to be moved to an interface of its own.
     */
    @Test
    public void anOptionalMethodIsExemptedFromAMandatoryOnTheInterface() {
        AllMandatoryButOne cfg = ConfigFactory.create(AllMandatoryButOne.class, new Properties() {{
            setProperty("required", "here");
        }});
        assertEquals("here", cfg.required());
        assertFalse(cfg.exempted().isPresent());
    }

    // -------------------------------------------------------------------------------------------------
    // the Optional follows the properties
    // -------------------------------------------------------------------------------------------------

    interface MutableConfig extends Mutable {
        Optional<String> value();
    }

    @Test
    public void theOptionalIsResolvedOnEveryAccess() {
        MutableConfig cfg = ConfigFactory.create(MutableConfig.class);
        assertFalse(cfg.value().isPresent());

        cfg.setProperty("value", "now here");
        assertEquals(Optional.of("now here"), cfg.value());

        cfg.removeProperty("value");
        assertFalse(cfg.value().isPresent());
    }
}
