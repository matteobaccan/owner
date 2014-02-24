/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Properties;

import static org.junit.Assert.assertNotNull;

/**
 * @author Luigi R. Viggiano
 */
public class ConfigURLFactoryTest {
    @Test
    public void shouldReturnAnURI() throws MalformedURLException, URISyntaxException {
        ConfigURIFactory h = new ConfigURIFactory(this.getClass().getClassLoader(), new VariablesExpander(new Properties()));
        URI url = h.newURI("classpath:test.properties");
        assertNotNull(url);
    }
}
