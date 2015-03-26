/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.loaders;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Properties;

/**
 * A {@link Loader loader} able to read properties from standard Java properties files.
 *
 * @since 1.0.5
 * @author Luigi R. Viggiano
 */
public class PropertiesLoader implements Loader {

    private static final long serialVersionUID = -1781643040589572341L;
    private static final String DEFAULT_ENCODING = "UTF-8";

    public boolean accept(URI uri) {
        try {
            uri.toURL();
            return true;
        } catch (MalformedURLException ex) {
            return false;
        }
    }

    public void load(Properties result, URI uri) throws IOException {
        URL url = uri.toURL();
        InputStream input = url.openStream();
        try {
            load(result, input);
        } finally {
            input.close();
        }
    }

    void load(Properties result, InputStream input) throws IOException {
        result.load(new InputStreamReader(input, DEFAULT_ENCODING));
    }

    public String defaultSpecFor(String uriPrefix) {
        return uriPrefix + ".properties";
    }

}
