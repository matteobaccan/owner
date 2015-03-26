/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.loaders;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.Properties;

/**
 * Defines the interface of a generic Properties loader.
 *
 * @author Luigi R. Viggiano
 * @since 1.0.5
 */
public interface Loader extends Serializable {

    /**
     * Indicates wether this Loader does accept the URL, guessing the content type from it.
     *
     * @since 1.1.0
     * @param uri   the URI
     * @return true, if the loader is able to handle the content of the URI.
     */
    boolean accept(URI uri);

    /**
     * Loads the given {@link URI uri} into the given {@link Properties result}
     *
     * @since 1.1.0
     * @param result    the resulting properties where to load the {@link URI uri}
     * @param uri     the {@link URI} from where to load the properties.
     * @throws java.io.IOException if there is some I/O error during the load.
     */
    void load(Properties result, URI uri) throws IOException;

    /**
     * Returns the default URI specification for a given url resource, that can be handled by this loader.
     *
     * @param uriPrefix the prefix identifying the url resource.
     * @return the default URI specification for a given uri resource, that can be handled by this loader.
     */
    String defaultSpecFor(String uriPrefix);
}
