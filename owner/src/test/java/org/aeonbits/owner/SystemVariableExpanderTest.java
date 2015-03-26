/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertEquals;

/**
 * @author Luigi R. Viggiano
 */
public class SystemVariableExpanderTest {
    private VariablesExpander expander;

    @Before
    public void before() {
        expander = new VariablesExpander(new Properties());
    }

    @Test
    public void shouldNotExpandTildesInTheMiddleOfTheString() {
        String result = expander.expand("foo-~-bar-~-baz");
        String expected = "foo-~-bar-~-baz";
        assertEquals(expected, result);
    }

    @Test
    public void shouldExpandStringsComposedByOnlyTilde() {
        String result = expander.expand("~");
        String expected = System.getProperty("user.home");
        assertEquals(expected, result);
    }

    @Test
    public void shouldExpandStringsComposedByTildeSlash() {
        String result = expander.expand("~/");
        String expected = System.getProperty("user.home") + "/";
        assertEquals(expected, result);
    }

    @Test
    public void shouldNotExpandTildesInTheMiddle() {
        String result = expander.expand("foo-~-bar-~-baz");
        String expected = "foo-~-bar-~-baz";
        assertEquals(expected, result);
    }

    @Test
    public void shouldExpandFileProtocol() {
        String result = expander.expand("file:~/foo/bar");
        String expected = "file:" + System.getProperty("user.home") + "/foo/bar";
        assertEquals(expected, result);
    }

    @Test
    public void shouldExpandJarFileProtocol() {
        String result = expander.expand("jar:file:~/foo/bar");
        String expected = "jar:file:" + System.getProperty("user.home") + "/foo/bar";
        assertEquals(expected, result);
    }

}
