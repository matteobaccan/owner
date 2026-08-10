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
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static org.aeonbits.owner.util.Util.system;
import static org.aeonbits.owner.util.Util.unsupported;

/**
 * A {@link Loader loader} able to read a <code>.env</code> file, the format that container tooling has made the
 * commonest way to carry configuration into a process.
 *
 * <p>
 * The file is a list of <code>NAME=value</code> lines. Blank lines are skipped and a line whose first
 * non-blank character is <code>#</code> is a comment. Everything beyond that — whether quotes delimit a value
 * or belong to it, whether <code>\n</code> means a newline, whether a comment may follow a value on the same
 * line — differs from one tool to the next, because <code>.env</code> has no specification. Those differences
 * are described by an {@link EnvDialect}, and the default is {@link EnvDialect#DOCKER}, which does nothing at
 * all to a value.
 * </p>
 *
 * <p>The dialect can be chosen in two ways. Registering the loader sets it for a factory:</p>
 * <pre>
 *     ConfigFactory.registerLoader(new DotEnvLoader(EnvDialect.DOTENV));
 * </pre>
 * <p>and an option on the source sets it for that file alone, which is finer:</p>
 * <pre>
 *     &#64;Sources("file:.env#dialect=dotenv")
 * </pre>
 * <p>
 * The options of a source live in the fragment, for every loader and every scheme - see
 * {@link SourceOptions}. They may also adjust one rule at a time, over the dialect or over the default:
 * <code>quotes=strip|literal</code>, <code>escapes=expand|literal</code>, <code>export=strip|keep</code>,
 * <code>comments=inline|none</code>, <code>multiline=allow|deny</code>,
 * <code>continuation=allow|deny</code> and <code>bare=env|ignore|error</code>, separated by
 * <code>&amp;</code>. An option or a setting that is not one of these is refused rather than ignored.
 * </p>
 *
 * <p>
 * This loader accepts any source whose path ends in <code>.env</code>, which covers both a file that is all
 * extension and one such as <code>local.env</code>. It contributes nothing to the sources that are looked for
 * when an interface carries no {@code @Sources}: a <code>.env</code> is seldom on the classpath and is never
 * named after the configuration interface, so it is always named explicitly.
 * </p>
 *
 * <p>
 * Under a dialect that keeps quotes — the default — a value written <code>NAME="Matteo"</code> almost
 * certainly came from a file meant for <code>dotenv</code>. Reading it verbatim is right but silent, so one
 * <code>WARNING</code> per file is written through {@link java.util.logging}, which is part of the JDK: OWNER
 * adds no logging dependency. Choosing the dialect that fits the file is the real cure; failing that, the
 * message is silenced with <code>org.aeonbits.owner.loaders.DotEnvLoader.level = OFF</code> in a
 * <code>logging.properties</code>, or with
 * <code>Logger.getLogger(DotEnvLoader.class.getName()).setLevel(Level.OFF)</code>. Logger names are a
 * hierarchy, so <code>org.aeonbits.owner.level = OFF</code> quietens the whole library.
 * </p>
 *
 * @author Matteo Baccan
 * @see EnvDialect
 * @since 2.0.0
 */
public class DotEnvLoader implements Loader {

    private static final long serialVersionUID = 4384174863943518945L;

    private static final String SUFFIX = ".env";
    private static final String EXPORT = "export";
    /** Written as an escape on purpose: the character itself is invisible, so a mangled file would look right. */
    private static final char BYTE_ORDER_MARK = '\uFEFF';
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_.\\-]*");

    private final EnvDialect dialect;

    /** Creates a loader reading in the {@link EnvDialect#DOCKER} dialect. */
    public DotEnvLoader() {
        this(EnvDialect.DOCKER);
    }

    /**
     * Creates a loader reading in the given dialect, which a query on a source can still override.
     *
     * @param dialect the dialect to read in.
     */
    public DotEnvLoader(EnvDialect dialect) {
        if (dialect == null)
            throw new IllegalArgumentException("dialect can't be null");
        this.dialect = dialect;
    }

    @Override
    public boolean accept(URI uri) {
        return SourceOptions.hasExtension(uri, SUFFIX);
    }

    @Override
    public void load(Properties result, URI uri) throws IOException {
        EnvDialect effective = dialectFor(uri);
        // the URI is opened as it stands: URL.getFile() excludes the fragment, so there is nothing to strip,
        // and a query - which only a remote source can carry - reaches the server it belongs to.
        // the three are declared separately rather than nested: closing the outermost would be enough for the
        // stream underneath, but a constructor that failed halfway would leave what it had already wrapped
        // open, and this way each is closed whatever happens to the one after it
        try (InputStream input = uri.toURL().openStream();
             InputStreamReader characters = new InputStreamReader(input, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(characters)) {
            new Parser(readLines(reader), effective, uri, result).run();
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Always <code>null</code>: a <code>.env</code> file is not named after the configuration interface, so
     * there is no useful name to guess, and returning nothing keeps this loader from adding a lookup to every
     * configuration that is loaded.
     * </p>
     */
    @Override
    public String defaultSpecFor(String uriPrefix) {
        return null;
    }

    // ---------------------------------------------------------------- the options on the source

    private EnvDialect dialectFor(URI uri) {
        SourceOptions options = SourceOptions.of(uri);
        if (options.isEmpty())
            return dialect;

        options.refuseUnknown("dialect", "quotes", "escapes", "export", "comments", "multiline",
                "continuation", "bare");
        EnvDialect result = baseDialect(options);
        for (SourceOptions.Option option : options.all())
            if (!"dialect".equals(option.name()))
                result = apply(result, option.name(), option.setting().toLowerCase(), uri);
        return result;
    }

    /** Read before everything else, so that {@code dialect} sets the starting point wherever it appears. */
    private EnvDialect baseDialect(SourceOptions options) {
        for (SourceOptions.Option option : options.all())
            if ("dialect".equals(option.name()))
                return EnvDialect.named(option.setting());
        return dialect;
    }

    /**
     * Applies one rule over a dialect. Every name reaching here is one of the eight, since
     * {@link SourceOptions#refuseUnknown(String...)} has already turned the others away - which is why the
     * last of them is the fall-through rather than an unreachable error.
     */
    private static EnvDialect apply(EnvDialect target, String option, String setting, URI uri) {
        if ("quotes".equals(option))
            return target.withQuotesStripped(flag(option, setting, "strip", "literal", uri));
        if ("escapes".equals(option))
            return target.withEscapesExpanded(flag(option, setting, "expand", "literal", uri));
        if ("export".equals(option))
            return target.withExportPrefixStripped(flag(option, setting, "strip", "keep", uri));
        if ("comments".equals(option))
            return target.withInlineComments(flag(option, setting, "inline", "none", uri));
        if ("multiline".equals(option))
            return target.withMultilineValues(flag(option, setting, "allow", "deny", uri));
        if ("continuation".equals(option))
            return target.withLineContinuation(flag(option, setting, "allow", "deny", uri));
        return target.withBareNames(bareNames(setting, uri));
    }

    private static boolean flag(String option, String setting, String whenTrue, String whenFalse, URI uri) {
        if (whenTrue.equals(setting)) return true;
        if (whenFalse.equals(setting)) return false;
        throw unsupported("'%s' is not a setting for the .env option '%s' in %s; use '%s' or '%s'",
                setting, option, uri, whenTrue, whenFalse);
    }

    private static EnvDialect.BareNames bareNames(String setting, URI uri) {
        if ("env".equals(setting)) return EnvDialect.BareNames.FROM_ENVIRONMENT;
        if ("ignore".equals(setting)) return EnvDialect.BareNames.IGNORE;
        if ("error".equals(setting)) return EnvDialect.BareNames.ERROR;
        throw unsupported("'%s' is not a setting for the .env option 'bare' in %s; use 'env', 'ignore' or "
                + "'error'", setting, uri);
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
        private final EnvDialect dialect;
        private final URI uri;
        private final Properties result;
        private final List<String> quotedButKeptVerbatim = new ArrayList<>();
        private int index;

        Parser(List<String> lines, EnvDialect dialect, URI uri, Properties result) {
            this.lines = lines;
            this.dialect = dialect;
            this.uri = uri;
            this.result = result;
        }

        void run() {
            while (index < lines.size()) {
                int lineNumber = index + 1;
                String line = leftTrim(nextLogicalLine());
                if (line.isEmpty() || line.charAt(0) == '#')
                    continue;
                if (dialect.isExportPrefixStripped())
                    line = leftTrim(withoutExportPrefix(line));

                int equals = line.indexOf('=');
                if (equals < 0) {
                    bareName(line, lineNumber);
                    continue;
                }

                String key = line.substring(0, equals).trim();
                if (key.isEmpty())
                    throw unsupported("Line %d of %s assigns to an empty name: '%s'", lineNumber, uri, line);
                result.setProperty(key, value(key, rawValue(line.substring(equals + 1), key, lineNumber)));
            }
            reportQuotedValues();
        }

        /** Joins the lines a trailing backslash holds together, when the dialect allows it. */
        private String nextLogicalLine() {
            String line = lines.get(index++);
            if (!dialect.isLineContinuation())
                return line;
            while (endsWithBackslash(line) && index < lines.size())
                line = line.substring(0, line.length() - 1) + lines.get(index++);
            return line;
        }

        /**
         * The text of the value as it stands in the file, with any trailing comment removed and any further
         * lines a quote holds open appended. Quoting is not undone here: that is {@link #value}.
         */
        private String rawValue(String text, String key, int lineNumber) {
            String candidate = withoutComment(text);
            if (!dialect.isQuotesStripped() || !dialect.isMultilineValues())
                return candidate;

            String opening = candidate.trim();
            if (opening.isEmpty())
                return candidate;
            char quote = opening.charAt(0);
            if (quote != '"' && quote != '\'' || closesWith(opening, quote))
                return candidate;

            StringBuilder joined = new StringBuilder(text);
            while (index < lines.size()) {
                joined.append('\n').append(lines.get(index++));
                candidate = withoutComment(joined.toString());
                if (closesWith(candidate.trim(), quote))
                    return candidate;
            }
            throw unsupported("The value of '%s' at line %d of %s opens a %s quote that is never closed",
                    key, lineNumber, uri, quote == '"' ? "double" : "single");
        }

        private String value(String key, String raw) {
            if (!dialect.isQuotesStripped()) {
                // whatever follows the '=' is the value, quotes included: this is the point of the dialect,
                // and the one case where it surprises people is worth a word afterwards
                if (looksQuoted(raw.trim()))
                    quotedButKeptVerbatim.add(key);
                return raw;
            }
            String trimmed = raw.trim();
            if (!isQuoted(trimmed))
                return trimmed;
            char quote = trimmed.charAt(0);
            String inner = trimmed.substring(1, trimmed.length() - 1);
            return quote == '"' && dialect.isEscapesExpanded() ? expandEscapes(inner) : inner;
        }

        private void bareName(String name, int lineNumber) {
            if (dialect.bareNames() == EnvDialect.BareNames.IGNORE)
                return;
            if (!NAME.matcher(name).matches())
                throw unsupported("Line %d of %s is neither a comment nor an assignment: '%s'",
                        lineNumber, uri, name);
            if (dialect.bareNames() == EnvDialect.BareNames.ERROR)
                throw unsupported("Line %d of %s names '%s' without assigning a value to it",
                        lineNumber, uri, name);
            String fromEnvironment = system().getenv().get(name);
            if (fromEnvironment != null)
                result.setProperty(name, fromEnvironment);
        }

        private String withoutComment(String text) {
            return dialect.isInlineComments() ? cutComment(text) : text;
        }

        private void reportQuotedValues() {
            if (quotedButKeptVerbatim.isEmpty())
                return;
            Logger.getLogger(DotEnvLoader.class.getName()).warning(String.format(
                    "%d value(s) in %s are wrapped in matching quotes but were read with the quotes included, "
                            + "because the '%s' dialect treats them as part of the value (the first is '%s'). "
                            + "If they are meant to delimit, add '#dialect=dotenv' to the source or register "
                            + "new DotEnvLoader(EnvDialect.DOTENV).",
                    quotedButKeptVerbatim.size(), uri, dialect.name(), quotedButKeptVerbatim.get(0)));
        }
    }

    // ---------------------------------------------------------------- text

    private static String leftTrim(String text) {
        int start = 0;
        while (start < text.length() && Character.isWhitespace(text.charAt(start)))
            start++;
        return start == 0 ? text : text.substring(start);
    }

    private static String withoutExportPrefix(String line) {
        if (!line.startsWith(EXPORT) || line.length() <= EXPORT.length())
            return line;
        return Character.isWhitespace(line.charAt(EXPORT.length()))
                ? line.substring(EXPORT.length() + 1)
                : line;
    }

    private static boolean endsWithBackslash(String line) {
        return trailingBackslashes(line, line.length()) % 2 == 1;
    }

    /** Counts the backslashes running back from {@code end}, which is what says whether the one before it escapes. */
    private static int trailingBackslashes(String text, int end) {
        int count = 0;
        for (int i = end - 1; i >= 0 && text.charAt(i) == '\\'; i--)
            count++;
        return count;
    }

    /** True when the text both opens and closes with {@code quote}, the closing one not being escaped. */
    private static boolean closesWith(String text, char quote) {
        if (text.length() < 2 || text.charAt(text.length() - 1) != quote)
            return false;
        // nothing is escaped inside single quotes, in any of the dialects
        return quote == '\'' || trailingBackslashes(text, text.length() - 1) % 2 == 0;
    }

    private static boolean looksQuoted(String text) {
        return text.length() >= 2
                && (text.charAt(0) == '"' || text.charAt(0) == '\'')
                && text.charAt(text.length() - 1) == text.charAt(0);
    }

    private static boolean isQuoted(String text) {
        return text.length() >= 2
                && (text.charAt(0) == '"' || text.charAt(0) == '\'')
                && closesWith(text, text.charAt(0));
    }

    /**
     * Cuts a trailing comment: a <code>#</code> that is outside quotes and preceded by whitespace. Requiring
     * the whitespace is what lets a value such as <code>abc#123</code> keep its hash, and it is the rule both
     * Compose and the recent dotenv releases settled on.
     */
    private static String cutComment(String text) {
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && inDoubleQuotes)
                i++;
            else if (c == '\'' && !inDoubleQuotes)
                inSingleQuotes = !inSingleQuotes;
            else if (c == '"' && !inSingleQuotes)
                inDoubleQuotes = !inDoubleQuotes;
            else if (c == '#' && !inSingleQuotes && !inDoubleQuotes
                    && i > 0 && Character.isWhitespace(text.charAt(i - 1)))
                return text.substring(0, i);
        }
        return text;
    }

    /** An escape that is not one of these is left exactly as it was written, backslash included. */
    private static String expandEscapes(String text) {
        if (text.indexOf('\\') < 0)
            return text;
        StringBuilder expanded = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '\\' || i == text.length() - 1) {
                expanded.append(c);
                continue;
            }
            char escaped = text.charAt(++i);
            switch (escaped) {
                case 'n': expanded.append('\n'); break;
                case 'r': expanded.append('\r'); break;
                case 't': expanded.append('\t'); break;
                case 'f': expanded.append('\f'); break;
                case 'b': expanded.append('\b'); break;
                case '"': expanded.append('"'); break;
                case '\'': expanded.append('\''); break;
                case '\\': expanded.append('\\'); break;
                default: expanded.append('\\').append(escaped);
            }
        }
        return expanded.toString();
    }
}
