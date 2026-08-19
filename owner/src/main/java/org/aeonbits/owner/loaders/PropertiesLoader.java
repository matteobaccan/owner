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
import java.io.PushbackReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * A {@link Loader loader} able to read properties from standard Java properties files.
 *
 * @since 1.0.5
 * @author Luigi R. Viggiano
 */
public class PropertiesLoader implements Loader {
    /**
     * A loader is built with no arguments, both when it is registered by hand and when
     * {@link java.util.ServiceLoader} finds it on the class path. Declared rather than left implicit
     * so that the requirement is visible to whoever changes this class.
     */
    public PropertiesLoader() {
    }


    private static final long serialVersionUID = -1781643040589572341L;

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
        try (Reader characters = withoutTheByteOrderMark(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            result.load(characters);
        }
    }

    /** Written as an escape on purpose: the character itself is invisible, so a mangled file would look right. */
    private static final char BYTE_ORDER_MARK = '﻿';

    /**
     * The same reader, past a byte order mark if there is one.
     * <p>
     * A UTF-8 BOM is what Notepad, PowerShell's <code>Out-File</code> and Visual Studio put in front of a
     * file they save, and {@link Properties#load(Reader)} has no idea what it is: it reads it as the first
     * character of the first key, so <code>first=one</code> arrives under a name nothing asks for and
     * <b>the property silently takes its default instead</b>. Only the first one, which is what makes it
     * hard to see - the rest of the file is fine.
     * </p>
     * <p>
     * Two things make it worth the four lines. The file this bites is the commonest format this library
     * reads, on the platform where a BOM is the default. And <code>owner.include</code> is written at the
     * top of a file by convention, so a BOM would silently stop a whole included file from being read.
     * {@link IniLoader} has done this since it shipped; this is the same thing, one format over.
     * </p>
     */
    private static Reader withoutTheByteOrderMark(Reader characters) throws IOException {
        PushbackReader reader = new PushbackReader(characters, 1);
        int first = reader.read();
        if (first != -1 && first != BYTE_ORDER_MARK)
            reader.unread(first);
        return reader;
    }

    @Override
    public String defaultSpecFor(String uriPrefix) {
        return uriPrefix + ".properties";
    }

}
