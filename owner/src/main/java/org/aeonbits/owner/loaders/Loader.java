/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.loaders;

import java.io.InputStream;
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
     * @since 1.0.5
     * @param uri   the URL
     * @return true, if the loader is able to handle the content of the URL based on the filename.
     */
    boolean accept(URI uri);

    /**
     * Loads the given {@link URI uri} into the given {@link Properties result}
     *
     * @since 1.0.5
     * @param result    the resulting properties where to load the {@link InputStream input}
     * @param uri		the {@link URI} from where to load the properties.
     */
    void load(Properties result, URI uri) throws ConfigurationSourceNotFoundException;

    /**
     * Returns the default URL specification for a given uri resource, that can be handled by this loader.
     *
     * @param urlPrefix the prefix identifying the uri resource.
     * @return the default URL specification for a given uri resource, that can be handled by this loader.
     */
    String defaultSpecFor(String urlPrefix);
}
