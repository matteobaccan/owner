/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.loaders.SourceOptions;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.regex.Pattern;

import static org.aeonbits.owner.util.Util.fixBackslashesToSlashes;
import static org.aeonbits.owner.util.Util.fixSpacesToPercentTwenty;
import static org.aeonbits.owner.util.Util.unsupported;

/**
 * @author Luigi R. Viggiano
 */
class ConfigURIFactory {

    private static final String CLASSPATH_PROTOCOL = "classpath:";
    private static final String FILE_PROTOCOL = "file:";
    private final transient ClassLoader classLoader;
    private final VariablesExpander expander;

    ConfigURIFactory(ClassLoader classLoader, VariablesExpander expander) {
        this.classLoader = classLoader;
        this.expander = expander;
    }

    /**
     * A URL-style scheme, and <b>two characters at least</b>: <code>C:/app/config.properties</code> would
     * otherwise be a source with the scheme <code>C</code>, which is a Windows drive and a thing people
     * write.
     */
    private static final Pattern SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]+:");

    /** What the pattern above is careful to exclude, so that it can be answered for by name. */
    private static final Pattern WINDOWS_DRIVE = Pattern.compile("^[a-zA-Z]:/");

    URI newURI(String spec) throws URISyntaxException {
        return newURI(spec, null);
    }

    /**
     * Turns a spec into the source it names, resolving it against another source when it names no scheme.
     * <p>
     * <b>The rule is Spring Boot's</b>, and the words are theirs: a location carrying a URL-style prefix is
     * <i>fixed</i> and resolves to the same resource wherever it was written; anything else is
     * <i>relative</i> and resolves against the source that named it. It chains, each source resolving
     * against itself rather than against the first one, which is what makes a tree of files movable as a
     * tree. C has had the same two-tier rule since 1972 — <code>#include "next door"</code> against
     * <code>#include &lt;on the search path&gt;</code>.
     * </p>
     * <p>
     * <b>Resolved through {@link URL} and not {@link URI#resolve}</b>, which is not a preference: a
     * <code>jar:</code> URI is <i>opaque</i>, so {@code URI.resolve} hands the relative reference straight
     * back unchanged and the source is then looked for under a name with no scheme at all - a wrong answer
     * arrived at in silence. {@link URL} resolves it, the JDK's <code>jar:</code> handler understanding the
     * part after the <code>!</code>, so a file inside a jar names its neighbours the way any other file
     * does. Spring reaches for the same constructor for the same reason.
     * </p>
     * <p>
     * A leading <code>/</code> therefore means <b>the root of wherever the declaring source lives</b>: the
     * filesystem for a file, the jar for an entry in a jar, the server for an http source. That is what a
     * URL has always meant by it and it is the useful reading.
     * </p>
     *
     * @param spec       the source as it was written.
     * @param relativeTo the source that named it, or <code>null</code> where there is none - which is every
     *                   {@link Config.Sources} entry, an annotation being written in no file.
     * @return the source, or <code>null</code> when there is no such classpath resource.
     * @throws URISyntaxException if the spec does not read as a URI.
     */
    URI newURI(String spec, URI relativeTo) throws URISyntaxException {
        String expanded = expand(spec);
        String fixed = fixBackslashesToSlashes(expanded);
        if (relativeTo != null && !SCHEME.matcher(fixed).find())
            return resolveAgainst(relativeTo, fixed, spec);
        if (fixed.startsWith(CLASSPATH_PROTOCOL)) {
            // the fragment carries the options of the source and is not part of the resource name: handed to
            // getResource as it stands, '#dialect=dotenv' would be looked for as part of the file name, the
            // lookup would find nothing, and the source would be dropped without a word
            String rest = fixed.substring(CLASSPATH_PROTOCOL.length());
            int hash = rest.indexOf('#');
            String path = hash < 0 ? rest : rest.substring(0, hash);
            URL url = classLoader.getResource(path);
            if (url == null) {
                // the one place a source disappears before any loader sees it, so the one place where a
                // source that said it has to be there can still be answered for
                if (hash >= 0 && requiredIn(rest.substring(hash)))
                    throw unsupported("The source '%s' says it is required and there is no such resource on "
                            + "the classpath", spec);
                return null;
            }
            URI resolved = url.toURI();
            return hash < 0 ? resolved : new URI(resolved.toString() + rest.substring(hash));
        } else if (fixed.startsWith(FILE_PROTOCOL)) {
            // This check fixes the case where an environment variable has been
            // specified for the path to the config file, but that environment
            // variable is blank / undefined.
            if ( fixed.equals(FILE_PROTOCOL) ) {
                return new URI("");
            } else {
                String path = fixSpacesToPercentTwenty(fixed);
                return new URI(path);
            }
        } else {
            return new URI(fixed);
        }
    }

    /**
     * Resolves a spec that names no scheme against the source that named it.
     * <p>
     * The spaces are the same fix a <code>file:</code> spec gets, and for the same reason: a space is not a
     * URI character, and a path with one in it is ordinary on both desktop platforms. <b>The
     * <code>#</code> is deliberately left alone</b>, where Spring escapes it — here the fragment is how a
     * source carries its options, so <code>base.properties#required=true</code> has to keep meaning what it
     * says.
     * </p>
     */
    private static URI resolveAgainst(URI relativeTo, String fixed, String spec) throws URISyntaxException {
        if (WINDOWS_DRIVE.matcher(fixed).find())
            throw unsupported("The source '%s' looks like a Windows path with a drive letter, and a drive "
                    + "letter is not a URL scheme. Write it as a source: 'file:/%s'", spec, fixed);

        try {
            return new URL(relativeTo.toURL(), fixSpacesToPercentTwenty(fixed)).toURI();
        } catch (MalformedURLException | IllegalArgumentException cannotBeResolved) {
            throw unsupported(cannotBeResolved,
                    "The source '%s' names no scheme, so it is looked for beside the source that named it - "
                            + "and %s is not something another source can be found beside. Name the scheme, "
                            + "as in 'file:%s' or 'classpath:%s'",
                    spec, relativeTo, fixed, fixed);
        }
    }

    /**
     * Asks the option parser about a fragment on its own, there being no resource to attach it to: what is
     * handed over is a URI made of a placeholder and that fragment, since the fragment is the whole of the
     * question and every other part of a URI would be invented.
     */
    private static boolean requiredIn(String fragment) {
        try {
            return SourceOptions.isRequired(new URI("owner:source" + fragment));
        } catch (URISyntaxException notAFragmentWeCanRead) {
            return false;
        }
    }

    private String expand(String path) {
        return expander.expand(path);
    }

    String toClasspathURLSpec(String name) {
        return CLASSPATH_PROTOCOL + name.replace('.', '/');
    }

}
