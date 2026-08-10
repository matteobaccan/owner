/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Luigi R. Viggiano
 */
public class ConfigURIFactoryTest {
    @Test
    public void shouldReturnAnURI() throws URISyntaxException {
        ConfigURIFactory h = new ConfigURIFactory(this.getClass().getClassLoader(), new VariablesExpander(new Properties()));
        URI uri = h.newURI("classpath:test.properties");
        assertNotNull(uri);
    }

    @Test
    public void shouldReturnAUriWithEmptyFilePath() throws URISyntaxException {
        ConfigURIFactory h = new ConfigURIFactory(this.getClass().getClassLoader(), new VariablesExpander(new Properties()));
        URI uri = h.newURI("file:");
        assertNotNull(uri);
    }

    /**
     * The options of a source are not part of the resource name. Handed to {@code getResource} as they
     * stand, the lookup finds nothing, {@code newURI} returns null and the source is dropped in silence.
     */
    @Test
    public void shouldKeepTheOptionsOfAClasspathSourceAndStillFindIt() throws URISyntaxException {
        ConfigURIFactory h = new ConfigURIFactory(this.getClass().getClassLoader(), new VariablesExpander(new Properties()));
        URI uri = h.newURI("classpath:test.properties#dialect=dotenv");
        assertNotNull("the resource was not found once the fragment was appended", uri);
        assertEquals("dialect=dotenv", uri.getFragment());
        assertTrue(uri.toString(), uri.toString().contains("test.properties"));
    }

    @Test
    public void shouldStillReturnNullWhenAClasspathSourceWithOptionsDoesNotExist() throws URISyntaxException {
        ConfigURIFactory h = new ConfigURIFactory(this.getClass().getClassLoader(), new VariablesExpander(new Properties()));
        assertNull(h.newURI("classpath:there-is-no-such.properties#dialect=dotenv"));
    }
}
