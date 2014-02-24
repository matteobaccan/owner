/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * @author Luigi R. Viggiano
 */
class ConfigURIFactory {

    private static final String CLASSPATH_PROTOCOL = "classpath:";
    private static final String FILE_PROTOCOL = "file:";
    private final transient ClassLoader classLoader;
    private final VariablesExpander expander;

    ConfigURIFactory(ClassLoader classLoader, VariablesExpander expander) {
        this.classLoader = classLoader;
        this.expander = expander;
    }

    URI newURI(String spec) throws MalformedURLException, URISyntaxException {
        String expanded = expand(spec);
        URI uri;
        if (expanded.startsWith(CLASSPATH_PROTOCOL)) {
            String path = expanded.substring(CLASSPATH_PROTOCOL.length());
            URL url = classLoader.getResource(path);
            if (url == null)
                return null;
            uri = url.toURI();
        } else if(expanded.startsWith(FILE_PROTOCOL)) {
        	URL url = new URL(expanded);
        	uri = url.toURI();
        } else {
            uri = new URI(expanded);
        }
        
        return uri;
    }

    private String expand(String path) {
        return expander.expand(path);
    }

    String toClasspathURLSpec(String name) {
        return CLASSPATH_PROTOCOL + name.replace('.', '/');
    }

}
