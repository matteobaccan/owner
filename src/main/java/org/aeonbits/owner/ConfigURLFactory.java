/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author Luigi R. Viggiano
 */
class ConfigURLFactory implements Serializable {
    private static final String CLASSPATH_PROTOCOL = "classpath:";
    private transient final ClassLoader classLoader;
    private final VariablesExpander expander;

    ConfigURLFactory(ClassLoader classLoader, VariablesExpander expander) {
        this.classLoader = classLoader;
        this.expander = expander;
    }

    URL newURL(String spec) throws MalformedURLException {
        String expanded = expand(spec);
        URL url;
        if (expanded.startsWith(CLASSPATH_PROTOCOL)) {
            String path = expanded.substring(CLASSPATH_PROTOCOL.length());
            url = classLoader.getResource(path);
            if (url == null)
                return null;
        } else {
            url = new URL(expanded);
        }
        return new URL(url.getProtocol(), url.getHost(), url.getPort(), expand(url.getPath()));
    }

    private String expand(String path) {
        return expander.expand(path);
    }

    String toClasspathURLSpec(String name) {
        return CLASSPATH_PROTOCOL + name.replace('.', '/');
    }
}
