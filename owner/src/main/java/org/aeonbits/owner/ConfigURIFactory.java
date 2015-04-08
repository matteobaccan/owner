/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * @author Luigi R. Viggiano
 */
class ConfigURIFactory {

    private static final String CLASSPATH_PROTOCOL = "classpath:";
    private final transient ClassLoader classLoader;
    private final VariablesExpander expander;

    ConfigURIFactory(ClassLoader classLoader, VariablesExpander expander) {
        this.classLoader = classLoader;
        this.expander = expander;
    }

    URI newURI(String spec) throws URISyntaxException {
        String expanded = expand(spec);
        if (expanded.startsWith(CLASSPATH_PROTOCOL)) {
            String path = expanded.substring(CLASSPATH_PROTOCOL.length());
            URL url = classLoader.getResource(path);
            if (url == null)
                return null;
            return url.toURI();
        } else {
            return new URI(expanded);
        }
    }

    private String expand(String path) {
        return expander.expand(path);
    }

    String toClasspathURLSpec(String name) {
        return CLASSPATH_PROTOCOL + name.replace('.', '/');
    }

}
