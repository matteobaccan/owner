/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.loaders;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;
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
     * @param url   the URL
     * @return true, if the loader is able to handle the content of the URL based on the filename.
     */
    boolean accept(URL url);

    /**
     * Loads the given {@link InputStream input} into the given {@link Properties result}
     *
     * @since 1.0.5
     * @param result    the resulting properties where to load the {@link InputStream input}
     * @param input     the {@link InputStream} from where to load the properties.
     */
    void load(Properties result, InputStream input) throws IOException;
}
