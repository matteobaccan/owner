/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.issues;

import org.aeonbits.owner.Accessible;
import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * See: https://github.com/lviggiano/owner/issues/41
 * <p>
 * The request was a map built out of <em>several properties sharing a prefix</em>, which is a different shape
 * from the one covered in {@link Issue286Test}: a {@code @ConverterClass} is handed the value of one property
 * and cannot see the others, so it cannot answer this one. A {@code default} method can, since it runs on the
 * proxy and reads the whole set through {@link Accessible#fill(Map)}.
 * <p>
 * The interface has to be public: a {@code default} method declared on a non-public one currently fails with an
 * {@code IllegalAccessException} on modern JDKs.
 */
public class Issue41Test {

    private static final String PREFIX = "something.";

    public interface PrefixedMapConfig extends Config, Accessible {
        @Key("something.qux")
        @DefaultValue("4")
        int qux();

        default Map<String, Integer> something() {
            Properties all = new Properties();
            fill(all);
            Map<String, Integer> result = new TreeMap<>();
            for (String name : all.stringPropertyNames())
                if (name.startsWith(PREFIX))
                    result.put(name.substring(PREFIX.length()), Integer.valueOf(all.getProperty(name)));
            return result;
        }
    }

    private static PrefixedMapConfig config() {
        return ConfigFactory.create(PrefixedMapConfig.class, new Properties() {{
            setProperty("something.foo", "1");
            setProperty("something.bar", "2");
            setProperty("something.baz", "3");
            setProperty("unrelated", "9");
        }});
    }

    @Test
    public void severalPropertiesSharingAPrefixAreCollectedIntoOneMap() {
        Map<String, Integer> something = config().something();

        assertEquals(Integer.valueOf(1), something.get("foo"));
        assertEquals(Integer.valueOf(2), something.get("bar"));
        assertEquals(Integer.valueOf(3), something.get("baz"));
    }

    @Test
    public void whatDoesNotShareThePrefixStaysOut() {
        assertFalse(config().something().containsKey("unrelated"));
    }

    /** A property declared with a {@code @DefaultValue} is part of the set, even with no source defining it. */
    @Test
    public void defaultValuesTakePartInTheCollection() {
        assertEquals(Integer.valueOf(4), config().something().get("qux"));
    }

    /** The map is built by the method, so its type and ordering are whatever the method chose. */
    @Test
    public void theMapIsTheOneTheMethodBuilt() {
        assertEquals("[bar, baz, foo, qux]", config().something().keySet().toString());
    }
}
