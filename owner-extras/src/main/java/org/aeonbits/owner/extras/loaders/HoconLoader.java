/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.extras.loaders;

import org.aeonbits.owner.loaders.Loader;
import org.aeonbits.owner.loaders.SourceOptions;

import java.io.IOException;
import java.net.URI;
import java.util.Properties;

import static org.aeonbits.owner.util.Util.hideCredentials;
import static org.aeonbits.owner.util.Util.unsupported;

/**
 * A {@link Loader loader} for <a href="https://github.com/lightbend/config/blob/main/HOCON.md">HOCON</a>,
 * reading a source whose path ends in <code>.conf</code>.
 *
 * <pre>
 *     server {
 *       host = localhost
 *       port = 8080
 *     }
 *     servers = [ { host = alpha }, { host = beta } ]
 * </pre>
 * <pre>
 *     server.host=localhost
 *     server.port=8080
 *     servers[0].host=alpha
 *     servers[1].host=beta
 * </pre>
 *
 * <p>
 * The keys are the ones every loader in this library flattens to, so a HOCON document is read by the same
 * nested interfaces, indexed lists and grouped maps as anything else.
 * </p>
 *
 * <h2>Why this one is not written by hand</h2>
 *
 * <p>
 * Every other format this project reads has a parser of its own, written here, with no dependency. HOCON is
 * the exception, and the reason is not its size. <b>HOCON's specification is an implementation</b>: there is
 * one, Lightbend's, and the value of the format is reading the <code>application.conf</code> files people
 * already have. Its substitutions - <code>${foo}</code> and <code>${?foo}</code> - resolve after the whole
 * merge, across files, and may be self-referential; objects with the same key merge rather than replace one
 * another; <code>include</code> pulls in another document mid-parse. A subset that refused all of that would
 * be JSON with comments, which is not why anybody chooses HOCON.
 * </p>
 *
 * <p>
 * And this library already reads <code>${...}</code>, with different semantics and at a different time. A
 * hand-written approximation would therefore not fail on the files it cannot handle - it would read them
 * and quietly mean something else, which is worse than not supporting the format. So this adapts the
 * reference implementation, and what a HOCON document means here is what it means everywhere.
 * </p>
 *
 * <h2>What it costs, and to whom</h2>
 *
 * <p>
 * <b>Nothing, unless a <code>.conf</code> source is read.</b> <code>com.typesafe:config</code> is an
 * optional dependency of <code>owner-extras</code>: it is not transitive, this project does not ship it,
 * and nobody receives it by depending on OWNER. Nothing in this class names it - everything that does lives
 * in {@link HoconReader}, which is not touched until a document is actually read - so this loader is
 * discovered and instantiated on a class path without it, like any other, and only a configuration naming a
 * <code>.conf</code> source is told, by name and with what to add, that the dependency is missing. It
 * contributes <code>.conf</code> to the file names tried when a configuration declares no
 * <code>&#64;Sources</code>, and probing for a file that is not there costs no more than any other probe.
 * See {@link HoconReader} for why that separation must not be undone.
 * </p>
 *
 * <h2>Two things to know about the values</h2>
 *
 * <p>
 * <b>A value comes back as the reference implementation understood it, not as it was written.</b> Elsewhere
 * in this library the text is kept exactly - our JSON reader answers <code>1e3</code> for <code>1e3</code> -
 * because those parsers hand over the characters. Typesafe Config parses eagerly into typed values and does
 * not keep the original text, so <code>1e3</code> arrives here as <code>1000</code> and <code>1.50</code> as
 * <code>1.5</code>. Nothing a converter needs is lost, and a string, a duration like <code>10s</code> or a
 * size like <code>512K</code> is untouched, those being strings in HOCON's own model. It is stated because
 * it cannot be seen.
 * </p>
 *
 * <p>
 * <b>A <code>null</code> writes no key at all</b>, as it does for JSON: a {@link Properties} cannot hold
 * one, and HOCON's own reading API treats a null as missing. So a <code>&#64;DefaultValue</code> wins over a
 * <code>null</code> written on purpose. Leave the key out of the document when that matters.
 * </p>
 *
 * @author Matteo Baccan
 * @since 2.0.0
 */
public class HoconLoader implements Loader {
    /**
     * A loader is built with no arguments, both when it is registered by hand and when
     * {@link java.util.ServiceLoader} finds it on the class path. Declared rather than left implicit
     * so that the requirement is visible to whoever changes this class.
     */
    public HoconLoader() {
    }


    private static final long serialVersionUID = 3072951166418537331L;

    private static final String EXTENSION = ".conf";

    @Override
    public boolean accept(URI uri) {
        return SourceOptions.hasExtension(uri, EXTENSION);
    }

    @Override
    public void load(Properties result, URI uri) throws IOException {
        SourceOptions.of(uri).refuseUnknown();
        try {
            HoconReader.read(result, uri);
        } catch (NoClassDefFoundError typesafeConfigIsNotOnTheClassPath) {
            // said here rather than left to propagate: a NoClassDefFoundError names the class that could
            // not be found, which is Typesafe Config's, and not the thing to do about it
            throw unsupported(typesafeConfigIsNotOnTheClassPath,
                    "Reading %s needs HOCON support, which owner-extras declares as an optional dependency "
                            + "and does not bring along. Add com.typesafe:config to read a .conf source.",
                    hideCredentials(uri));
        }
    }

    @Override
    public String defaultSpecFor(String uriPrefix) {
        return uriPrefix + EXTENSION;
    }
}
