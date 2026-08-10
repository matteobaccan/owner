/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.loaders;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import static org.aeonbits.owner.util.Util.unsupported;

/**
 * A {@link Loader loader} for INI files: sections in square brackets, <code>key = value</code> below them.
 *
 * <pre>
 *     [server]
 *     host = localhost
 *     port = 8080
 * </pre>
 * <pre>
 *     server.host=localhost
 *     server.port=8080
 * </pre>
 *
 * <p>
 * A section becomes the prefix of the keys below it, which needs no convention of its own: it is the same
 * dot {@link PropertyKeys} already uses for nesting, so <code>[a.b]</code> and a nested structure produce
 * the same keys, and keys written before any section have no prefix at all.
 * </p>
 *
 * <p><b>A repeated key is a list</b>, numbered exactly as repeated XML elements are:</p>
 * <pre>
 *     [servers]
 *     host = alpha
 *     host = beta
 * </pre>
 * <pre>
 *     servers.host[0]=alpha
 *     servers.host[1]=beta
 * </pre>
 * <p>
 * A key occurring once keeps its plain key, and only a repeat is numbered - at which point the first is
 * moved to <code>[0]</code>. The asymmetry is the same one the XML loader has and for the same reason: when
 * the first occurrence is read there is no telling whether a second will follow, and numbering everything on
 * the chance of it would rename the keys of every file that has no repeats. {@link IniDialect.Duplicates}
 * has the other answers, the field being divided three ways on this.
 * </p>
 *
 * <p>
 * There is no INI standard, so the rules are an {@link IniDialect}, chosen the same two ways a
 * <code>.env</code> dialect is - by registering the loader, or per source in the fragment:
 * </p>
 * <pre>
 *     &#64;Sources("file:app.ini#dialect=git")
 * </pre>
 * <p>
 * This loader accepts a source whose path ends in <code>.ini</code> or <code>.cfg</code>, and looks for both
 * of those beside the configuration class when no {@code @Sources} is declared.
 * </p>
 *
 * @author Matteo Baccan
 * @see IniDialect
 * @since 2.0.0
 */
public class IniLoader implements Loader {

    private static final long serialVersionUID = 5806483104578471951L;

    private static final String[] SUFFIXES = {".ini", ".cfg"};
    private static final String DEFAULT_SECTION = "DEFAULT";
    /** Written as an escape on purpose: the character itself is invisible, so a mangled file would look right. */
    private static final char BYTE_ORDER_MARK = '﻿';
    /** What Python would interpolate and this library will not. */
    private static final Pattern INTERPOLATION = Pattern.compile("%\\([^)]*\\)s");

    private final IniDialect dialect;

    /** Creates a loader reading in the {@link IniDialect#INI} dialect. */
    public IniLoader() {
        this(IniDialect.INI);
    }

    /**
     * Creates a loader reading in the given dialect, which an option on a source can still override.
     *
     * @param dialect the dialect to read in.
     */
    public IniLoader(IniDialect dialect) {
        if (dialect == null)
            throw new IllegalArgumentException("dialect can't be null");
        this.dialect = dialect;
    }

    @Override
    public boolean accept(URI uri) {
        return SourceOptions.hasExtension(uri, SUFFIXES);
    }

    @Override
    public void load(Properties result, URI uri) throws IOException {
        IniDialect effective = dialectFor(uri);
        try (InputStream input = uri.toURL().openStream();
             InputStreamReader characters = new InputStreamReader(input, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(characters)) {
            new Parser(readLines(reader), effective, uri).run(result);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Both of the names the format goes by, which is what {@link #defaultSpecsFor(String)} exists for.
     * </p>
     */
    @Override
    public String[] defaultSpecsFor(String uriPrefix) {
        return new String[]{uriPrefix + SUFFIXES[0], uriPrefix + SUFFIXES[1]};
    }

    // ---------------------------------------------------------------- the options on the source

    private IniDialect dialectFor(URI uri) {
        SourceOptions options = SourceOptions.of(uri);
        if (options.isEmpty())
            return dialect;

        options.refuseUnknown("dialect", "separator", "duplicates", "keys", "bare", "comments", "quotes",
                "continuation", "subsections", "default", "interpolation");
        IniDialect result = baseDialect(options);
        for (SourceOptions.Option option : options.all())
            if (!"dialect".equals(option.name()))
                result = apply(result, option.name(), option.setting().toLowerCase(), uri);
        return result;
    }

    /** Read before everything else, so that {@code dialect} sets the starting point wherever it appears. */
    private IniDialect baseDialect(SourceOptions options) {
        for (SourceOptions.Option option : options.all())
            if ("dialect".equals(option.name()))
                return IniDialect.named(option.setting());
        return dialect;
    }

    /**
     * Applies one rule over a dialect. Every name reaching here is one of the eleven, since
     * {@link SourceOptions#refuseUnknown(String...)} has already turned the others away - which is why the
     * last of them is the fall-through rather than an unreachable error.
     */
    private static IniDialect apply(IniDialect target, String option, String setting, URI uri) {
        if ("separator".equals(option))
            return target.withSeparator(flag(option, setting, "colon", "equals", uri)
                    ? IniDialect.Separator.EQUALS_OR_COLON : IniDialect.Separator.EQUALS);
        if ("duplicates".equals(option))
            return target.withDuplicates(duplicates(setting, uri));
        if ("keys".equals(option))
            return target.withKeyCase(flag(option, setting, "lower", "literal", uri)
                    ? IniDialect.KeyCase.LOWER : IniDialect.KeyCase.LITERAL);
        if ("bare".equals(option))
            return target.withBareKeys(bareKeys(setting, uri));
        if ("comments".equals(option))
            return target.withInlineComments(flag(option, setting, "inline", "none", uri));
        if ("quotes".equals(option))
            return target.withQuotes(flag(option, setting, "strip", "literal", uri)
                    ? IniDialect.Quotes.DELIMIT : IniDialect.Quotes.LITERAL);
        if ("continuation".equals(option))
            return target.withContinuation(continuation(setting, uri));
        if ("subsections".equals(option))
            return target.withSubsections(flag(option, setting, "allow", "deny", uri));
        if ("default".equals(option))
            return target.withDefaultSectionInherited(flag(option, setting, "inherit", "section", uri));
        return target.withInterpolationRefused(flag(option, setting, "refuse", "literal", uri));
    }

    private static IniDialect.Duplicates duplicates(String setting, URI uri) {
        if ("list".equals(setting)) return IniDialect.Duplicates.LIST;
        if ("error".equals(setting)) return IniDialect.Duplicates.ERROR;
        if ("first".equals(setting)) return IniDialect.Duplicates.FIRST;
        if ("last".equals(setting)) return IniDialect.Duplicates.LAST;
        throw unsupported("'%s' is not a setting for the INI option 'duplicates' in %s; use 'list', 'error', "
                + "'first' or 'last'", setting, uri);
    }

    private static IniDialect.BareKeys bareKeys(String setting, URI uri) {
        if ("error".equals(setting)) return IniDialect.BareKeys.ERROR;
        if ("ignore".equals(setting)) return IniDialect.BareKeys.IGNORE;
        if ("true".equals(setting)) return IniDialect.BareKeys.TRUE;
        throw unsupported("'%s' is not a setting for the INI option 'bare' in %s; use 'error', 'ignore' or "
                + "'true'", setting, uri);
    }

    private static IniDialect.Continuation continuation(String setting, URI uri) {
        if ("none".equals(setting)) return IniDialect.Continuation.NONE;
        if ("backslash".equals(setting)) return IniDialect.Continuation.BACKSLASH;
        if ("indent".equals(setting)) return IniDialect.Continuation.INDENT;
        throw unsupported("'%s' is not a setting for the INI option 'continuation' in %s; use 'none', "
                + "'backslash' or 'indent'", setting, uri);
    }

    private static boolean flag(String option, String setting, String whenTrue, String whenFalse, URI uri) {
        if (whenTrue.equals(setting)) return true;
        if (whenFalse.equals(setting)) return false;
        throw unsupported("'%s' is not a setting for the INI option '%s' in %s; use '%s' or '%s'",
                setting, option, uri, whenTrue, whenFalse);
    }

    private static List<String> readLines(BufferedReader reader) throws IOException {
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null)
            lines.add(line);
        if (!lines.isEmpty() && !lines.get(0).isEmpty() && lines.get(0).charAt(0) == BYTE_ORDER_MARK)
            lines.set(0, lines.get(0).substring(1));
        return lines;
    }

    // ---------------------------------------------------------------- reading the file

    /**
     * Holds the position in the file while it is read, so that the loader itself stays immutable and can be
     * shared between threads and between configurations.
     */
    private static final class Parser {

        private final List<String> lines;
        private final IniDialect dialect;
        private final URI uri;

        /** What has been read, in the order it was read, before it is merged into the caller's properties. */
        private final Map<String, String> read = new LinkedHashMap<>();
        /** How many times each key has been seen, which is what turns the second occurrence into a list. */
        private final Map<String, Integer> seen = new LinkedHashMap<>();
        /** The sections met, so that an inherited DEFAULT knows where to go. */
        private final List<String> sections = new ArrayList<>();
        /** The entries of a DEFAULT section, when it is inherited rather than being a section of its own. */
        private final Map<String, String> inherited = new LinkedHashMap<>();

        private String section = "";
        private String lastKey;
        private int number;

        Parser(List<String> lines, IniDialect dialect, URI uri) {
            this.lines = lines;
            this.dialect = dialect;
            this.uri = uri;
        }

        void run(Properties result) {
            for (String line : joined()) {
                number++;
                readLine(line);
            }
            applyInheritedDefaults();
            result.putAll(read);
        }

        /** Joins the lines a trailing backslash holds together, when the dialect allows it. */
        private List<String> joined() {
            if (dialect.continuation() != IniDialect.Continuation.BACKSLASH)
                return lines;

            List<String> result = new ArrayList<>(lines.size());
            StringBuilder held = null;
            for (String line : lines) {
                boolean continues = line.endsWith("\\");
                String text = continues ? line.substring(0, line.length() - 1) : line;
                if (held == null) {
                    if (continues) held = new StringBuilder(text);
                    else result.add(text);
                } else {
                    held.append(text);
                    if (!continues) {
                        result.add(held.toString());
                        held = null;
                    }
                }
            }
            if (held != null)
                result.add(held.toString());
            return result;
        }

        /**
         * Reads one line. Nothing stops the reading early: a line this loader cannot make sense of is
         * refused there and then, so there is no case where it would carry on with less than the whole file.
         */
        private void readLine(String line) {
            if (isContinuation(line)) {
                append(line.trim());
                return;
            }

            String text = line.trim();
            if (text.isEmpty() || isComment(text))
                return;

            if (text.charAt(0) == '[') {
                openSection(text);
                return;
            }

            assign(text);
        }

        private boolean isContinuation(String line) {
            return dialect.continuation() == IniDialect.Continuation.INDENT
                    && lastKey != null
                    && !line.trim().isEmpty()
                    && Character.isWhitespace(line.charAt(0))
                    && !isComment(line.trim())
                    && line.trim().charAt(0) != '[';
        }

        private static boolean isComment(String text) {
            char first = text.charAt(0);
            return first == '#' || first == ';';
        }

        private void append(String more) {
            read.put(lastKey, read.get(lastKey) + "\n" + more);
        }

        private void openSection(String text) {
            int close = text.lastIndexOf(']');
            if (close < 0)
                throw unsupported("Line %d of %s opens a section and never closes it: %s", number, uri, text);

            String name = text.substring(1, close).trim();
            if (name.isEmpty())
                throw unsupported("Line %d of %s is a section with no name", number, uri);

            section = dialect.isSubsections() ? sectionWithSubsection(name) : name;
            lastKey = null;
            // an inherited DEFAULT is not a section of its own - Python's sections() does not list it either,
            // and listing it here would end with the defaults inheriting into themselves
            if (isInheritedDefault() || sections.contains(section))
                return;
            sections.add(section);
        }

        /**
         * A git section header carries its subsection in quotes: <code>[remote "origin"]</code> is the
         * section <code>remote</code> and the subsection <code>origin</code>, which together make the key
         * prefix <code>remote.origin</code> - the very name <code>git config</code> prints.
         */
        private String sectionWithSubsection(String name) {
            int quote = name.indexOf('"');
            if (quote < 0)
                return name;

            int end = name.lastIndexOf('"');
            if (end == quote)
                throw unsupported("Line %d of %s opens a subsection and never closes the quote: %s",
                        number, uri, name);
            String outer = name.substring(0, quote).trim();
            String inner = name.substring(quote + 1, end);
            return PropertyKeys.child(outer, inner);
        }

        private void assign(String text) {
            int at = separatorIn(text);
            if (at < 0) {
                bare(text);
                return;
            }

            String name = text.substring(0, at).trim();
            if (name.isEmpty())
                throw unsupported("Line %d of %s assigns to an empty name: %s", number, uri, text);

            put(name, value(text.substring(at + 1)));
        }

        private int separatorIn(String text) {
            int equals = text.indexOf('=');
            if (dialect.separator() == IniDialect.Separator.EQUALS)
                return equals;

            int colon = text.indexOf(':');
            if (equals < 0) return colon;
            if (colon < 0) return equals;
            return Math.min(equals, colon);
        }

        private void bare(String text) {
            switch (dialect.bareKeys()) {
                case IGNORE:
                    return;
                case TRUE:
                    put(text, "true");
                    return;
                default:
                    throw unsupported("Line %d of %s is neither a comment nor an assignment: %s",
                            number, uri, text);
            }
        }

        private String value(String raw) {
            String text = dialect.isInlineComments() ? cutComment(raw) : raw;
            text = text.trim();
            if (dialect.quotes() == IniDialect.Quotes.DELIMIT)
                text = unquote(text);
            if (dialect.isInterpolationRefused() && INTERPOLATION.matcher(text).find())
                throw unsupported("Line %d of %s holds '%%(…)s', which the '%s' dialect would interpolate and "
                        + "OWNER does not: variables are expanded after loading, so write ${…} instead. Line: %s",
                        number, uri, dialect.name(), text);
            return text;
        }

        /** A comment character preceded by whitespace and outside quotes ends the value. */
        private static String cutComment(String raw) {
            boolean quoted = false;
            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);
                if (c == '"')
                    quoted = !quoted;
                else if (!quoted && (c == '#' || c == ';') && i > 0 && Character.isWhitespace(raw.charAt(i - 1)))
                    return raw.substring(0, i);
            }
            return raw;
        }

        /**
         * Only a value wrapped in matching double quotes from end to end is unquoted, git's partial quoting -
         * <code>a" b"c</code> - being left as written rather than half understood.
         */
        private String unquote(String text) {
            if (text.length() < 2 || text.charAt(0) != '"' || text.charAt(text.length() - 1) != '"')
                return text;
            return expandEscapes(text.substring(1, text.length() - 1));
        }

        private String expandEscapes(String text) {
            StringBuilder out = new StringBuilder(text.length());
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c != '\\' || i + 1 >= text.length()) {
                    out.append(c);
                    continue;
                }
                char next = text.charAt(++i);
                switch (next) {
                    case 'n': out.append('\n'); break;
                    case 't': out.append('\t'); break;
                    case 'b': out.append('\b'); break;
                    case '"': out.append('"'); break;
                    case '\\': out.append('\\'); break;
                    // anything else is left as written, an unknown escape being likelier a backslash in a
                    // path than a sequence somebody meant
                    default: out.append('\\').append(next); break;
                }
            }
            return out.toString();
        }

        private void put(String name, String value) {
            String key = dialect.keyCase() == IniDialect.KeyCase.LOWER ? name.toLowerCase() : name;

            if (isInheritedDefault()) {
                inherited.put(key, value);
                lastKey = null;
                return;
            }

            String full = PropertyKeys.child(section, key);
            Integer times = seen.get(full);
            if (times == null) {
                read.put(full, value);
                seen.put(full, 1);
                lastKey = full;
                return;
            }

            switch (dialect.duplicates()) {
                case ERROR:
                    throw unsupported("Line %d of %s repeats the key '%s', which the '%s' dialect does not "
                            + "allow", number, uri, full, dialect.name());
                case FIRST:
                    lastKey = null;
                    return;
                case LAST:
                    read.put(full, value);
                    lastKey = full;
                    return;
                default:
                    lastKey = numbered(full, times, value);
                    seen.put(full, times + 1);
            }
        }

        /**
         * The second occurrence is what turns a key into a list, and it takes the first with it: the plain
         * key becomes <code>[0]</code>, exactly as a repeated XML element does.
         */
        private String numbered(String full, int times, String value) {
            if (times == 1)
                read.put(PropertyKeys.element(full, 0), read.remove(full));
            String key = PropertyKeys.element(full, times);
            read.put(key, value);
            return key;
        }

        private boolean isInheritedDefault() {
            return dialect.isDefaultSectionInherited() && DEFAULT_SECTION.equals(section);
        }

        /** Python's DEFAULT section supplies what a section does not say for itself, and never overrides it. */
        private void applyInheritedDefaults() {
            for (String each : sections)
                for (Map.Entry<String, String> entry : inherited.entrySet())
                    read.putIfAbsent(PropertyKeys.child(each, entry.getKey()), entry.getValue());
        }
    }
}
