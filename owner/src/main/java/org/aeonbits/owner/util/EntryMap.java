/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.util;

import java.util.AbstractMap;
import java.util.HashSet;
import java.util.Set;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableSet;

/**
 * Utility class to create a Map from a single entry (key-value pair).
 *
 * <p>
 * Example of usage:
 * </p>
 *
 * <pre>
 * import static org.aeonbits.owner.util.EntryMap.map;
 *
 * Map&lt;String, String&gt; myMap = map("foo", "bar");
 *
 * String bar = myMap.get("foo");
 * </pre>
 *
 * @since 1.0.6
 * @author Luigi R. Viggiano
 */
public class EntryMap<K, V> extends AbstractMap<K, V> {
    private final Entry<K, V> entry;
    private final Set<Entry<K, V>> entrySet;

    private EntryMap(final K key, final V value) {
        entry = new SimpleEntry<K, V>(key, value);
        entrySet = unmodifiableSet(new HashSet<Entry<K, V>>(asList(entry)));
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return entrySet;
    }

    public static <K, V> EntryMap<K, V> map(K key, V value) {
        return new EntryMap<K, V>(key, value);
    }
}
