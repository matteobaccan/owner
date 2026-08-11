/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.util;

import org.junit.Test;

import java.util.Map;

import static org.aeonbits.owner.util.Collections.entry;
import static org.aeonbits.owner.util.Collections.map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Matteo Baccan
 */
public class CollectionsTest {

    @Test
    public void testMapEqualsItself() {
        Map<String, String> map = map("foo", "bar");
        assertTrue(map.equals(map));
    }

    @Test
    public void testMapNotEqualsNull() {
        assertFalse(map("foo", "bar").equals(null));
    }

    @Test
    public void testMapNotEqualsObjectOfDifferentClass() {
        assertFalse(map("foo", "bar").equals("foo=bar"));
    }

    @Test
    public void testMapsWithSameEntriesAreEqual() {
        Map<String, String> first = map(entry("foo", "bar"));
        Map<String, String> second = map(entry("foo", "bar"));
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    public void testMapsWithDifferentEntriesAreNotEqual() {
        Map<String, String> first = map("foo", "bar");
        Map<String, String> second = map("foo", "baz");
        assertNotEquals(first, second);
    }
}
