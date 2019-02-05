/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
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

import static org.junit.Assert.assertNotNull;

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
}
