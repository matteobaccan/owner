/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.formats.toml;

import org.aeonbits.owner.loaders.Loader;
import org.aeonbits.owner.loaders.SourceOptions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * A {@link Loader loader} for <a href="https://toml.io/en/v1.0.0">TOML v1.0.0</a>, reading a source whose
 * path ends in <code>.toml</code>.
 *
 * <pre>
 *     [server]
 *     host = "localhost"
 *     port = 8080
 *
 *     [[servers]]
 *     host = "alpha"
 *
 *     [[servers]]
 *     host = "beta"
 * </pre>
 * <pre>
 *     server.host=localhost
 *     server.port=8080
 *     servers[0].host=alpha
 *     servers[1].host=beta
 * </pre>
 *
 * <p>
 * <b>TOML is the format this library's flattening convention was already shaped like.</b> An
 * <code>[[array of tables]]</code> <i>is</i> <code>servers[0].host</code>, a dotted key <i>is</i> the
 * flattening, and a <code>[table]</code> is a prefix — so nothing had to be adapted, and a TOML document is
 * read by the same nested interfaces, indexed lists and grouped maps as anything else.
 * </p>
 *
 * <h2>Held to the conformance suite</h2>
 *
 * <p>
 * YAML ships here as a documented subset because a complete YAML implementation is out of reach without a
 * dependency. TOML is held to a different standard, its specification being a document rather than an
 * implementation and <a href="https://github.com/toml-lang/toml-test">toml-test</a> a conformance suite
 * anyone can run — which is the reason this format is written here rather than delegated.
 * </p>
 *
 * <p>
 * <b>Every one of the 499 documents that suite says to refuse is refused, and 204 of the 210 it says to
 * read are read as it expects.</b> The six are not a to-do list: they are an empty key and a dot inside a
 * quoted key, both of which the flattening convention cannot name and neither of which an interface could
 * declare a method for. Closing them would be a change to a convention chosen for the whole library rather
 * than to this parser. See {@code TomlConformanceTest}, which runs the whole suite in every build with the
 * score as a ratchet.
 * </p>
 *
 * <h2>Values are kept as written, with one exception that is a rule</h2>
 *
 * <p>
 * Everywhere else in this library the text is handed over exactly: our JSON reader answers
 * <code>1e3</code> for <code>1e3</code>, because that is JSON's only way of writing the number and keeping
 * it loses nothing. <b>TOML offers several spellings of one value</b>, and those are canonicalised:
 * </p>
 *
 * <ul>
 *   <li><code>1_000</code>, <code>0xDEADBEEF</code>, <code>0o755</code> and <code>0b1101</code> are four
 *       ways of writing an integer, and the conversion chain reads none of them, so all four become plain
 *       decimal;</li>
 *   <li><code>inf</code>, <code>+inf</code>, <code>-inf</code> and <code>nan</code> become Java's
 *       <code>Infinity</code>, <code>-Infinity</code> and <code>NaN</code>;</li>
 *   <li>the space TOML allows in place of the <code>T</code> of a date-time becomes a <code>T</code>, which
 *       is the ISO form {@link java.time.LocalDateTime#parse} wants.</li>
 * </ul>
 *
 * <p>
 * Strings are untouched, being the value rather than a spelling of it, and so are ordinary decimals. The
 * four date-time types need no code of their own: since 2.0.0 the conversion chain builds a type with its
 * static <code>parse</code> factory, so <code>LocalDate</code>, <code>LocalTime</code>,
 * <code>LocalDateTime</code> and <code>OffsetDateTime</code> are read from a TOML document with nothing
 * registered.
 * </p>
 *
 * <p>
 * <b>A key written twice is refused</b>, as it is for JSON and as TOML itself requires — a table defined
 * twice, a table that is already a value, an inline table something tries to extend, a header reopening a
 * path a dotted key already created. The opposite of HOCON, which merges, and deliberately so: TOML says a
 * repetition is a mistake, and so do we.
 * </p>
 *
 * @author Matteo Baccan
 * @since 2.0.0
 */
public class TomlLoader implements Loader {
    /**
     * A loader is built with no arguments, both when it is registered by hand and when
     * {@link java.util.ServiceLoader} finds it on the class path. Declared rather than left implicit
     * so that the requirement is visible to whoever changes this class.
     */
    public TomlLoader() {
    }


    private static final long serialVersionUID = 8814431395297615185L;

    /** The one place the name of the format is written: {@code accept} and the default spec both read it. */
    private static final String SUFFIX = ".toml";

    @Override
    public boolean accept(URI uri) {
        return SourceOptions.hasExtension(uri, SUFFIX);
    }

    @Override
    public void load(Properties result, URI uri) throws IOException {
        SourceOptions.of(uri).refuseUnknown();
        try (InputStream input = uri.toURL().openStream()) {
            new TomlParser(read(input), result).parse();
        }
    }

    /**
     * The whole document, as text.
     *
     * <p>
     * <b>UTF-8, and not the platform encoding</b>: TOML v1.0.0 says a document is a valid UTF-8 encoded
     * Unicode document, which leaves nothing to guess. A byte order mark is dropped, since a file written
     * by a Windows editor may carry one and it is not part of the document.
     * </p>
     *
     * <p>
     * <b>And strictly, which {@code new String(bytes, UTF_8)} is not.</b> That constructor replaces a
     * malformed byte with U+FFFD and reads on, so a truncated or mis-encoded file would come back as a
     * configuration with a replacement character sitting in a value — the format says the document is UTF-8,
     * so a document that is not is refused rather than repaired. It is the same rule the rest of this
     * parser follows: refuse what cannot be understood instead of half-reading it.
     * </p>
     */
    private static String read(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int count = input.read(buffer); count > 0; count = input.read(buffer))
            bytes.write(buffer, 0, count);

        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        String text;
        try {
            text = decoder.decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
        } catch (CharacterCodingException notUtf8) {
            throw new IOException("the document is not valid UTF-8, which TOML requires: "
                    + notUtf8.getMessage(), notUtf8);
        }
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    @Override
    public String defaultSpecFor(String urlPrefix) {
        return urlPrefix + SUFFIX;
    }
}
