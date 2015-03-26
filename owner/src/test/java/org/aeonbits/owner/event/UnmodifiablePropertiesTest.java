/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.event;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;

import static org.junit.Assert.assertTrue;

/**
 * @author Luigi R. Viggiano
 */
public class UnmodifiablePropertiesTest {
    private Properties props = new Properties() {{
        setProperty("foo", "bar");
        setProperty("baz", "qux");
    }};

    private UnmodifiableProperties unmodifiable = new UnmodifiableProperties(props);

    @Test(expected = UnsupportedOperationException.class)
    public void testSetProperty() {
        unmodifiable.setProperty("blah", "blah");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testPut() {
        unmodifiable.put("blah", "blah");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testRemove() {
        unmodifiable.remove("foo");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testClear() {
        unmodifiable.clear();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testKeySet() {
        unmodifiable.keySet().clear();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testEntrySet() {
        unmodifiable.entrySet().clear();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testValues() {
        unmodifiable.values().clear();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testLoadReader() throws IOException {
        unmodifiable.load(new StringReader("someProperty=someValue"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testLoadInputStream() throws IOException {
        unmodifiable.load(new ByteArrayInputStream("someProperty=someValue".getBytes()));
    }

    @Test
    public void testContentIsSame() {
        assertTrue(unmodifiable.entrySet().containsAll(props.entrySet()));
    }

}
