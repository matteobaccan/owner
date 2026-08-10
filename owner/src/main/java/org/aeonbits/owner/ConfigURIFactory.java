/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
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
            // the fragment carries the options of the source and is not part of the resource name: handed to
            // getResource as it stands, '#dialect=dotenv' would be looked for as part of the file name, the
            // lookup would find nothing, and the source would be dropped without a word
            String rest = fixed.substring(CLASSPATH_PROTOCOL.length());
            int hash = rest.indexOf('#');
            String path = hash < 0 ? rest : rest.substring(0, hash);
            URL url = classLoader.getResource(path);
            if (url == null)
                return null;
            URI resolved = url.toURI();
            return hash < 0 ? resolved : new URI(resolved.toString() + rest.substring(hash));
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
