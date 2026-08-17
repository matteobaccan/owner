/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * Writes a properties file, keeping the one that is already there.
 * <p>
 * The division it implements is decided in <code>WRITING.md</code>: <b>the code owns the descriptions,
 * the file owns the arrangement</b>. So the existing file supplies the order of its keys, its blank
 * lines, and every comment except the ones sitting immediately above a key the interface describes —
 * those are ours and are rewritten. Keys the file has and the interface has never heard of go through
 * untouched, because an <code>application.properties</code> is usually read by more than one thing.
 * </p>
 * <p>
 * The escaping is {@link java.util.Properties#store}'s, done here because doing it ourselves is the
 * price of not calling it: a file we wrote differently from how we read it would be worse than no
 * feature at all.
 * </p>
 *
 * @author Matteo Baccan
 * @since 2.0.0
 */
final class PropertiesFileWriter {

    /**
     * The encoding {@link java.util.Properties#load(java.io.InputStream)} reads, which is the only
     * defensible one to write: anything outside it is escaped as <code>&#92;uXXXX</code> instead.
     */
    private static final Charset LATIN_1 = StandardCharsets.ISO_8859_1;

    private final Map<String, String> descriptions;
    private final Map<String, String> headings;
    private final String header;

    PropertiesFileWriter(Map<String, String> descriptions, String header) {
        this(descriptions, Collections.<String, String>emptyMap(), header);
    }

    PropertiesFileWriter(Map<String, String> descriptions, Map<String, String> headings, String header) {
        this.descriptions = descriptions;
        this.headings = headings;
        this.header = header;
    }

    /**
     * A writer that knows what a mapping interface says about itself: the {@link Config.Description} of
     * each method, under the key that method resolves to, and the one on the interface as the header.
     * <p>
     * Shared by {@link Accessible#save(java.io.File)} and by {@link TemplateTool}, so that a file written
     * by the tool and one written by a running configuration cannot drift apart.
     * </p>
     */
    static PropertiesFileWriter describing(Class<? extends Config> clazz, KeyPrefix prefix) {
        Map<String, String> descriptions = new HashMap<>();
        collectDescriptions(clazz, prefix, descriptions);

        // a section is described by the accessor that opens it or by the interface it is of, and what is
        // written of it is a heading over the block of keys underneath - see the javadoc of headingFor
        Map<String, String> headings = new HashMap<>();
        NestedProperties.forEachNested(clazz, prefix, (nested, nestedPrefix) -> {
            collectDescriptions(nested, nestedPrefix, descriptions);
            headingFor(nested, nestedPrefix, descriptions, headings);
        });
        // the header describes the configuration and not one interface of it, so it is taken from wherever
        // in the hierarchy it is written, nearest first - a base interface that describes what the file is
        // for describes it for everything that extends it
        Config.Description onTheInterface = Annotations.findAnnotation(clazz, Config.Description.class);
        return new PropertiesFileWriter(descriptions, headings,
                onTheInterface == null ? null : onTheInterface.value());
    }

    /**
     * What is written above the block of keys of a section: the {@link Config.Description} of the accessor
     * that opens it, or failing that the one on the interface it is of.
     * <p>
     * <b>A section resolves to a path and not to a property</b>, so a description on its accessor has no
     * line of its own to sit above and was, until this was written, collected and then never used - which
     * is how the shape <code>WRITING.md</code> promised, <i>"a heading over the block of keys under its
     * prefix"</i>, came to be designed and not built. It is taken out of the ordinary descriptions here so
     * that it cannot be written twice.
     * </p>
     * <p>
     * The accessor wins over the interface because it is the more specific of the two: the same interface
     * used for <code>primary()</code> and <code>replica()</code> is one type describing itself and two
     * accessors describing what each of them is for.
     * </p>
     */
    private static void headingFor(Class<?> nested, KeyPrefix nestedPrefix,
                                   Map<String, String> descriptions, Map<String, String> headings) {
        String path = nestedPrefix.path();
        String heading = descriptions.remove(path.endsWith(".") ? path.substring(0, path.length() - 1) : path);
        if (heading == null) {
            Config.Description onTheType = nested.getAnnotation(Config.Description.class);
            if (onTheType != null)
                heading = onTheType.value();
        }
        if (heading != null)
            headings.put(path, heading);
    }

    private static void collectDescriptions(Class<?> clazz, KeyPrefix prefix, Map<String, String> into) {
        for (Method method : clazz.getMethods()) {
            Config.Description description = method.getAnnotation(Config.Description.class);
            if (description != null)
                into.put(PropertiesMapper.key(method, prefix), description.value());
        }
    }

    /**
     * Every key the interface owns, which is what tells the writer which lines of a file are ours.
     * <p>
     * <b>The nested interfaces are walked too</b>, under the key that nests them, so a configuration with a
     * <code>server()</code> section owns <code>server.host</code> as much as it owns its own keys. Left at
     * the root - which is how this shipped, and it took a template with a section in it to notice - saving
     * such a configuration wrote the top-level keys and quietly dropped every section, since a key nobody
     * claims is a key the writer leaves to whoever else reads the file.
     * </p>
     * <p>
     * A section whose path the <b>properties</b> decide - an element of a list, a value of a map - has no
     * key to be named by until there is a value under it, so it cannot be part of this set. Its keys are
     * therefore somebody else's as far as the writer is concerned, which is the safe way round: they are
     * kept as they are rather than rewritten or dropped.
     * </p>
     */
    static Set<String> keysOf(Class<? extends Config> clazz, KeyPrefix prefix) {
        Set<String> known = new HashSet<>();
        collectKeys(clazz, prefix, known);
        NestedProperties.forEachNested(clazz, prefix,
                (nested, nestedPrefix) -> collectKeys(nested, nestedPrefix, known));
        return known;
    }

    private static void collectKeys(Class<?> clazz, KeyPrefix prefix, Set<String> into) {
        for (Method method : clazz.getMethods())
            into.add(PropertiesMapper.key(method, prefix));
    }

    /**
     * @param file   the file to rewrite, which may not exist yet
     * @param values what to write, key by key
     * @param known  the keys the interface declares; anything else in the file is somebody else's
     */
    void write(File file, Properties values, Set<String> known) throws IOException {
        List<String> existing = file.isFile()
                ? Files.readAllLines(file.toPath(), LATIN_1)
                : Collections.<String>emptyList();

        // both are declared, and in this order, so that each closes what it opened: the writer holds an
        // encoder of its own, and flushing the stream underneath it is not the same as closing it
        try (OutputStream stream = Files.newOutputStream(file.toPath());
             Writer writer = new OutputStreamWriter(stream, LATIN_1)) {
            writer.write(render(existing, values, known));
        }
    }

    /**
     * The same file, as text, for a caller that has nowhere to put it - {@link TemplateTool} writing to
     * standard output.
     * <p>
     * It exists so that there is no temporary file anywhere in this. Writing a configuration into the
     * system temporary directory to read it straight back would put its values where every local user can
     * read them, and a default value is sometimes a password. The first version of the tool did exactly
     * that and both scanners said so within the hour -
     * <code>java/local-temp-file-or-directory-information-disclosure</code> and <code>S5443</code> - which
     * is the second time in two days that the answer to a report was to remove the thing rather than to
     * guard it.
     * </p>
     */
    String render(Properties values, Set<String> known) {
        return render(Collections.<String>emptyList(), values, known);
    }

    private String render(List<String> existing, Properties values, Set<String> known) {
        List<String> out = new ArrayList<>();
        Set<String> placed = new LinkedHashSet<>();
        // each section is announced once, above the first key of it that gets written, wherever the file
        // happens to put that key
        Set<String> pending = new LinkedHashSet<>(headings.keySet());

        if (existing.isEmpty() && header != null)
            addComment(out, header);

        keepWhatTheFileArranged(existing, values, known, out, placed, pending);
        appendWhatTheFileDidNotHave(values, known, out, placed, pending);

        StringBuilder text = new StringBuilder();
        for (String line : out)
            // a newline rather than the platform separator: a configuration file travels between machines,
            // and a rewrite that flips every line ending is a diff with no content in it
            text.append(line).append('\n');
        return text.toString();
    }

    /** Walks the file as it stands, replacing values in place and leaving everything else alone. */
    private void keepWhatTheFileArranged(List<String> existing, Properties values, Set<String> known,
                                         List<String> out, Set<String> placed, Set<String> pending) {
        for (int i = 0; i < existing.size(); i++) {
            String raw = existing.get(i);
            if (raw.trim().isEmpty() || isComment(raw)) {
                // blank lines and comments are the file's, and pass through before anything is parsed
                out.add(raw);
                continue;
            }
            // a line ending in an odd number of backslashes continues onto the next one, and the key is
            // only readable once they are joined
            String logical = raw;
            while (continuesOntoTheNextLine(logical) && i + 1 < existing.size())
                logical = logical.substring(0, logical.length() - 1) + existing.get(++i).replaceAll("^\\s+", "");

            String key = keyOf(logical);
            if (key == null) {
                out.add(logical);
                continue;
            }
            if (!values.containsKey(key)) {
                if (!known.contains(key)) {
                    // not ours to touch: the file is read by more than this interface, and a key we have
                    // never heard of is somebody else's line, kept exactly as it was written
                    out.add(logical);
                    continue;
                }
                // declared, and no longer there: removed through Mutable, so the key goes and so does
                // the comment we wrote for it
                if (descriptions.containsKey(key))
                    dropTheCommentBlockAbove(out);
                continue;
            }
            String heading = headingOf(key, pending);
            if (descriptions.containsKey(key) || heading != null) {
                dropTheCommentBlockAbove(out);
                if (heading != null)
                    announce(out, heading);
                if (descriptions.containsKey(key))
                    addComment(out, descriptions.get(key));
            }
            out.add(escapeKey(key) + " = " + escapeValue(values.getProperty(key)));
            placed.add(key);
        }
    }

    /** New keys go at the end, alphabetical among themselves, so the diff is an addition and nothing else. */
    private void appendWhatTheFileDidNotHave(Properties values, Set<String> known, List<String> out,
                                             Set<String> placed, Set<String> pending) {
        Set<String> missing = new TreeSet<>();
        for (String key : values.stringPropertyNames())
            if (!placed.contains(key) && known.contains(key))
                missing.add(key);

        for (String key : missing) {
            if (!out.isEmpty() && out.get(out.size() - 1).length() > 0)
                out.add("");
            String heading = headingOf(key, pending);
            if (heading != null)
                announce(out, heading);
            if (descriptions.containsKey(key))
                addComment(out, descriptions.get(key));
            out.add(escapeKey(key) + " = " + escapeValue(values.getProperty(key)));
        }
    }

    /**
     * The heading of the section this key belongs to, if it has one and it has not been written yet; the
     * longest path wins, so a section inside a section announces the inner one.
     */
    private String headingOf(String key, Set<String> pending) {
        String longest = null;
        for (String path : pending)
            if (key.startsWith(path) && (longest == null || path.length() > longest.length()))
                longest = path;
        if (longest == null)
            return null;
        pending.remove(longest);
        return headings.get(longest);
    }

    /** A heading is a comment with a blank line above it, which is what makes it read as a heading. */
    private static void announce(List<String> out, String heading) {
        if (!out.isEmpty() && !out.get(out.size() - 1).isEmpty())
            out.add("");
        addComment(out, heading);
    }

    /**
     * Removes the comment block immediately above what is about to be written — contiguous, so a blank
     * line stops it and a banner two lines up survives.
     * <p>
     * That is the whole of the convention documented on {@link Config.Description}: a note you mean to
     * keep goes above a blank line. It is one rule instead of a mechanism — no marker in the file that
     * only this library understands — and it costs this method its one <code>while</code>.
     * </p>
     */
    private static void dropTheCommentBlockAbove(List<String> out) {
        while (!out.isEmpty() && isComment(out.get(out.size() - 1)))
            out.remove(out.size() - 1);
    }

    private static void addComment(List<String> out, String text) {
        for (String line : text.split("\n", -1))
            out.add("# " + line);
    }

    private static boolean isComment(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith("#") || trimmed.startsWith("!");
    }

    private static boolean continuesOntoTheNextLine(String line) {
        int backslashes = 0;
        for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--)
            backslashes++;
        return backslashes % 2 == 1;
    }

    /**
     * The key a line declares, or <code>null</code> if it declares none — a comment or a blank line.
     * The separator is the first unescaped <code>=</code>, <code>:</code> or run of whitespace, which is
     * what {@link java.util.Properties} reads.
     */
    static String keyOf(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || isComment(trimmed))
            return null;

        StringBuilder key = new StringBuilder();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '\\' && i + 1 < trimmed.length()) {
                char escaped = trimmed.charAt(++i);
                int code = escaped == 'u' ? hexQuadAt(trimmed, i + 1) : -1;
                if (escaped != 'u') {
                    key.append(unescape(escaped));
                } else if (code >= 0) {
                    key.append((char) code);
                    i += 4;
                } else {
                    // a \\u nobody can read is nobody's to interpret. It is kept as the two characters it
                    // is written with, so the key matches none of ours and the line goes back out
                    // untouched - which is what this writer does with everything it cannot claim. The
                    // file cannot have been loaded, java.util.Properties refusing it outright, but it can
                    // perfectly well be the file we are saving into.
                    key.append('\\').append('u');
                }
                continue;
            }
            if (c == '=' || c == ':' || Character.isWhitespace(c))
                break;
            key.append(c);
        }
        return key.length() == 0 ? null : key.toString();
    }

    /**
     * The character the four hexadecimal digits at the given position spell, or <code>-1</code> when there
     * are not four of them, or not hexadecimal, or not enough text left.
     * <p>
     * Reading them and deciding whether they are readable is one operation on purpose. Written as a check
     * followed by an {@link Integer#parseInt} the two can disagree - and even where they cannot, nobody
     * reading the code, and no analyser, can see that they cannot: the parse still looks like something
     * that throws.
     * </p>
     */
    private static int hexQuadAt(String s, int at) {
        if (at + 4 > s.length())
            return -1;
        int value = 0;
        for (int i = at; i < at + 4; i++) {
            int digit = Character.digit(s.charAt(i), 16);
            if (digit < 0)
                return -1;
            value = value * 16 + digit;
        }
        return value;
    }

    private static char unescape(char c) {
        switch (c) {
            case 'n': return '\n';
            case 'r': return '\r';
            case 't': return '\t';
            case 'f': return '\f';
            default: return c;
        }
    }

    static String escapeKey(String key) {
        return escape(key, true);
    }

    static String escapeValue(String value) {
        return escape(value, false);
    }

    /**
     * Exactly what {@link java.util.Properties#store} escapes. A key escapes its spaces everywhere,
     * because a space in a key is the separator; a value escapes only a leading one.
     */
    private static String escape(String text, boolean isKey) {
        StringBuilder escaped = new StringBuilder(text.length() * 2);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case ' ':
                    if (isKey || i == 0)
                        escaped.append('\\');
                    escaped.append(' ');
                    break;
                case '\\': escaped.append("\\\\"); break;
                case '\t': escaped.append("\\t"); break;
                case '\n': escaped.append("\\n"); break;
                case '\r': escaped.append("\\r"); break;
                case '\f': escaped.append("\\f"); break;
                case '=': case ':': case '#': case '!':
                    escaped.append('\\').append(c);
                    break;
                default:
                    if (c < 0x20 || c > 0x7e)
                        escaped.append(String.format("\\u%04x", (int) c));
                    else
                        escaped.append(c);
            }
        }
        return escaped.toString();
    }
}
