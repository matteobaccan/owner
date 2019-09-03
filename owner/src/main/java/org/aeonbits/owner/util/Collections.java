/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.util;

import java.io.Serializable;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static java.util.Arrays.asList;

/**
 * Utility class to create a maps, lists and sets
 * <p> Examples of usage: </p>
 * <pre>
 * import static org.aeonbits.owner.util.Collections.map;
 * import static org.aeonbits.owner.util.Collections.entry;
 * import static org.aeonbits.owner.util.Collections.set;
 * import static org.aeonbits.owner.util.Collections.list;
 *
 * Map&lt;String, String&gt; myMap = map("foo", "bar");
 * Map&lt;String, String&gt; myMap2 = map(entry("foo", "bar"), entry("baz", "qux");
 * Set&lt;String&gt; mySet = set("foo", "bar", "baz", "qux");
 * List&lt;String&gt; myList = list("foo", "bar", "baz", "qux");
 * </pre>
 *
 * @author Luigi R. Viggiano
 * @since 1.0.6
 */
public abstract class Collections {

    // Suppresses default constructor, ensuring no one instantiate this class.
    private Collections() {}

    private static final class EntryMap<K, V> extends AbstractMap<K, V> implements Serializable {
        private static final long serialVersionUID = -789853606407653214L;
        private final Set<Entry<? extends K, ? extends V>> entries;

        private EntryMap(Entry<? extends K, ? extends V>... entries) {
            this.entries = set(entries);
        }

        @SuppressWarnings("unchecked")
        @Override
        public Set<Entry<K, V>> entrySet() {
            return (Set) entries;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;

            EntryMap<?, ?> entryMap = (EntryMap<?, ?>) o;

            return entries.equals(entryMap.entries);
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + entries.hashCode();
            return result;
        }
    }

    public static <K, V> Entry<K, V> entry(K key, V value) {
        return new SimpleEntry<K, V>(key, value);
    }

    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> map(K key, V value) {
        return map(entry(key, value));
    }

    public static <K, V> Map<K, V> map(Map.Entry<? extends K, ? extends V>... entries) {
        return new EntryMap<K, V>(entries);
    }

    public static <E> Set<E> set(E... elements) {
        return new LinkedHashSet<E>(list(elements));
    }

    public static <E> List<E> list(E... elements) {
        return asList(elements);
    }

}
