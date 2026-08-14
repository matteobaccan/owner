/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.loaders.PropertyKeys;

import java.util.ArrayList;
import java.util.List;

/**
 * The other ways the same key may have been written in a file.
 * <pre>
 *     firstName     the key the method resolves to
 *     first-name    kebab-case, what a .properties or a .yaml usually holds
 *     first_name    snake_case
 *     FIRST_NAME    what the same setting is called as an environment variable
 * </pre>
 *
 * <p>
 * <b>Four forms and no more, derived from the key rather than guessed at.</b> Spring Boot 1 matched
 * loosely — separators removed, case ignored, several spellings collapsing onto one property — and Boot 2
 * deliberately narrowed it to a canonical form with a defined mapping, because the loose version made it
 * impossible to say which key a value would be read from without running the program. This is the narrow
 * side of that split: the set is closed, it is written down, and every member of it is a key somebody
 * could have typed on purpose.
 * </p>
 *
 * <p>
 * <b>One form is applied to the whole key at once, not to each segment on its own.</b> A file is written
 * in one convention throughout, so <code>server.firstName</code> gives <code>server.first-name</code> and
 * never the four-to-the-power-of-the-depth mixtures that varying the segments independently would produce.
 * It also composes with the nesting: a section found under <code>first-name.</code> reads its own keys as
 * <code>first-name.host</code>, whose camelCase form is <code>firstName.host</code> — which is where the
 * {@link Config.DefaultValue} of that method was registered.
 * </p>
 *
 * <p>
 * <b>The derivation goes one way only</b>, from the key a method resolved to towards the file, and the
 * properties are never indexed or rewritten. That is what keeps {@link Accessible#store},
 * {@link Accessible#list} and {@link Accessible#propertyNames} showing the keys exactly as they were
 * loaded, and what keeps a {@link Traceable} origin attached to a key that really exists. The
 * permissiveness of normalising both sides is not lost with it: <code>@Key("first_name")</code> finds a
 * file that says <code>firstName</code>, because <code>firstName</code> is one of the forms of
 * <code>first_name</code>.
 * </p>
 *
 * @author Matteo Baccan
 * @since 2.0.0
 */
final class RelaxedKeys {

    /** One of the two characters that separate words inside a segment of a key. */
    private static final char KEBAB = '-';

    /** And the other one. See {@link #KEBAB}. */
    private static final char SNAKE = '_';

    /** Don't let anyone instantiate this class */
    private RelaxedKeys() {}

    /**
     * The other ways the given key may have been written, without the key itself and without repetitions.
     * <p>
     * The order is the one the documentation states — kebab, snake, upper snake, camel — and it is
     * observable only when a configuration holds two of these spellings at once, which is the case
     * {@link PropertiesManager#reportAmbiguousKeys} reports. camelCase comes last because it is the
     * spelling a method without a {@link Config.Key} already resolves to, so for the common configuration
     * it has been tried before this list is even built.
     * </p>
     *
     * @param key the key the method resolved to, its prefix and nesting path included.
     * @return the alternatives, in the order they are to be tried; empty when the key has no other form.
     */
    static List<String> alternativesTo(String key) {
        List<List<String>> segments = segmentsOf(key);
        List<String> forms = new ArrayList<>(4);
        add(forms, key, joined(segments, KEBAB, false));
        add(forms, key, joined(segments, SNAKE, false));
        add(forms, key, joined(segments, SNAKE, true));
        add(forms, key, camelCased(segments));
        return forms;
    }

    private static void add(List<String> forms, String key, String form) {
        if (!form.equals(key) && !forms.contains(form))
            forms.add(form);
    }

    /** The key cut at the nesting separators, each piece cut again into its words. */
    private static List<List<String>> segmentsOf(String key) {
        List<List<String>> segments = new ArrayList<>();
        int start = 0;
        int dot = key.indexOf(PropertyKeys.NESTING);
        while (dot >= 0) {
            segments.add(wordsOf(key, start, dot));
            start = dot + 1;
            dot = key.indexOf(PropertyKeys.NESTING, start);
        }
        segments.add(wordsOf(key, start, key.length()));
        return segments;
    }

    /**
     * The words one segment is made of, cut at the separators and wherever the case changes.
     * <p>
     * A segment holding nothing a word could be made of — the empty one an
     * <code>@Key("server.")</code> ends with, or a run of separators — is kept whole rather than dropped:
     * every form has to be a key of the same shape as the one it came from.
     * </p>
     */
    private static List<String> wordsOf(String key, int from, int to) {
        List<String> words = new ArrayList<>(3);
        int start = from;
        for (int i = from; i < to; i++) {
            char c = key.charAt(i);
            if (c == KEBAB || c == SNAKE) {
                if (i > start) words.add(key.substring(start, i));
                start = i + 1;
            } else if (i > start && opensAWord(key, i, to)) {
                words.add(key.substring(start, i));
                start = i;
            }
        }
        if (to > start) words.add(key.substring(start, to));
        if (words.isEmpty() && to > from) words.add(key.substring(from, to));
        return words;
    }

    /**
     * Whether a word starts at the given position because the case changes there.
     * <p>
     * Two boundaries and not one: <code>firstName</code> breaks at the upper case following a lower one,
     * and <code>httpURLConnection</code> breaks again before <code>Connection</code>, at the last capital
     * of a run that is followed by a lower case letter. Without the second, an acronym would swallow the
     * word after it and give <code>http-urlconnection</code>.
     * </p>
     */
    private static boolean opensAWord(String key, int i, int to) {
        char c = key.charAt(i);
        if (!Character.isUpperCase(c))
            return false;
        if (!Character.isUpperCase(key.charAt(i - 1)))
            return true;
        return i + 1 < to && Character.isLowerCase(key.charAt(i + 1));
    }

    /** The words joined by the given separator, all in one case, the segments joined by the nesting one. */
    private static String joined(List<List<String>> segments, char separator, boolean upperCase) {
        StringBuilder out = new StringBuilder();
        for (int s = 0; s < segments.size(); s++) {
            if (s > 0) out.append(PropertyKeys.NESTING);
            List<String> segment = segments.get(s);
            for (int i = 0; i < segment.size(); i++) {
                if (i > 0) out.append(separator);
                appendCased(out, segment.get(i), upperCase);
            }
        }
        return out.toString();
    }

    /** The words with no separator at all, each but the first of its segment capitalised. */
    private static String camelCased(List<List<String>> segments) {
        StringBuilder out = new StringBuilder();
        for (int s = 0; s < segments.size(); s++) {
            if (s > 0) out.append(PropertyKeys.NESTING);
            List<String> segment = segments.get(s);
            for (int i = 0; i < segment.size(); i++) {
                String word = segment.get(i);
                if (i > 0) {
                    out.append(Character.toUpperCase(word.charAt(0)));
                    appendCased(out, word.substring(1), false);
                } else {
                    appendCased(out, word, false);
                }
            }
        }
        return out.toString();
    }

    /**
     * Character by character rather than through {@link String#toLowerCase()}, which is locale sensitive:
     * a property key is not text in the user's language, and under a Turkish locale the same interface
     * would resolve <code>maxThreads</code> to a key holding a dotless i.
     */
    private static void appendCased(StringBuilder out, String word, boolean upperCase) {
        for (int i = 0; i < word.length(); i++)
            out.append(upperCase
                    ? Character.toUpperCase(word.charAt(i))
                    : Character.toLowerCase(word.charAt(i)));
    }
}
