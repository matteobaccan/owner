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

    static {
        GROUP.put("server.beta", "2");
        GROUP.put("server.alpha", "1");
        GROUP.put("server.gamma", "3");
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

    // ---------------------------------------------------------------- the two that cannot be built

    /**
     * EnumMap has no no-argument constructor - it needs the key class - so it cannot be built the way every
     * other concrete map is. It fails well: the message names the type, at the first call.
     */
    @Test
    public void anEnumMapCannotBeBuiltAndSaysSo() {
        try {
            ConfigFactory.create(AsEnumMap.class, GROUP).server();
            fail("EnumMap has no no-argument constructor and cannot be instantiated");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Cannot instantiate map"));
            assertTrue(e.getMessage(), e.getMessage().contains("EnumMap"));
        }
    }

    /**
     * ConcurrentMap is an interface that is not sorted, so it takes the LinkedHashMap branch - and a
     * LinkedHashMap is not a ConcurrentMap. The proxy then refuses the value on the way out, which is a
     * ClassCastException with nothing in it to say what to do about it. This is the one gap worth knowing
     * about: it is a poorer failure than the EnumMap one above, not a different capability.
     */
    @Test
    public void aConcurrentMapIsTheOneThatFailsPoorly() {
        try {
            ConfigFactory.create(AsConcurrentMap.class, GROUP).server();
            fail("a LinkedHashMap cannot be returned as a ConcurrentMap");
        } catch (ClassCastException e) {
            assertTrue(String.valueOf(e.getMessage()), true);
        }
    }
}
