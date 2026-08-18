/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.formats.json;

import org.aeonbits.owner.loaders.Loader;
import org.aeonbits.owner.loaders.SourceOptions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * A {@link Loader loader} for JSON, reading a source whose path ends in <code>.json</code>.
 *
 * <pre>
 *     {
 *       "server": { "host": "localhost", "port": 8080 },
 *       "servers": [ { "host": "alpha" }, { "host": "beta" } ]
 *     }
 * </pre>
 * <pre>
 *     server.host=localhost
 *     server.port=8080
 *     servers[0].host=alpha
 *     servers[1].host=beta
 * </pre>
 *
 * <p>
 * The keys are the ones every loader in this library flattens to, so what a JSON document produces is read
 * by the same nested interfaces, indexed lists and grouped maps as anything else. Nothing here is specific
 * to JSON except the parsing.
 * </p>
 *
 * <p>
 * <b>A value is kept as it was written.</b> A number is not read into a <code>double</code> and printed
 * back — <code>1e3</code> stays <code>1e3</code> — because every value in this library is text until a
 * converter is asked for a type, and the conversion is the caller's to declare.
 * </p>
 *
 * <p>
 * <b>A <code>null</code> writes nothing at all</b>, and the key is simply not there. A
 * {@link java.util.Properties} cannot hold a null, so this is the only faithful reading available: the
 * alternative, an empty value, would give <code>""</code> two meanings and is the choice Spring has been
 * unable to move away from for a decade. Typesafe Config arrives at the same place from the other side —
 * a null exists in its object model and the reading API treats it as missing.
 * </p>
 * <p>
 * The cost has to be stated because it cannot be seen: no method signature in this library can tell
 * <i>absent</i> from <i>explicitly null</i>, both being <code>null</code> or an empty {@link
 * java.util.Optional}. So a <code>@DefaultValue</code> wins over a <code>null</code> written on purpose,
 * which is not what the author of <code>{"proxy": null}</code> meant. Write the key out of the document
 * when that matters, or give the method no default.
 * </p>
 *
 * <p>
 * <b>An empty array is an empty value</b>, <code>servers=</code>, which the library already reads as an
 * empty collection — a faithful reading, and one that overrides a default, as the document says. An empty
 * object writes nothing: a section with nothing in it has nothing to say.
 * </p>
 *
 * <p>
 * <b>A repeated name is refused.</b> RFC 8259 leaves that case undefined and most parsers keep the last
 * value, which is reading half of an ambiguous document instead of saying it is ambiguous. JSON has a real
 * way to write a list, so a repeated name is a mistake rather than a shorthand — which is why the answer
 * differs from the one INI and XML give to a repeated key, where the repetition <b>is</b> the list.
 * </p>
 *
 * @author Matteo Baccan
 * @since 2.0.0
 */
public class JsonLoader implements Loader {
    /**
     * A loader is built with no arguments, both when it is registered by hand and when
     * {@link java.util.ServiceLoader} finds it on the class path. Declared rather than left implicit
     * so that the requirement is visible to whoever changes this class.
     */
    public JsonLoader() {
    }


    private static final long serialVersionUID = 6142058516080633156L;

    /** The one place the name of the format is written: {@code accept} and the default spec both read it. */
    private static final String SUFFIX = ".json";

    @Override
    public boolean accept(URI uri) {
        return SourceOptions.hasExtension(uri, SUFFIX);
    }

    @Override
    public void load(Properties result, URI uri) throws IOException {
        SourceOptions.of(uri).refuseUnknown();
        try (InputStream input = uri.toURL().openStream()) {
            new JsonParser(read(input), result).parse();
        }
    }

    /**
     * The whole document, as text.
     * <p>
     * <b>UTF-8, and not the platform encoding</b>: RFC 8259 says a JSON text exchanged between systems is
     * encoded in UTF-8, and a configuration file is exactly that. A byte order mark is dropped, since a
     * file written by a Windows editor may carry one and it is not part of the document.
     * </p>
     */
    private static String read(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int count = input.read(buffer); count > 0; count = input.read(buffer))
            bytes.write(buffer, 0, count);

        String text = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    @Override
    public String defaultSpecFor(String urlPrefix) {
        return urlPrefix + SUFFIX;
    }
}
