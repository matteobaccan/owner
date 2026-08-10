/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.loaders;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.aeonbits.owner.util.Util.unsupported;

/**
 * The options written on a single source, and the one place every {@link Loader} reads them from.
 * <p>
 * The rule is the same for every loader and every scheme: <b>the query belongs to the protocol and the
 * fragment belongs to OWNER</b>.
 * </p>
 * <pre>
 *     &#64;Sources("file:.env#dialect=dotenv&amp;quotes=strip")
 *     &#64;Sources("https://config/app.env?token=abc#dialect=dotenv")
 * </pre>
 * <p>
 * The second line is why it is the fragment rather than the query. A query means something to the server
 * that answers an <code>http:</code> source, so a loader cannot claim it without breaking that source; a
 * fragment is never sent to a server at all. It also survives where a query does not: a resource inside a
 * jar resolves to an opaque URI such as <code>jar:file:/a.jar!/app.env</code>, where
 * {@link URI#getQuery()} returns <code>null</code> while the fragment is still read.
 * </p>
 * <p>
 * The practical consequence is that a loader has nothing to strip. {@link java.net.URL#getFile()} excludes
 * the fragment, so both {@link Loader#accept(URI) accept} and {@code openStream()} see the plain resource,
 * and the options are read beside them rather than cut out of them.
 * </p>
 * <p>
 * <b>An option that is not recognised is refused</b>, never ignored - see {@link #refuseUnknown(String...)}.
 * A misspelt option that passes in silence is a configuration that is wrong and says nothing, which is the
 * failure this class exists to prevent.
 * </p>
 *
 * @author Matteo Baccan
 * @since 2.0.0
 */
public final class SourceOptions {

    private static final SourceOptions NONE = new SourceOptions(null, Collections.<Option>emptyList());

    private final URI uri;
    private final List<Option> options;

    private SourceOptions(URI uri, List<Option> options) {
        this.uri = uri;
        this.options = options;
    }

    /**
     * The part of a source to match an extension against: no query, no fragment, and no assumption that the
     * URI is hierarchical.
     * <p>
     * {@link URI#getPath()} cannot be used for this. <code>file:.env</code> - the form the documentation
     * uses, since the path is relative - is an <b>opaque</b> URI whose path is <code>null</code>, and so is
     * every resource resolved inside a jar. The text is what both have in common.
     * </p>
     *
     * @param uri the source, may be <code>null</code>.
     * @return the source without its query or fragment; an empty string when the URI is <code>null</code>.
     */
    public static String path(URI uri) {
        if (uri == null) return "";
        String spec = uri.toString();
        // the fragment first: it comes last in a URI and may legally contain a '?', so cutting the query
        // first would truncate a source whose options happen to contain one
        int hash = spec.indexOf('#');
        if (hash >= 0) spec = spec.substring(0, hash);
        int mark = spec.indexOf('?');
        if (mark >= 0) spec = spec.substring(0, mark);
        return spec;
    }

    /**
     * Whether a source ends in one of the given extensions, which is what most {@link Loader#accept(URI)}
     * implementations amount to.
     * <p>
     * Built on {@link #path(URI)}, so it is not fooled by a query, by the options in the fragment, or by an
     * opaque URI - the three things a hand-written <code>endsWith</code> gets wrong, and did. The comparison
     * ignores case, because a file called <code>CONFIG.XML</code> is an XML file.
     * </p>
     * <p>
     * A loader keeps the list in one constant and uses it here and in
     * {@link Loader#defaultSpecsFor(String)}, so that the names a format goes by are written once.
     * </p>
     *
     * @param uri        the source, may be <code>null</code>.
     * @param extensions the extensions to match, dot included.
     * @return whether the source ends in one of them.
     */
    public static boolean hasExtension(URI uri, String... extensions) {
        String path = path(uri).toLowerCase();
        for (String extension : extensions)
            if (path.endsWith(extension.toLowerCase()))
                return true;
        return false;
    }

    /**
     * Reads the options written on a source.
     *
     * @param uri the source, may be <code>null</code>.
     * @return the options, in the order they were written; never <code>null</code>.
     * @throws UnsupportedOperationException if the fragment holds something that is not a list of
     *         <code>name=setting</code> pairs, or if the source carries a query on a scheme where a query
     *         cannot mean anything. Unchecked on purpose: an <code>IOException</code> here would be caught
     *         by {@link org.aeonbits.owner.Config.LoadType} and the source dropped in silence.
     */
    public static SourceOptions of(URI uri) {
        if (uri == null) return NONE;
        refuseMeaninglessQuery(uri);
        String fragment = uri.getRawFragment();
        if (fragment == null || fragment.isEmpty()) return new SourceOptions(uri, Collections.<Option>emptyList());
        return new SourceOptions(uri, parse(fragment, uri));
    }

    /** Whether the source carries no option at all. */
    public boolean isEmpty() {
        return options.isEmpty();
    }

    /**
     * The options, in the order they were written, with repetitions kept: a loader that lets a later option
     * refine an earlier one needs to see both, and in which order.
     *
     * @return the options; never <code>null</code>.
     */
    public List<Option> all() {
        return options;
    }

    /**
     * Refuses every option whose name is not in the given list.
     * <p>
     * The message names the offending option, the source it was written on, and <b>the options that would
     * have been accepted</b>. That last part is not politeness: the day a loader starts refusing, the people
     * it refuses first are the ones whose configuration was already wrong and silently doing nothing, and
     * they need to be told what to write instead rather than only what not to.
     * </p>
     *
     * @param known the option names this loader understands; none at all means the loader takes no options.
     * @throws UnsupportedOperationException if any option is not among them.
     */
    public void refuseUnknown(String... known) {
        for (Option option : options)
            if (!isKnown(option.name(), known))
                throw unknown(option.name(), known);
    }

    private static boolean isKnown(String name, String... known) {
        for (String candidate : known)
            if (candidate.equals(name))
                return true;
        return false;
    }

    private UnsupportedOperationException unknown(String name, String... known) {
        if (known.length == 0)
            return unsupported("'%s' in the fragment of %s is not an option: this source takes none", name, uri);
        return unsupported("'%s' in the fragment of %s is not an option; the options are %s",
                name, uri, list(known));
    }

    private static String list(String... known) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < known.length; i++) {
            if (i > 0) text.append(i == known.length - 1 ? " and " : ", ");
            text.append(known[i]);
        }
        return text.toString();
    }

    private static List<Option> parse(String fragment, URI uri) {
        List<Option> parsed = new ArrayList<>();
        for (String pair : fragment.split("&")) {
            if (pair.isEmpty()) continue;
            int equals = pair.indexOf('=');
            if (equals < 1)
                throw unsupported("'%s' in the fragment of %s is not an option=setting pair", pair, uri);
            parsed.add(new Option(pair.substring(0, equals).trim().toLowerCase(),
                    pair.substring(equals + 1).trim()));
        }
        return Collections.unmodifiableList(parsed);
    }

    /**
     * Refuses a query on a scheme that has no use for one.
     * <p>
     * An <code>http:</code> source may well carry a query and it belongs to the server, untouched. A
     * <code>file:</code> or <code>jar:</code> source cannot: {@link java.net.URL#getFile()} includes the
     * query, so the handler goes looking for a file whose name ends in <code>?dialect=dotenv</code>, does
     * not find it, and the source disappears without a word. It is nearly always somebody writing the
     * options where they used to be, so the message says where they live now.
     * </p>
     */
    private static void refuseMeaninglessQuery(URI uri) {
        String scheme = uri.getScheme();
        if (!"file".equals(scheme) && !"jar".equals(scheme)) return;
        if (!hasQuery(uri)) return;
        throw unsupported("a query has no meaning on a '%s:' source, and %s would be looked for under that "
                + "name; the options of a source go in the fragment, as in file:.env#dialect=dotenv",
                scheme, uri);
    }

    /**
     * Read from the text rather than from {@link URI#getQuery()}, which returns <code>null</code> on the
     * opaque URIs both of these schemes produce - <code>file:.env</code> among them.
     */
    private static boolean hasQuery(URI uri) {
        String spec = uri.toString();
        int hash = spec.indexOf('#');
        String beforeFragment = hash < 0 ? spec : spec.substring(0, hash);
        return beforeFragment.indexOf('?') >= 0;
    }

    /**
     * One <code>name=setting</code> pair written on a source.
     */
    public static final class Option {

        private final String name;
        private final String setting;

        Option(String name, String setting) {
            this.name = name;
            this.setting = setting;
        }

        /** The option name, trimmed and lower case, since a name is a keyword. */
        public String name() {
            return name;
        }

        /**
         * The setting, trimmed and <b>otherwise exactly as written</b>: no case folding and no percent
         * decoding. Decoding would have to happen before the pairs are split, so a <code>%26</code> inside a
         * setting would turn into a separator and silently split one option into two. A loader whose setting
         * is a keyword folds the case itself.
         */
        public String setting() {
            return setting;
        }

        @Override
        public String toString() {
            return name + "=" + setting;
        }
    }
}
