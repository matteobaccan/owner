/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.formats.yaml;

import org.aeonbits.owner.loaders.Loader;
import org.aeonbits.owner.loaders.SourceOptions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * A {@link Loader loader} for YAML, reading a source whose path ends in <code>.yaml</code> or
 * <code>.yml</code>.
 *
 * <pre>
 *     server:
 *       host: localhost
 *       port: 8080
 *     servers:
 *       - host: alpha
 *       - host: beta
 *     ports: [80, 443]
 * </pre>
 * <pre>
 *     server.host=localhost
 *     server.port=8080
 *     servers[0].host=alpha
 *     servers[1].host=beta
 *     ports[0]=80
 *     ports[1]=443
 * </pre>
 *
 * <p>
 * The keys are the ones every loader here flattens to, so a YAML document is read by the same nested
 * interfaces, indexed lists and grouped maps as anything else.
 * </p>
 *
 * <h2>It is a subset, and this is the whole of it</h2>
 *
 * <p>
 * Read: block mappings and sequences nested by indentation, a mapping opened on the same line as its dash,
 * plain and quoted scalars, the block scalars <code>|</code> and <code>&gt;</code> with their chomping
 * indicators, flow collections, comments, and a leading <code>---</code>.
 * </p>
 * <p>
 * <b>Refused by name, with the line they are on</b> — never guessed at, because guessing changes what a
 * file means rather than declining to read it:
 * </p>
 * <ul>
 *     <li>anchors, aliases and merge keys — <code>&amp;name</code>, <code>*name</code>, <code>&lt;&lt;:</code></li>
 *     <li>tags — <code>!!str</code>, <code>!Ref</code></li>
 *     <li>complex keys — <code>? </code></li>
 *     <li>a value continued on the next line without <code>|</code> or <code>&gt;</code></li>
 *     <li>a second document in the same file</li>
 *     <li>a tab used as indentation, which YAML forbids and which no two editors agree about</li>
 * </ul>
 *
 * <h2>Types are not guessed</h2>
 *
 * <p>
 * A scalar is kept exactly as it was written, and what it means is decided by the method that reads it.
 * That is what spares this parser YAML's implicit type resolution — the <i>Norway problem</i>, where
 * <code>no</code> becomes <code>false</code> and the country code of Norway stops being a string — and it
 * is also the honest arrangement: the interface is where the types are declared, so the interface is where
 * they should be decided.
 * </p>
 * <p>
 * The consequence is worth stating: <code>enabled: yes</code> is the text <code>yes</code>, and a method
 * returning <code>boolean</code> will read it through the ordinary conversion rather than through YAML
 * 1.1's table. Write <code>true</code> when a boolean is meant.
 * </p>
 *
 * <h2>What an absent value produces</h2>
 *
 * <p>
 * <code>host:</code> with nothing after it, <code>~</code> and <code>null</code> are the same thing and
 * <b>write no key at all</b>, as in JSON and for the same reason: a {@link Properties} cannot hold a null.
 * An empty flow sequence writes an empty value, which is already read as an empty collection; an empty
 * mapping writes nothing, a section with nothing in it having nothing to say.
 * </p>
 *
 * @author Matteo Baccan
 * @since 2.0.0
 */
public class YamlLoader implements Loader {
    /**
     * A loader is built with no arguments, both when it is registered by hand and when
     * {@link java.util.ServiceLoader} finds it on the class path. Declared rather than left implicit
     * so that the requirement is visible to whoever changes this class.
     */
    public YamlLoader() {
    }


    private static final long serialVersionUID = 8261671300253685517L;

    /** The names the format goes by; {@code accept} and the default specs both read them. */
    private static final String[] SUFFIXES = {".yaml", ".yml"};

    @Override
    public boolean accept(URI uri) {
        return SourceOptions.hasExtension(uri, SUFFIXES);
    }

    @Override
    public void load(Properties result, URI uri) throws IOException {
        SourceOptions.of(uri).refuseUnknown();
        try (InputStream input = uri.toURL().openStream()) {
            YamlParser parser = new YamlParser(read(input), result);
            parser.refuseTabs();
            parser.parse();
        }
    }

    /**
     * The whole document, as text. <b>UTF-8</b>, which the YAML specification requires of every stream,
     * and without the byte order mark an editor may have left at the front.
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
        return urlPrefix + SUFFIXES[0];
    }

    @Override
    public String[] defaultSpecsFor(String urlPrefix) {
        String[] specs = new String[SUFFIXES.length];
        for (int i = 0; i < SUFFIXES.length; i++)
            specs[i] = urlPrefix + SUFFIXES[i];
        return specs;
    }
}
