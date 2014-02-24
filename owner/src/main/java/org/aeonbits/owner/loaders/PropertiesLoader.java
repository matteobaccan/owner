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

    public boolean accept(URI uri) {
		try {
			uri.toURL();
			return true;
		} catch (MalformedURLException e) {
			return false;
		}
    }

    public void load(Properties result, InputStream input) throws IOException {
        result.load(input);
    }

    public String defaultSpecFor(String urlPrefix) {
        return urlPrefix + ".properties";
    }

}
