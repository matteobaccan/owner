/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.loaders.SourceOptions;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import static org.aeonbits.owner.util.Util.fixBackslashesToSlashes;
import static org.aeonbits.owner.util.Util.fixSpacesToPercentTwenty;
import static org.aeonbits.owner.util.Util.unsupported;

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
            if (url == null) {
                // the one place a source disappears before any loader sees it, so the one place where a
                // source that said it has to be there can still be answered for
                if (hash >= 0 && requiredIn(rest.substring(hash)))
                    throw unsupported("The source '%s' says it is required and there is no such resource on "
                            + "the classpath", spec);
                return null;
            }
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

    /**
     * Asks the option parser about a fragment on its own, there being no resource to attach it to: what is
     * handed over is a URI made of a placeholder and that fragment, since the fragment is the whole of the
     * question and every other part of a URI would be invented.
     */
    private static boolean requiredIn(String fragment) {
        try {
            return SourceOptions.isRequired(new URI("owner:source" + fragment));
        } catch (URISyntaxException notAFragmentWeCanRead) {
            return false;
        }
    }

    private String expand(String path) {
        return expander.expand(path);
    }

    String toClasspathURLSpec(String name) {
        return CLASSPATH_PROTOCOL + name.replace('.', '/');
    }

}
