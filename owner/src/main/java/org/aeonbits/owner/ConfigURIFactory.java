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

import static org.aeonbits.owner.util.Util.fixBackslashesToSlashes;
import static org.aeonbits.owner.util.Util.fixSpacesToPercentTwenty;

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

    URI newURI(String spec) throws URISyntaxException {
        String expanded = expand(spec);
        String fixed = fixBackslashesToSlashes(expanded);
        if (fixed.startsWith(CLASSPATH_PROTOCOL)) {
            String path = fixed.substring(CLASSPATH_PROTOCOL.length());
            URL url = classLoader.getResource(path);
            if (url == null)
                return null;
            return url.toURI();
        } else if (fixed.startsWith(FILE_PROTOCOL)) {
            // This check fixes the case where an environment variable has been
            // specified for the path to the config file, but that environment
            // variable is blank / undefined.
            if ( fixed.equals(FILE_PROTOCOL) ) {
                return new URI("");
            } else {
                String path = fixSpacesToPercentTwenty(fixed);
                return new URI(path);
            }
        } else {
            return new URI(fixed);
        }
    }

    private String expand(String path) {
        return expander.expand(path);
    }

    String toClasspathURLSpec(String name) {
        return CLASSPATH_PROTOCOL + name.replace('.', '/');
    }

}
