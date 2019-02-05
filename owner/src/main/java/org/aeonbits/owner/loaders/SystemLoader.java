/*
 * Copyright (c) 2012-2018, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.loaders;

import java.io.IOException;
import java.net.URI;
import java.util.Properties;

import static org.aeonbits.owner.util.Util.system;

/**
 * Allows specifying <code>system:properties</code> and <code>system:env</code> with the <code>@Sources</code> annotation.
 *
 * @author Ting-Kuan Wu
 * @author Luigi R. Viggiano
 * @since 1.0.10
 */
public class SystemLoader implements Loader {
/*
 * This class accesses <code>Util.system()</code> which is package accessible; for this reason this class cannot be moved
 * inside the package loaders.
 *
 * I think this class should be splitted in two separate classes:
 * one for system:properties and one for system:env.
 */

    private static final String SYSTEM_PROPERTIES_URI = "system:properties";
    private static final String ENVIRONMENT_VARIABLES_URI = "system:env";


    public boolean accept(URI uri) {
        String path = uri.toString();
        return SYSTEM_PROPERTIES_URI.equals(path) || ENVIRONMENT_VARIABLES_URI.equals(path);
    }

    public void load(Properties result, URI uri) throws IOException {
        String path = uri.toString();
        if (SYSTEM_PROPERTIES_URI.equals(path))
            result.putAll(system().getProperties());
        if (ENVIRONMENT_VARIABLES_URI.equals(path))
            result.putAll(system().getenv());
    }

    public String defaultSpecFor(String uriPrefix) {
        return null;
    }
}


