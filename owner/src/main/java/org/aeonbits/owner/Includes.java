/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.loaders.SourceOptions;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static java.util.Collections.emptyList;
import static org.aeonbits.owner.util.Util.unsupported;

/**
 * Reads the sources, following the file that says which other file it builds on.
 * <p>
 * The list of sources a configuration has was closed until now: {@link Config.Sources} named them, or the
 * conventional probe found them, and nothing read could add to it. A file carrying the directive
 * <code>owner.include</code> adds to it, which is what
 * <a href="https://github.com/matteobaccan/owner/issues/165">#165</a> asked for — the dependency between
 * files decided <b>in the files</b> and not in the code, so that a deployment wanting one more file does
 * not need recompiling.
 * </p>
 * <p>
 * This class is built for one load and thrown away: it holds what is needed to read a source and to turn a
 * spec into a URI, and it is the only place that knows an include from a declared source. It is not kept
 * as a field of anything, so it needs no serialised form — {@link PropertiesManager} keeps the
 * {@linkplain #token() token} instead, which is a string.
 * </p>
 *
 * <h2>Where the directive is recognised</h2>
 * <p>
 * At the <b>root of the file and nowhere else</b>: the key has to be exactly the token. Every loader in
 * this project hands back a flat {@link Properties}, a nested format having been flattened with dots on
 * the way, so <code>owner.include</code> written inside a section arrives here as
 * <code>section.owner.include</code> and is somebody's property rather than a directive. That is the rule
 * a nested format needs and the one a properties file gets for free.
 * </p>
 * <p>
 * <b>Where in the file it is written does not matter and cannot matter.</b> By the time it is read the
 * file is a map: {@code Properties} is a {@code Hashtable}, and the line order the author saw is gone
 * before this class is given anything. Asking for the directive at the top would be a rule nothing here
 * could enforce, and enforcing it would mean parsing every format a second time to find out which line a
 * key was on. The convention is worth writing in the documentation and worth nothing in the code.
 * </p>
 *
 * @author Matteo Baccan
 */
final class Includes {

    /**
     * The {@link Factory} property naming the token: <code>owner.include.key</code>. The empty value turns
     * the feature off, and that second use is what makes the first defensible — whoever already has a
     * legitimate property called <code>owner.include</code> must be able to say <i>not here</i>, or this
     * release would change what files that already exist mean.
     * <p>
     * A {@link Factory} property and never a directive inside the file: a directive that redefines itself
     * is a maze. {@link StrSubstitutor#NESTED_VARIABLE_EXPANSION} is the one precedent in this project for
     * a switch over a parsing rule, and it exists for the same reason.
     * </p>
     */
    static final String INCLUDE_KEY = "owner.include.key";

    /**
     * The token as it is when nobody says otherwise: <code>owner.include</code>.
     * <p>
     * Not Commons Configuration's bare <code>include</code>, which is a plausible key for somebody's own
     * configuration. This one cannot collide by accident and sits beside {@link PropertiesManager#STRICT}
     * and {@link PropertiesManager#DECLARED_ONLY}.
     * </p>
     */
    static final String DEFAULT_TOKEN = "owner.include";

    /** What separates one source from the next inside the directive. */
    private static final String SEPARATOR = ",";

    private final String token;
    private final ConfigURIFactory uriFactory;
    private final LoadersManager loaders;
    private final PropertiesManager report;

    /** The sources reached through the directive, in the order they were scheduled. */
    private final List<URI> included = new ArrayList<>();

    /** Every source that answered, declared or included, in the order it was read. */
    private final List<URI> readInOrder = new ArrayList<>();

    Includes(String token, ConfigURIFactory uriFactory, LoadersManager loaders, PropertiesManager report) {
        this.token = token == null ? DEFAULT_TOKEN : token.trim();
        this.uriFactory = uriFactory;
        this.loaders = loaders;
        this.report = report;
    }

    /**
     * Reads the {@link Factory} property naming the token, the default standing in when it is not set.
     *
     * @param props the properties the factory was configured with.
     * @return the token, possibly empty, which means the feature is off.
     */
    static String tokenIn(Properties props) {
        String declared = props.getProperty(INCLUDE_KEY);
        return declared == null ? DEFAULT_TOKEN : declared.trim();
    }

    /** The key a file has to write to name the files it builds on; empty when the feature is off. */
    String token() {
        return token;
    }

    /** Whether the directive is read at all. */
    boolean isOff() {
        return token.isEmpty();
    }

    /**
     * The sources reached through the directive during the last {@link #readAll}, in the order they were
     * scheduled. Empty when no file named one, which is every configuration written before this release.
     * <p>
     * {@link PropertiesManager} needs them because the list of sources is no longer decided once: with
     * includes a file can add one or stop naming one, and what {@link Config.HotReload} watches has to be
     * worked out again after every load.
     * </p>
     *
     * @return the included sources, in scheduling order.
     */
    List<URI> included() {
        return Collections.unmodifiableList(included);
    }

    /**
     * Every source that answered, in the order it was read — which is the order they take precedence in,
     * the first prevailing. It is what a person is owed when the list of sources is no longer written
     * anywhere they can look: {@link Config.Sources} names the declared ones and the files name the rest.
     *
     * @return the sources read, in precedence order.
     */
    List<URI> readInOrder() {
        return Collections.unmodifiableList(readInOrder);
    }

    /**
     * Reads the sources, following what each of them includes.
     * <p>
     * The declared sources are walked in the order they were declared, and the sources each one includes
     * are read <b>immediately after it, depth first</b>: <code>b</code> includes <code>a</code>,
     * <code>a</code> includes <code>z</code>, and the answer is <code>b, a, z</code>. The caller merges
     * that list backwards, so the file that includes wins over the file it includes — the child
     * specialises the template, which is what <i>inheritance</i> means and what
     * {@link Config.LoadType#MERGE} already says about the order sources are declared in.
     * </p>
     * <p>
     * A source already scheduled is <b>skipped</b>, however many times it is named. Re-reading a file
     * yields the same values, so overwriting would change nothing about the content; what it would change
     * is the position of that file in the precedence order — a file nobody edited moving because a third
     * file named it again. A cycle terminates on the same rule, with nothing to report and nothing to
     * explain. (A circular <i>variable</i> reference still throws: a file named twice is not a mistake, a
     * value that resolves to itself is.)
     * </p>
     *
     * @param declared                  the sources as declared, in the order they were declared.
     * @param stopAtTheFirstThatAnswers {@link Config.LoadType#FIRST}: the sources after the one that
     *                                  answered are never read, but the ones <b>it</b> includes are — what
     *                                  a file includes is part of what that file means.
     * @return what was read, in the order it was read, each source with the properties it held.
     */
    List<Source> readAll(List<URI> declared, boolean stopAtTheFirstThatAnswers) {
        List<Source> result = new ArrayList<>();
        Set<URI> scheduled = new LinkedHashSet<>(declared);
        for (URI uri : declared) {
            Properties loaded = readDeclared(uri);
            if (loaded == null)
                continue;
            record(result, uri, loaded);
            follow(loaded, uri, result, scheduled);
            if (stopAtTheFirstThatAnswers)
                break;
        }
        return result;
    }

    /**
     * Schedules what a file just read includes, and then what those include.
     * <p>
     * The directive is <b>taken out</b> of the properties before they go anywhere: it is a directive and
     * not a property, so it appears in no view — not in {@code propertyNames()}, not in {@code store()},
     * not in {@code toString()}, and {@code originOf} is never asked about it. The one place it survives
     * is the file itself, which {@code save(File)} keeps as a line it does not own.
     * </p>
     */
    private void follow(Properties loaded, URI declaredIn, List<Source> result, Set<URI> scheduled) {
        for (String spec : takeSpecsFrom(loaded)) {
            URI uri = resolve(spec, declaredIn);
            if (uri == null || !scheduled.add(uri))
                continue;
            included.add(uri);
            Properties p = readIncluded(spec, uri);
            if (p == null)
                continue;
            record(result, uri, p);
            follow(p, uri, result, scheduled);
        }
    }

    /** Keeps a source that answered, and the place it took in the order. */
    private void record(List<Source> result, URI uri, Properties loaded) {
        result.add(new Source(uri, loaded));
        readInOrder.add(uri);
    }

    /**
     * Removes the directive from what was just loaded and answers with the sources it names.
     * <p>
     * With the feature off nothing is removed and nothing is named: the key stays exactly what it was
     * before this release, an ordinary property.
     * </p>
     */
    private List<String> takeSpecsFrom(Properties loaded) {
        if (isOff())
            return emptyList();

        Object value = loaded.remove(token);
        if (!(value instanceof String)) {
            reportADirectiveWrittenTwice(loaded);
            return emptyList();
        }

        List<String> specs = new ArrayList<>();
        for (String spec : ((String) value).split(SEPARATOR)) {
            String trimmed = spec.trim();
            if (!trimmed.isEmpty())
                specs.add(trimmed);
        }
        return specs;
    }

    /**
     * Says so when the directive was written more than once in a format that turns a repeated key into a
     * list, which is the one way of getting this wrong that leaves nothing at all behind.
     * <p>
     * The three families of format answer a repeated key in three different ways, and this was measured
     * rather than assumed. A properties file and a <code>.env</code> keep the <b>last</b> line and lose the
     * first, silently — nothing here can see that, the map holding one entry either way, and it is a thing
     * to write in the documentation rather than a thing to detect. JSON, YAML and TOML <b>refuse the whole
     * document</b>, naming the key and the line, so the loudest answer needs no help from us. An INI file
     * turns it into <code>owner.include[0]</code> and <code>owner.include[1]</code>, which is that loader's
     * convention for a repeated key — and then there is no key called the token at all, so the directive
     * simply does not happen. That is the one that would be invisible.
     * </p>
     * <p>
     * It is <b>not</b> read as a list of sources, though it easily could be. The directive would then have
     * two spellings, one of which exists only in one format, and the same two lines would mean two sources
     * in an INI file and one in a properties file. One spelling: one line, sources separated by commas.
     * </p>
     */
    private void reportADirectiveWrittenTwice(Properties loaded) {
        if (!loaded.containsKey(token + "[0]"))
            return;

        List<String> named = new ArrayList<>();
        for (int i = 0; loaded.containsKey(token + "[" + i + "]"); i++)
            named.add(String.valueOf(loaded.remove(token + "[" + i + "]")));
        report.directiveWrittenTwice(token, named);
    }

    /**
     * Turns a spec into the source it names, <b>the same grammar {@link Config.Sources} entries have</b>
     * and expanded by the same {@link VariablesExpander} — so a spec may name a variable the factory set.
     * <p>
     * With one thing an annotation has no use for: a spec that names <b>no scheme</b> is looked for
     * <b>beside the source that named it</b>, which is the whole of what a relative path can mean here.
     * {@link Config.Sources} does not get that form and could not — an annotation is written in no file, so
     * there is nothing for it to be beside. See {@link ConfigURIFactory#newURI(String, URI)} for the rule
     * and where it comes from.
     * </p>
     *
     * @param spec       the source as it was written in the file.
     * @param declaredIn the source that wrote it, which a relative spec is resolved against.
     * @return the source, or <code>null</code> when there is no such resource, which has been reported.
     */
    private URI resolve(String spec, URI declaredIn) {
        try {
            URI uri = uriFactory.newURI(spec, declaredIn);
            if (uri == null)
                report.includeNotRead(spec, null, null);
            return uri;
        } catch (URISyntaxException e) {
            throw unsupported(e, "Can't convert '%s' to a valid URI", spec);
        }
    }

    /** A declared source: absence is how a fallback chain works, so it is answered for as it always was. */
    private Properties readDeclared(URI uri) {
        try {
            Properties loaded = new Properties();
            loaders.load(loaded, uri);
            return loaded;
        } catch (IOException ex) {
            report.sourceFailed(uri, ex);
            return null;
        }
    }

    /**
     * An included source. <b>Not the same case as a declared one</b>: nobody builds a fallback chain out
     * of a file naming another file, so an include that does not arrive is a warning rather than the
     * silence a merely absent {@link Config.LoadType#FIRST} candidate gets.
     */
    private Properties readIncluded(String spec, URI uri) {
        try {
            Properties loaded = new Properties();
            loaders.load(loaded, uri);
            return loaded;
        } catch (IOException ex) {
            report.includeNotRead(spec, uri, ex);
            return null;
        }
    }

    /** Whether an include said for itself that it has to be there: see {@link SourceOptions}. */
    static boolean isRequired(URI uri) {
        return uri != null && SourceOptions.isRequired(uri);
    }

    /** One source that answered, with what it held once the directive was taken out of it. */
    static final class Source {
        private final URI uri;
        private final Properties properties;

        Source(URI uri, Properties properties) {
            this.uri = uri;
            this.properties = properties;
        }

        URI uri() {
            return uri;
        }

        Properties properties() {
            return properties;
        }
    }
}
