/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.issues;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigCache;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Factory;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * See: https://github.com/lviggiano/owner/issues/195
 * <p>
 * Imports are merged into a {@link java.util.Properties}, which only admits String keys and values. Anything else
 * used to be accepted and then silently misbehave when read, so it is now rejected up front.
 */
public class Issue195Test {

    private static final String KEY = "some.key";

    public interface MyConfig extends Config {
        @Key(KEY)
        @DefaultValue("1")
        Integer getSomeValue();
    }

    private static Map<Object, Object> importOf(Object key, Object value) {
        Map<Object, Object> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    @Test
    public void testConfigImportMapWithNonStringValue() {
        Object[] illegalValues = {Integer.valueOf(42), new StringBuffer("42"), new StringBuilder("42")};

        for (Object illegalValue : illegalValues) {
            try {
                ConfigFactory.create(MyConfig.class, importOf(KEY, illegalValue));
                fail("A non-string value of type " + illegalValue.getClass() + " should result in an exception");
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().contains(KEY));
            }
        }
    }

    @Test
    public void testConfigImportMapWithNonStringKey() {
        Object[] illegalKeys = {Integer.valueOf(42), new StringBuffer(KEY), new StringBuilder(KEY)};

        for (Object illegalKey : illegalKeys) {
            try {
                ConfigFactory.create(MyConfig.class, importOf(illegalKey, "42"));
                fail("A non-string key of type " + illegalKey.getClass() + " should result in an exception");
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().contains("non-string key"));
            }
        }
    }

    @Test
    public void testConfigImportMapWithStringValueStillWorks() {
        MyConfig config = ConfigFactory.create(MyConfig.class, importOf(KEY, "42"));
        assertEquals(Integer.valueOf(42), config.getSomeValue());
    }

    /**
     * The validation must not depend on which entry point was used: {@link Factory#create(Class, Map[])} and
     * {@link ConfigCache} both bypass {@link ConfigFactory#create(Class, Map[])} entirely.
     */
    @Test
    public void testValidationAppliesToFactoryInstance() {
        Factory factory = ConfigFactory.newInstance();
        try {
            factory.create(MyConfig.class, importOf(KEY, Integer.valueOf(42)));
            fail("A non-string value should result in an exception through Factory.create too");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains(KEY));
        }
    }

    @Test
    public void testValidationAppliesToConfigCache() {
        try {
            ConfigCache.getOrCreate(MyConfig.class, importOf(KEY, Integer.valueOf(42)));
            fail("A non-string value should result in an exception through ConfigCache too");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains(KEY));
        } finally {
            ConfigCache.clear();
        }
    }
}
