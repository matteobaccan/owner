/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.junit.Test;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Which map types a group of properties can be read into, and what the two that cannot look like.
 * <p>
 * The rule is one sentence: <b>a concrete class is instantiated through its no-argument constructor, and an
 * interface gets a {@link TreeMap} if it is sorted and a {@link LinkedHashMap} otherwise</b>. Everything
 * below follows from it, including the two failures - which are worth pinning precisely because one of them
 * fails well and the other does not.
 * </p>
 *
 * @author Matteo Baccan
 */
public class MapTypesTest {

    private static final Map<String, String> GROUP = new HashMap<>();
    /** Written out of order on purpose, so that a map claiming to sort has to prove it. */
    private static final Map<String, String> NAMED_LIKE_THE_ENUM = new HashMap<>();

    static {
        GROUP.put("server.beta", "2");
        GROUP.put("server.alpha", "1");
        GROUP.put("server.gamma", "3");

        NAMED_LIKE_THE_ENUM.put("server.BETA", "2");
        NAMED_LIKE_THE_ENUM.put("server.ALPHA", "1");
        NAMED_LIKE_THE_ENUM.put("server.GAMMA", "3");
    }

    public interface AsInterface extends Config {
        Map<String, Integer> server();
    }

    public interface AsSorted extends Config {
        SortedMap<String, Integer> server();
    }

    public interface AsNavigable extends Config {
        NavigableMap<String, Integer> server();
    }

    public interface AsHashMap extends Config {
        HashMap<String, Integer> server();
    }

    public interface AsTreeMap extends Config {
        TreeMap<String, Integer> server();
    }

    public interface AsLinkedHashMap extends Config {
        LinkedHashMap<String, Integer> server();
    }

    public interface AsConcurrentHashMap extends Config {
        ConcurrentHashMap<String, Integer> server();
    }

    public interface AsProperties extends Config {
        Properties server();
    }

    public interface AsEnumMap extends Config {
        EnumMap<Which, Integer> server();
    }

    public interface AsConcurrentMap extends Config {
        ConcurrentMap<String, Integer> server();
    }

    public interface AsConcurrentNavigable extends Config {
        ConcurrentNavigableMap<String, Integer> server();
    }

    @SuppressWarnings("rawtypes")
    public interface AsRawEnumMap extends Config {
        EnumMap server();
    }

    public interface AsSomebodyElses extends Config {
        SomebodyElsesMap<String, Integer> server();
    }

    public interface AsNeedsAnArgument extends Config {
        NeedsAnArgument<String, Integer> server();
    }

    public enum Which { ALPHA, BETA, GAMMA }

    // ---------------------------------------------------------------- an interface gets one chosen for it

    @Test
    public void aPlainMapKeepsTheOrderItWasBuiltIn() {
        Map<String, Integer> map = ConfigFactory.create(AsInterface.class, GROUP).server();
        assertEquals(3, map.size());
        assertEquals(Integer.valueOf(1), map.get("alpha"));
        assertTrue(map.getClass().getName(), map instanceof LinkedHashMap);
    }

    @Test
    public void aSortedMapIsSorted() {
        SortedMap<String, Integer> map = ConfigFactory.create(AsSorted.class, GROUP).server();
        assertEquals("alpha", map.firstKey());
        assertTrue(map.getClass().getName(), map instanceof TreeMap);
    }

    /** NavigableMap extends SortedMap, so it takes the same branch and a TreeMap satisfies it. */
    @Test
    public void aNavigableMapIsSortedToo() {
        NavigableMap<String, Integer> map = ConfigFactory.create(AsNavigable.class, GROUP).server();
        assertEquals("gamma", map.lastKey());
    }

    // ---------------------------------------------------------------- a class is built by its constructor

    @Test
    public void aConcreteClassIsInstantiatedByItsNoArgumentConstructor() {
        assertEquals(HashMap.class, ConfigFactory.create(AsHashMap.class, GROUP).server().getClass());
        assertEquals(TreeMap.class, ConfigFactory.create(AsTreeMap.class, GROUP).server().getClass());
        assertEquals(LinkedHashMap.class,
                ConfigFactory.create(AsLinkedHashMap.class, GROUP).server().getClass());
        assertEquals(ConcurrentHashMap.class,
                ConfigFactory.create(AsConcurrentHashMap.class, GROUP).server().getClass());
    }

    /** Properties is a Map, so it is a group like any other - and its entries are its own kind of pair. */
    @Test
    public void evenPropertiesIsAMap() {
        Properties group = ConfigFactory.create(AsProperties.class, GROUP).server();
        assertEquals(3, group.size());
        assertEquals("1", group.get("alpha"));
    }

    /**
     * A concurrent interface used to be handed a LinkedHashMap, which does not satisfy it, and the proxy
     * refused the value on the way out with a ClassCastException that said nothing about the cause. The
     * implementation now has to satisfy the interface, so these work rather than fail.
     */
    @Test
    public void aConcurrentMapGetsSomethingConcurrent() {
        ConcurrentMap<String, Integer> map = ConfigFactory.create(AsConcurrentMap.class, GROUP).server();
        assertEquals(3, map.size());
        assertEquals(ConcurrentHashMap.class, map.getClass());
    }

    /**
     * The one that was wrong twice over: ConcurrentNavigableMap is a SortedMap too, so it took that branch
     * and was handed a TreeMap - which is not concurrent either.
     */
    @Test
    public void aConcurrentNavigableMapGetsSomethingBothConcurrentAndSorted() {
        ConcurrentNavigableMap<String, Integer> map =
                ConfigFactory.create(AsConcurrentNavigable.class, GROUP).server();
        assertEquals("alpha", map.firstKey());
        assertEquals(ConcurrentSkipListMap.class, map.getClass());
    }

    /**
     * EnumMap cannot be built by the ordinary path - it is the one map in the JDK that needs to be told the
     * class of its keys - but that class is already in hand, read off the return type in order to convert
     * the keys. So it works, and the keys come back as the enum constants.
     */
    @Test
    public void anEnumMapIsBuiltFromTheKeyTypeItDeclares() {
        EnumMap<Which, Integer> map = ConfigFactory.create(AsEnumMap.class, NAMED_LIKE_THE_ENUM).server();
        assertEquals(3, map.size());
        assertEquals(Integer.valueOf(1), map.get(Which.ALPHA));
        assertEquals("an EnumMap iterates in the order the constants are declared",
                Which.ALPHA, map.keySet().iterator().next());
    }

    /**
     * The keys of the group are converted to the key type like any other value, so for an enum they have to
     * name a constant exactly - the conversion is Enum.valueOf and nothing is folded. A property called
     * <code>server.alpha</code> does not become <code>ALPHA</code>, and says which property it choked on.
     */
    @Test
    public void aKeyThatNamesNoConstantSaysWhichPropertyItWas() {
        try {
            ConfigFactory.create(AsEnumMap.class, GROUP).server();
            fail("'alpha' is not a constant of Which");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("server.alpha"));
        }
    }

    // ---------------------------------------------------------------- what is left, and how it reads

    /** A raw EnumMap has no key type to read, so it cannot be built - and the message says what to write. */
    @Test
    public void aRawEnumMapSaysWhatIsMissing() {
        try {
            ConfigFactory.create(AsRawEnumMap.class, GROUP).server();
            fail("an EnumMap needs an enum key type");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("has to be an enum"));
        }
    }

    /**
     * A map interface OWNER has never heard of cannot be satisfied by anything it knows how to build.
     * It now says so, where before it handed back a LinkedHashMap and let the proxy refuse it.
     */
    @Test
    public void anUnknownMapInterfaceIsRefusedWithItsNameInTheMessage() {
        try {
            ConfigFactory.create(AsSomebodyElses.class, GROUP).server();
            fail("nothing OWNER can build satisfies that interface");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("no implementation known to OWNER"));
            assertTrue(e.getMessage(), e.getMessage().contains("SomebodyElsesMap"));
        }
    }

    /** A class with no no-argument constructor still cannot be built, and still says which one. */
    @Test
    public void aClassWithNoUsableConstructorSaysWhichOne() {
        try {
            ConfigFactory.create(AsNeedsAnArgument.class, GROUP).server();
            fail("that class cannot be instantiated");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Cannot instantiate map"));
            assertTrue(e.getMessage(), e.getMessage().contains("NeedsAnArgument"));
        }
    }

    public interface SomebodyElsesMap<K, V> extends Map<K, V> { }

    public static class NeedsAnArgument<K, V> extends HashMap<K, V> {
        private static final long serialVersionUID = 1L;

        public NeedsAnArgument(int size) {
            super(size);
        }
    }
}
