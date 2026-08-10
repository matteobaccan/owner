/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
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

    @Override
    public boolean accept(URI uri) {
        // isAbsolute first: toURL throws IllegalArgumentException rather than MalformedURLException for a
        // URI with no scheme, which a spec whose ${…} did not expand produces - and an accept() that throws
        // aborts the search for a loader instead of letting the next one answer
        if (uri == null || !uri.isAbsolute())
            return false;
        try {
            uri.toURL();
            return true;
        } catch (MalformedURLException ex) {
            return false;
        }
    }

    @Override
    public void load(Properties result, URI uri) throws IOException {
        SourceOptions.of(uri).refuseUnknown();
        URL url = uri.toURL();
        try (InputStream input = url.openStream()) {
            load(result, input);
        }
    }

    /**
     * Reads the stream as UTF-8, whoever opened it.
     * <p>
     * The reader is closed here because it is created here: closing it closes the stream underneath as well, and
     * the caller that opened the stream closes it too, which {@link java.io.Closeable#close()} defines as having
     * no effect the second time. The alternative - leaving the reader to the garbage collector because the stream
     * is closed anyway - loses no file descriptor either, but it is the shape that invites a leak the day the
     * reader wraps something with a buffer of its own.
     * </p>
     *
     * @param result the properties where to load the stream.
     * @param input  the stream to read, owned by the caller.
     * @throws IOException if there is some I/O error while reading.
     */
    void load(Properties result, InputStream input) throws IOException {
        try (InputStreamReader characters = new InputStreamReader(input, DEFAULT_ENCODING)) {
            result.load(characters);
        }
    }

    @Override
    public String defaultSpecFor(String uriPrefix) {
        return uriPrefix + ".properties";
    }

}
