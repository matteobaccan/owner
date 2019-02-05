/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import java.util.Map;
import java.util.Properties;
import org.junit.Before;
import org.junit.Test;

import static java.util.Collections.singletonMap;
import static org.junit.Assert.assertEquals;

/**
 * @author Wander Costa
 */
public class ConfigCacheTest {

    private static final String DUMMY_KEY = "dummy-key";
    private static final Map<String, String> DUMMY_MAP = singletonMap(DUMMY_KEY, "dummy-value");

    interface MyConfig extends Config {
        @DefaultValue("${" + DUMMY_KEY + "}")
        String value();
    }

    @Before
    public void before() {
        ConfigCache.clear();
    }

    @Test
    public void testCachedReferenceToEnvironmentVariable() {
        MyConfig cfg = ConfigCache.getOrCreate(MyConfig.class, DUMMY_MAP);
        assertEquals(DUMMY_MAP.get(DUMMY_KEY), cfg.value());
    }

    @Test
    public void testCachedReferenceToSystemProperty() {
        Properties properties = new Properties();
        properties.putAll(DUMMY_MAP);

        MyConfig cfg = ConfigCache.getOrCreate(MyConfig.class, properties);
        assertEquals(DUMMY_MAP.get(DUMMY_KEY), cfg.value());
    }
}
