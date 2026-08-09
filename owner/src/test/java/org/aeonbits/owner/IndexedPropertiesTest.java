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
import org.aeonbits.owner.Config.Prefix;
import org.aeonbits.owner.Config.Separator;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A list written one element per key: <code>servers[0]</code>, <code>servers[1]</code>. See
 * <code>FORMATS.md</code> for why the notation is square brackets, why an indexed key wins over a single
 * value, and why a gap is refused rather than closed up.
 *
 * @author Matteo Baccan
 */
public class IndexedPropertiesTest {

    interface ServerConfig extends Config {
        List<String> servers();
    }

    private static <T extends Config> T create(Class<T> type, String... pairs) {
        Properties props = new Properties();
        for (int i = 0; i < pairs.length; i += 2)
            props.setProperty(pairs[i], pairs[i + 1]);
        return ConfigFactory.create(type, props);
    }

    // -------------------------------------------------------------------------------------------------
    // reading one
    // -------------------------------------------------------------------------------------------------

    @Test
    public void aListIsReadFromItsIndexedKeys() {
        ServerConfig cfg = create(ServerConfig.class, "servers[0]", "alpha", "servers[1]", "beta");
        assertEquals(Arrays.asList("alpha", "beta"), cfg.servers());
    }

    @Test
    public void oneElementIsAList() {
        assertEquals(Arrays.asList("alpha"), create(ServerConfig.class, "servers[0]", "alpha").servers());
    }

    /** The file may write them in any order; the index says where each one goes. */
    @Test
    public void theOrderIsTheIndexAndNotTheOrderInTheFile() {
        ServerConfig cfg = create(ServerConfig.class,
                "servers[2]", "gamma", "servers[0]", "alpha", "servers[1]", "beta");
        assertEquals(Arrays.asList("alpha", "beta", "gamma"), cfg.servers());
    }

    @Test
    public void tenElementsAreOrderedByNumberAndNotAsText() {
        ServerConfig cfg = create(ServerConfig.class,
                "servers[0]", "a", "servers[1]", "b", "servers[2]", "c", "servers[3]", "d", "servers[4]", "e",
                "servers[5]", "f", "servers[6]", "g", "servers[7]", "h", "servers[8]", "i", "servers[9]", "j",
                "servers[10]", "k");
        assertEquals("k", cfg.servers().get(10));
        assertEquals(11, cfg.servers().size());
    }

    @Test
    public void anEmptyElementIsAnEmptyString() {
        ServerConfig cfg = create(ServerConfig.class, "servers[0]", "", "servers[1]", "beta");
        assertEquals(Arrays.asList("", "beta"), cfg.servers());
    }

    // -------------------------------------------------------------------------------------------------
    // the point of it: an element is one element, whatever it contains
    // -------------------------------------------------------------------------------------------------

    @Test
    public void theSeparatorDoesNotApplyToAnIndexedElement() {
        ServerConfig cfg = create(ServerConfig.class, "servers[0]", "a,b", "servers[1]", "c");
        assertEquals("a value holding the separator is still one element",
                Arrays.asList("a,b", "c"), cfg.servers());
    }

    @Separator(";")
    interface CustomSeparator extends Config {
        List<String> servers();
    }

    @Test
    public void aDeclaredSeparatorDoesNotApplyEither() {
        CustomSeparator cfg = create(CustomSeparator.class, "servers[0]", "a;b");
        assertEquals(Arrays.asList("a;b"), cfg.servers());
    }

    // -------------------------------------------------------------------------------------------------
    // the types it produces
    // -------------------------------------------------------------------------------------------------

    interface Typed extends Config {
        int[] ports();

        List<Integer> numbers();

        Set<String> names();

        SortedSet<String> sorted();

        Collection<String> anyCollection();

        String[] words();
    }

    @Test
    public void anArrayOfPrimitivesIsBuilt() {
        Typed cfg = create(Typed.class, "ports[0]", "80", "ports[1]", "443");
        assertArrayEquals(new int[]{80, 443}, cfg.ports());
    }

    @Test
    public void anArrayOfObjectsIsBuilt() {
        Typed cfg = create(Typed.class, "words[0]", "alpha", "words[1]", "beta");
        assertArrayEquals(new String[]{"alpha", "beta"}, cfg.words());
    }

    @Test
    public void everyElementGoesThroughTheOrdinaryConversion() {
        Typed cfg = create(Typed.class, "numbers[0]", "1", "numbers[1]", "2");
        assertEquals(Arrays.asList(1, 2), cfg.numbers());
    }

    @Test
    public void theCollectionAskedForIsTheOneBuilt() {
        Typed cfg = create(Typed.class,
                "names[0]", "b", "names[1]", "a",
                "sorted[0]", "b", "sorted[1]", "a",
                "anyCollection[0]", "x");

        assertTrue(cfg.names() instanceof LinkedHashSet);
        assertEquals("a set keeps the order the indices give it", Arrays.asList("b", "a"),
                new java.util.ArrayList<>(cfg.names()));

        assertTrue(cfg.sorted() instanceof TreeSet);
        assertEquals(Arrays.asList("a", "b"), new java.util.ArrayList<>(cfg.sorted()));

        assertEquals(1, cfg.anyCollection().size());
    }

    enum Colour { PINK, BLACK }

    interface WithEnums extends Config {
        List<Colour> colours();

        EnumSet<Colour> colourSet();
    }

    @Test
    public void aListOfEnumsIsBuilt() {
        WithEnums cfg = create(WithEnums.class, "colours[0]", "PINK", "colours[1]", "BLACK");
        assertEquals(Arrays.asList(Colour.PINK, Colour.BLACK), cfg.colours());
    }

    @Test
    public void anEnumSetIsBuilt() {
        WithEnums cfg = create(WithEnums.class, "colourSet[0]", "BLACK", "colourSet[1]", "PINK");
        assertEquals(EnumSet.of(Colour.PINK, Colour.BLACK), cfg.colourSet());
    }

    interface WithOptional extends Config {
        Optional<List<String>> servers();
    }

    @Test
    public void anOptionalListIsBuilt() {
        WithOptional cfg = create(WithOptional.class, "servers[0]", "alpha");
        assertTrue(cfg.servers().isPresent());
        assertEquals(Arrays.asList("alpha"), cfg.servers().get());
    }

    @Test
    public void anOptionalListIsEmptyWhenNothingIsThere() {
        assertFalse(create(WithOptional.class).servers().isPresent());
    }

    // -------------------------------------------------------------------------------------------------
    // precedence
    // -------------------------------------------------------------------------------------------------

    @Test
    public void anIndexedKeyWinsOverASingleValue() {
        ServerConfig cfg = create(ServerConfig.class,
                "servers", "ignored,entirely", "servers[0]", "alpha", "servers[1]", "beta");
        assertEquals(Arrays.asList("alpha", "beta"), cfg.servers());
    }

    interface WithDefault extends Config {
        @DefaultValue("one,two")
        List<String> servers();
    }

    @Test
    public void anIndexedKeyWinsOverADefaultValue() {
        assertEquals(Arrays.asList("alpha"), create(WithDefault.class, "servers[0]", "alpha").servers());
    }

    @Test
    public void theDefaultValueStillAppliesWhenThereIsNoIndexedKey() {
        assertEquals(Arrays.asList("one", "two"), create(WithDefault.class).servers());
    }

    /** Nothing that worked before changes: a single comma-separated value is read exactly as it was. */
    @Test
    public void aSingleValueIsStillSplitWhenNoIndexedKeyIsThere() {
        ServerConfig cfg = create(ServerConfig.class, "servers", "alpha,beta");
        assertEquals(Arrays.asList("alpha", "beta"), cfg.servers());
    }

    @Test
    public void aListWithNoPropertyAtAllIsNull() {
        assertNull(create(ServerConfig.class).servers());
    }

    // -------------------------------------------------------------------------------------------------
    // what is not an element
    // -------------------------------------------------------------------------------------------------

    /** Something below an element belongs to nested interfaces, and must not be mistaken for one. */
    @Test
    public void aKeyBelowAnElementIsNotAnElement() {
        ServerConfig cfg = create(ServerConfig.class,
                "servers[0]", "alpha", "servers[0].port", "8080", "servers[1]", "beta");
        assertEquals(Arrays.asList("alpha", "beta"), cfg.servers());
    }

    @Test
    public void aBracketHoldingSomethingOtherThanDigitsIsNotAnIndex() {
        ServerConfig cfg = create(ServerConfig.class, "servers[first]", "alpha", "servers", "beta");
        assertEquals("nothing was indexed, so the single value is read", Arrays.asList("beta"), cfg.servers());
    }

    /** A minus sign is not a digit, so a negative index is a key of its own and never an element. */
    @Test
    public void aNegativeIndexIsNotAnIndex() {
        ServerConfig cfg = create(ServerConfig.class, "servers[-1]", "alpha", "servers", "beta");
        assertEquals(Arrays.asList("beta"), cfg.servers());
    }

    @Test
    public void anEmptyBracketIsNotAnIndex() {
        ServerConfig cfg = create(ServerConfig.class, "servers[]", "alpha", "servers", "beta");
        assertEquals(Arrays.asList("beta"), cfg.servers());
    }

    @Test
    public void anIndexTooLargeForAnIntIsNotAnIndex() {
        ServerConfig cfg = create(ServerConfig.class, "servers[99999999999999999999]", "alpha",
                "servers", "beta");
        assertEquals(Arrays.asList("beta"), cfg.servers());
    }

    @Test
    public void aKeyOfAnotherPropertyThatMerelyStartsTheSameIsNotAnElement() {
        ServerConfig cfg = create(ServerConfig.class, "serversBackup[0]", "other", "servers[0]", "alpha");
        assertEquals(Arrays.asList("alpha"), cfg.servers());
    }

    // -------------------------------------------------------------------------------------------------
    // gaps are refused
    // -------------------------------------------------------------------------------------------------

    @Test
    public void aGapInTheMiddleIsRefused() {
        try {
            create(ServerConfig.class, "servers[0]", "alpha", "servers[2]", "gamma").servers();
            fail("a missing index should be refused");
        } catch (UnsupportedOperationException expected) {
            String message = expected.getMessage();
            assertTrue(message, message.contains("skip from 'servers[0]' to 'servers[2]'"));
        }
    }

    @Test
    public void aSequenceThatDoesNotStartAtZeroIsRefused() {
        try {
            create(ServerConfig.class, "servers[1]", "beta", "servers[2]", "gamma").servers();
            fail("a list that does not start at zero should be refused");
        } catch (UnsupportedOperationException expected) {
            String message = expected.getMessage();
            assertTrue(message, message.contains("start at 'servers[1]'"));
            assertTrue(message, message.contains("servers[0]"));
        }
    }

    @Test
    public void aLoneElementAtAHighIndexIsRefused() {
        try {
            create(ServerConfig.class, "servers[5]", "alpha").servers();
            fail("a lone element at index five is not a list of one");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("servers[5]"));
        }
    }

    // -------------------------------------------------------------------------------------------------
    // living beside what was already there
    // -------------------------------------------------------------------------------------------------

    @Prefix("app.")
    interface Prefixed extends Config {
        List<String> servers();
    }

    @Test
    public void theKeyIsTheOneTheMethodResolvesTo() {
        Prefixed cfg = create(Prefixed.class, "app.servers[0]", "alpha", "servers[0]", "not this one");
        assertEquals(Arrays.asList("alpha"), cfg.servers());
    }

    interface Renamed extends Config {
        @Key("the.servers")
        List<String> servers();
    }

    @Test
    public void theKeyAnnotationIsHonoured() {
        assertEquals(Arrays.asList("alpha"),
                create(Renamed.class, "the.servers[0]", "alpha").servers());
    }

    public static class Doubler implements Converter<String> {
        @Override
        public String convert(Method method, String input) {
            return input + input;
        }
    }

    interface WithConverter extends Config {
        @ConverterClass(Doubler.class)
        List<String> servers();
    }

    /** A converter named on the method is handed the raw value of one property and keeps precedence. */
    @Test
    public void aMethodNamingAConverterIsLeftAlone() {
        WithConverter cfg = create(WithConverter.class, "servers", "x", "servers[0]", "ignored");
        assertEquals(Arrays.asList("xx"), cfg.servers());
    }

    interface NotAList extends Config {
        String servers();
    }

    @Test
    public void aMethodThatIsNotAListIgnoresIndexedKeys() {
        NotAList cfg = create(NotAList.class, "servers", "plain", "servers[0]", "indexed");
        assertEquals("plain", cfg.servers());
    }

    interface GroupAndList extends Accessible {
        java.util.Map<String, String> servers();
    }

    /** The dot and the bracket do not collide: a map reads {@code servers.} and never {@code servers[0]}. */
    @Test
    public void aMapDoesNotPickUpIndexedKeys() {
        GroupAndList cfg = create(GroupAndList.class,
                "servers.host", "localhost", "servers[0]", "alpha");
        assertEquals(1, cfg.servers().size());
        assertEquals("localhost", cfg.servers().get("host"));
    }
}
