/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.formats.toml;

import org.aeonbits.owner.loaders.PropertyKeys;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Reads a TOML v1.0.0 document into properties, flattening it as it goes.
 *
 * <p>
 * The keys are made by {@link PropertyKeys}, the same convention every loader in this library flattens
 * with — and TOML is the format that convention was already shaped like: <code>[[servers]]</code> is
 * <code>servers[0]</code>, a dotted key is the flattening itself, and <code>[table]</code> is a prefix.
 * </p>
 *
 * <p>
 * <b>Values are kept as written, except where TOML offers several spellings of one value.</b>
 * <code>1_000</code>, <code>0xDEAD</code>, <code>0o755</code> and <code>0b1101</code> are four ways of
 * writing one integer and none of them converts, so they are canonicalised to plain decimal; <code>inf</code>
 * and <code>nan</code> become Java's <code>Infinity</code> and <code>NaN</code>; and the space TOML allows
 * in place of the <code>T</code> of a date-time becomes a <code>T</code>, which is what
 * {@link java.time.LocalDateTime#parse} wants. Strings and everything else are untouched.
 * </p>
 *
 * <p>
 * Every complaint carries the line and the column.
 * </p>
 *
 * @author Matteo Baccan
 * @since 2.0.0
 */
final class TomlParser {

    private final String text;
    private final Properties into;
    private int at;

    /** The table the following key/value pairs belong to, already flattened. */
    private String table = "";

    /** Every key that has been given a value, so that a second one is refused. */
    private final Set<String> values = new HashSet<>();

    /** Paths that name a table, however they came to exist, so that a redefinition can be spotted. */
    private final Set<String> tables = new HashSet<>();

    /** Tables written out with a <code>[header]</code>, which may not be written out twice. */
    private final Set<String> explicit = new HashSet<>();

    /** Paths created as a side effect of a dotted key, which a later header may not reopen. */
    private final Set<String> dotted = new HashSet<>();

    /** Paths that came from an inline table, which nothing may extend. */
    private final Set<String> inline = new HashSet<>();

    /** For each <code>[[array of tables]]</code>, how many elements have been written. */
    private final Map<String, Integer> arrays = new HashMap<>();

    TomlParser(String text, Properties into) {
        this.text = text;
        this.into = into;
    }

    /**
     * Reads the whole document.
     *
     * @throws IOException if it is not a TOML document, or not one a configuration can be made of.
     */
    void parse() throws IOException {
        while (true) {
            skipToNextExpression();
            if (!more())
                return;
            if (peek() == '[')
                header();
            else
                keyValue(table, true);
            endOfLine();
        }
    }

    // -------------------------------------------------------------- tables

    /** A <code>[table]</code> or an <code>[[array of tables]]</code> header. */
    private void header() throws IOException {
        int headerAt = at;
        expect('[');
        boolean arrayOfTables = peekIs('[');
        if (arrayOfTables)
            at++;

        List<String> path = keyPath();
        expect(']');
        if (arrayOfTables)
            expect(']');

        if (arrayOfTables)
            openArrayOfTables(path, headerAt);
        else
            openTable(path, headerAt);
    }

    private void openTable(List<String> path, int headerAt) throws IOException {
        String key = resolve(path);
        if (arrays.containsKey(unindexed(path)))
            throw errorAt(headerAt, "[" + dot(path) + "] is an array of tables, so it cannot also be a "
                    + "table: write [[" + dot(path) + "]] to add another element");
        if (!explicit.add(key))
            throw errorAt(headerAt, "the table [" + dot(path) + "] is defined twice");
        if (values.contains(key))
            throw errorAt(headerAt, "[" + dot(path) + "] is already a value, so it cannot be a table");
        if (dotted.contains(key))
            throw errorAt(headerAt, "[" + dot(path) + "] was already written as a dotted key inside another "
                    + "table, and a table cannot be reopened");
        if (inline.contains(key))
            throw errorAt(headerAt, "[" + dot(path) + "] is an inline table, which cannot be extended");
        claimParents(path, headerAt);
        tables.add(key);
        table = key;
    }

    private void openArrayOfTables(List<String> path, int headerAt) throws IOException {
        // only the path above it is resolved: the last segment is the array being written, and indexing it
        // here as well as below would give products[0][1] for the second [[products]]
        String key = unindexed(path);
        if (explicit.contains(key) || (tables.contains(key) && !arrays.containsKey(key)))
            throw errorAt(headerAt, "[[" + dot(path) + "]] is already a table, so it cannot also be an "
                    + "array of tables");
        if (values.contains(key))
            throw errorAt(headerAt, "[[" + dot(path) + "]] is already a value");
        claimParents(path, headerAt);
        int index = arrays.containsKey(key) ? arrays.get(key) : 0;
        arrays.put(key, index + 1);
        tables.add(key);
        table = PropertyKeys.element(key, index);
    }

    /** The resolved key of a header's path with its own last segment left un-indexed. */
    private String unindexed(List<String> path) {
        return PropertyKeys.child(resolveParent(path), path.get(path.size() - 1));
    }

    /**
     * Every part of a header's path above the last names a table too, implicitly. Claiming them is what
     * makes <code>[a.b]</code> after <code>a = 1</code> a refusal rather than a key called
     * <code>a.b</code> sitting beside a value called <code>a</code>.
     */
    private void claimParents(List<String> path, int headerAt) throws IOException {
        String walked = "";
        String resolved = "";
        for (int i = 0; i < path.size() - 1; i++) {
            walked = PropertyKeys.child(walked, path.get(i));
            resolved = index(PropertyKeys.child(resolved, path.get(i)));
            if (values.contains(resolved))
                throw errorAt(headerAt, "'" + walked + "' is a value, so it cannot also hold a table");
            if (inline.contains(resolved))
                throw errorAt(headerAt, "'" + walked + "' is an inline table, which cannot be extended");
            tables.add(resolved);
        }
    }

    /**
     * The flattened prefix a header's path names, which is not simply the path: a segment that is an
     * <code>[[array of tables]]</code> carries the index of the element being written, so
     * <code>[fruits.physical]</code> after <code>[[fruits]]</code> is <code>fruits[0].physical</code> and
     * belongs to that element rather than sitting beside the array.
     */
    private String resolve(List<String> path) {
        String resolved = "";
        for (String part : path)
            resolved = index(PropertyKeys.child(resolved, part));
        return resolved;
    }

    /** As {@link #resolve}, stopping before the last segment. */
    private String resolveParent(List<String> path) {
        return resolve(path.subList(0, path.size() - 1));
    }

    /**
     * Adds the current element's index to a prefix, if that prefix is an array of tables.
     *
     * <p>
     * The count is kept against the <b>resolved</b> key rather than the declared path, which is what makes
     * an array inside an array work: <code>[[fruits.varieties]]</code> under the second
     * <code>[[fruits]]</code> counts as <code>fruits[1].varieties</code> and starts again at zero, where
     * counting against <code>fruits.varieties</code> would carry the first fruit's total into the second.
     * </p>
     */
    private String index(String resolved) {
        Integer written = arrays.get(resolved);
        return written == null ? resolved : PropertyKeys.element(resolved, written - 1);
    }

    // ---------------------------------------------------------- key/values

    /**
     * A <code>key = value</code> pair.
     *
     * @param parent    the key it hangs from.
     * @param topLevel  whether it stands on a line of its own, as opposed to inside an inline table.
     */
    private void keyValue(String parent, boolean topLevel) throws IOException {
        int keyAt = at;
        List<String> path = keyPath();
        String key = parent.isEmpty() ? join(path) : parent + PropertyKeys.NESTING + join(path);

        if (values.contains(key) || tables.contains(key) || explicit.contains(key))
            throw errorAt(keyAt, "'" + dot(path) + "' is defined twice");

        // the parts above the last are tables this key brings into being, and a later [header] may not
        // reopen them - which is the rule that makes a document's shape unambiguous
        String walked = parent;
        for (int i = 0; i < path.size() - 1; i++) {
            walked = PropertyKeys.child(walked, path.get(i));
            if (values.contains(walked))
                throw errorAt(keyAt, "'" + walked + "' is a value, so it cannot also hold a key");
            if (inline.contains(walked))
                throw errorAt(keyAt, "'" + walked + "' is an inline table, which cannot be extended");
            // a dotted key may bring a table into being, but it may not reach into one that a [header]
            // already wrote out. TOML calls this too confusing to allow rather than ambiguous, and says so:
            // see toml-lang/toml#846. It is also what stops [a.b.c] and then [a] with b.c.t from meaning
            // something the reader has to scroll to work out
            if (explicit.contains(walked))
                throw errorAt(keyAt, "[" + walked + "] is written out as a table of its own, so a dotted "
                        + "key here cannot add to it");
            if (arrays.containsKey(walked))
                throw errorAt(keyAt, "[[" + walked + "]] is an array of tables, and a dotted key cannot "
                        + "add to one: write another [[" + walked + "]] element");
            tables.add(walked);
            if (topLevel)
                dotted.add(walked);
        }

        skipSpaces();
        expect('=');
        skipSpaces();
        value(key);
    }

    /** A key, which may be bare, quoted, or dotted into several parts. */
    private List<String> keyPath() throws IOException {
        List<String> path = new ArrayList<>();
        while (true) {
            skipSpaces();
            path.add(keyPart());
            skipSpaces();
            if (!peekIs('.'))
                return path;
            at++;
        }
    }

    private String keyPart() throws IOException {
        if (!more())
            throw error("a key was expected");
        char c = peek();
        if (c == '"')
            return basicString();
        if (c == '\'')
            return literalString();

        int start = at;
        while (more() && isBare(peek()))
            at++;
        if (start == at)
            throw error("a key was expected");
        return text.substring(start, at);
    }

    private static boolean isBare(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || c == '_' || c == '-';
    }

    // --------------------------------------------------------------- values

    private void value(String key) throws IOException {
        if (!more())
            throw error("a value was expected");
        char c = peek();
        if (c == '[') {
            array(key);
            return;
        }
        if (c == '{') {
            inlineTable(key);
            return;
        }
        values.add(key);
        into.setProperty(key, scalar());
    }

    /**
     * An array. Its elements are numbered from zero, and an empty one writes an empty value, which the
     * library already reads as an empty collection — the same answer JSON gives to <code>[]</code>.
     */
    private void array(String key) throws IOException {
        expect('[');
        // taken whether or not anything is written under it: an array is a value, so a second
        // arr = [2] and a later [arr.x] are both redefinitions even though the properties are arr[0]
        values.add(key);
        int index = 0;
        while (true) {
            skipArrayWhitespace();
            if (peekIs(']')) {
                at++;
                if (index == 0)
                    into.setProperty(key, "");
                return;
            }
            value(PropertyKeys.element(key, index++));
            skipArrayWhitespace();
            if (peekIs(',')) {
                at++;
                continue;
            }
            skipArrayWhitespace();
            expect(']');
            return;
        }
    }

    /**
     * An inline table, which is written on one line and is closed for good: nothing may add a key to it
     * afterwards, which is why the path is remembered.
     */
    private void inlineTable(String key) throws IOException {
        expect('{');
        inline.add(key);
        tables.add(key);
        skipSpaces();
        if (peekIs('}')) {
            at++;
            return;
        }
        while (true) {
            skipSpaces();
            keyValue(key, false);
            skipSpaces();
            if (peekIs(',')) {
                at++;
                continue;
            }
            expect('}');
            return;
        }
    }

    /** A string, a number, a boolean or a date-time — everything that is not a collection. */
    private String scalar() throws IOException {
        char c = peek();
        if (c == '"')
            return startsWith("\"\"\"") ? multiLineBasicString() : basicString();
        if (c == '\'')
            return startsWith("'''") ? multiLineLiteralString() : literalString();
        if (text.startsWith("true", at) && !isBare(charAfter(4))) {
            at += 4;
            return "true";
        }
        if (text.startsWith("false", at) && !isBare(charAfter(5))) {
            at += 5;
            return "false";
        }
        return numberOrDate();
    }

    private char charAfter(int length) {
        int index = at + length;
        return index < text.length() ? text.charAt(index) : ' ';
    }

    private boolean startsWith(String what) {
        return text.startsWith(what, at);
    }

    // -------------------------------------------------------------- strings

    private String basicString() throws IOException {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (true) {
            if (!more() || peek() == '\n')
                throw error("the string was not closed before the end of the line");
            char c = text.charAt(at++);
            if (c == '"')
                return out.toString();
            if (c == '\\')
                escape(out);
            else
                out.append(refuseControl(c));
        }
    }

    private String literalString() throws IOException {
        expect('\'');
        StringBuilder out = new StringBuilder();
        while (true) {
            if (!more() || peek() == '\n')
                throw error("the literal string was not closed before the end of the line");
            char c = text.charAt(at++);
            if (c == '\'')
                return out.toString();
            out.append(refuseControl(c));
        }
    }

    private String multiLineBasicString() throws IOException {
        at += 3;
        skipFirstNewline();
        StringBuilder out = new StringBuilder();
        while (true) {
            if (!more())
                throw error("the multi-line string was not closed");
            if (startsWith("\"\"\"")) {
                at += 3;
                // up to two further quotes belong to the string, """ being the only thing that ends it
                while (peekIs('"') && out.length() >= 0 && countQuotes() < 3) {
                    out.append('"');
                    at++;
                }
                return out.toString();
            }
            char c = text.charAt(at++);
            if (c == '\\') {
                if (isLineEndingBackslash()) {
                    trimWhitespaceAcrossLines();
                    continue;
                }
                escape(out);
            } else {
                out.append(refuseControlInMultiLine(c));
            }
        }
    }

    private int countQuotes() {
        int count = 0;
        while (at + count < text.length() && text.charAt(at + count) == '"')
            count++;
        return count;
    }

    private String multiLineLiteralString() throws IOException {
        at += 3;
        skipFirstNewline();
        StringBuilder out = new StringBuilder();
        while (true) {
            if (!more())
                throw error("the multi-line literal string was not closed");
            if (startsWith("'''")) {
                at += 3;
                while (peekIs('\'') && countApostrophes() < 3) {
                    out.append('\'');
                    at++;
                }
                return out.toString();
            }
            out.append(refuseControlInMultiLine(text.charAt(at++)));
        }
    }

    private int countApostrophes() {
        int count = 0;
        while (at + count < text.length() && text.charAt(at + count) == '\'')
            count++;
        return count;
    }

    /** A newline straight after the opening delimiter is not part of the string. */
    private void skipFirstNewline() {
        if (peekIs('\n'))
            at++;
        else if (startsWith("\r\n"))
            at += 2;
    }

    private boolean isLineEndingBackslash() {
        int look = at;
        while (look < text.length() && (text.charAt(look) == ' ' || text.charAt(look) == '\t'))
            look++;
        return look < text.length() && (text.charAt(look) == '\n' || text.charAt(look) == '\r');
    }

    private void trimWhitespaceAcrossLines() {
        while (more()) {
            char c = peek();
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r')
                return;
            at++;
        }
    }

    private void escape(StringBuilder out) throws IOException {
        if (!more())
            throw error("the string ends with an unfinished escape");
        char c = text.charAt(at++);
        switch (c) {
            case 'b': out.append('\b'); return;
            case 't': out.append('\t'); return;
            case 'n': out.append('\n'); return;
            case 'f': out.append('\f'); return;
            case 'r': out.append('\r'); return;
            case '"': out.append('"'); return;
            case '\\': out.append('\\'); return;
            case 'u': out.appendCodePoint(codePoint(4)); return;
            case 'U': out.appendCodePoint(codePoint(8)); return;
            default:
                throw errorAt(at - 1, "'\\" + c + "' is not an escape TOML knows");
        }
    }

    /**
     * The digits of a backslash-u or backslash-U escape, added up as they are checked so that no substring
     * is taken and no unchecked text ever reaches a parse. (The escape cannot be written out here: the Java
     * compiler resolves one inside a comment as readily as inside a string.)
     */
    private int codePoint(int length) throws IOException {
        // a long, because eight hexadecimal digits overflow an int and the overflowed value walks straight
        // through the range check below and out into appendCodePoint, which throws the wrong exception
        long value = 0;
        for (int i = 0; i < length; i++) {
            if (!more())
                throw error("an escape needs " + length + " hexadecimal digits");
            int digit = Character.digit(text.charAt(at), 16);
            if (digit < 0)
                throw error("an escape needs " + length + " hexadecimal digits");
            value = value * 16 + digit;
            at++;
        }
        if (value > 0x10FFFF || (value >= 0xD800 && value <= 0xDFFF))
            throw errorAt(at - length, "an escape does not name a character");
        return (int) value;
    }

    /**
     * The same rule as {@link #refuseControl}, but a multi-line string may hold the newlines that end its
     * lines - and nothing else that is a control character.
     */
    private char refuseControlInMultiLine(char c) throws IOException {
        if (c == 0x0A || c == 0x0D)
            return c;
        return refuseControl(c);
    }

    private char refuseControl(char c) throws IOException {
        if (c < 0x20 && c != '\t')
            throw errorAt(at - 1, "a control character has to be written as an escape");
        if (c == 0x7F)
            throw errorAt(at - 1, "a control character has to be written as an escape");
        return c;
    }

    // -------------------------------------------------- numbers and dates

    /**
     * Everything that is neither a string, a boolean nor a collection: the four date-time types, and the
     * integers and floats, which are canonicalised because TOML writes each of them several ways.
     */
    private String numberOrDate() throws IOException {
        int start = at;
        while (more() && !isValueEnd(peek()))
            at++;
        String raw = text.substring(start, at);
        if (raw.isEmpty())
            throw errorAt(start, "a value was expected");

        String dateTime = asDateTime(raw, start);
        if (dateTime != null)
            return dateTime;
        return asNumber(raw, start);
    }

    /**
     * A bare value ends at whitespace, with one exception: TOML lets a date and a time be separated by a
     * space instead of a <code>T</code>. Scanning to the end of the line instead would swallow whatever
     * follows and report <code>'1 b = 2' is not a value</code> where the document's real fault is two
     * expressions on one line.
     */
    private boolean isValueEnd(char c) {
        if (c == ',' || c == ']' || c == '}' || c == '\n' || c == '\r' || c == '#')
            return true;
        if (c != ' ' && c != '\t')
            return false;
        return c == '\t' || !continuesADateTime();
    }

    /** Whether the space under the cursor joins a date already scanned to the time that follows it. */
    private boolean continuesADateTime() {
        int look = at + 1;
        int digits = 0;
        while (look < text.length() && digits < 2 && Character.isDigit(text.charAt(look))) {
            look++;
            digits++;
        }
        return digits == 2 && look < text.length() && text.charAt(look) == ':';
    }

    /**
     * A date-time, kept as written but for the space TOML allows in place of the <code>T</code>: the
     * conversion chain reads these with {@link java.time.LocalDate#parse} and its relatives, which want the
     * ISO form.
     */
    private String asDateTime(String raw, int start) throws IOException {
        if (looksLikeDate(raw))
            return dateAndWhateverFollowsIt(raw, start);
        if (looksLikeTime(raw))
            return localTime(raw, start);
        return null;
    }

    /**
     * A local date, a local date-time or an offset date-time - which is to say a date, and then whichever
     * of them the rest of the text turns it into.
     *
     * <p>
     * <b>Once it starts like a date it has to be one.</b> Answering <code>null</code> here would hand
     * <code>2020-01-01x</code> to the number reader and have it refused for not being a number, which is
     * true and unhelpful; the document meant a date and the complaint should say what is wrong with it.
     * </p>
     */
    private String dateAndWhateverFollowsIt(String raw, int start) throws IOException {
        refuseImpossibleDate(raw, start);
        if (raw.length() == 10)
            return raw;

        char delimiter = raw.charAt(10);
        if (delimiter != 'T' && delimiter != 't' && delimiter != ' ')
            throw errorAt(start, "'" + raw + "' has no T between its date and its time");

        // the whole of the rest is the time, so it is measured before the offset is taken off it
        String rest = raw.substring(11);
        String offset = "";
        int offsetAt = offsetStartsAt(rest);
        if (offsetAt >= 0) {
            offset = rest.substring(offsetAt);
            rest = rest.substring(0, offsetAt);
            refuseImpossibleOffset(offset, raw, start);
        }
        refuseImpossibleTime(rest, raw, start);

        // TOML writes the delimiter as T, t or a space and the zone as Z or z; java.time reads only the
        // upper-case forms, and these are spellings of one instant rather than values of their own
        return raw.substring(0, 10) + 'T' + rest + ("z".equals(offset) ? "Z" : offset);
    }

    /** A local time, which in TOML carries no offset: <code>07:32:00</code> or <code>07:32:00.999</code>. */
    private String localTime(String raw, int start) throws IOException {
        refuseImpossibleTime(raw, raw, start);
        return raw;
    }

    /**
     * Where the offset begins in the text after the delimiter, or <code>-1</code> for a local date-time.
     * The <code>-</code> of a negative offset cannot be confused with anything, a time holding none.
     */
    private static int offsetStartsAt(String time) {
        for (int i = 0; i < time.length(); i++) {
            char c = time.charAt(i);
            if (c == 'Z' || c == 'z' || c == '+' || c == '-')
                return i;
        }
        return -1;
    }

    /**
     * The date is real. <code>java.time</code> is asked rather than the month lengths written out again:
     * it knows that February has 29 days in 2000 and not in 2100, which is the whole of what these cases
     * are about.
     */
    private void refuseImpossibleDate(String raw, int start) throws IOException {
        try {
            LocalDate.of(number(raw, 0, 4), number(raw, 5, 2), number(raw, 8, 2));
        } catch (DateTimeException noSuchDay) {
            throw errorAt(start, "'" + raw.substring(0, 10) + "' is not a date that exists: "
                    + noSuchDay.getMessage());
        }
    }

    /**
     * The time is real, and is written the one way TOML writes one: two digits for each of the hour, the
     * minute and the second, and an optional fraction of at least one digit.
     *
     * <p>
     * The second may be <b>60</b>, which is not a mistake: TOML allows it for a leap second, and it is the
     * one place these ranges are not the ones {@link java.time.LocalTime} would accept.
     * </p>
     */
    private void refuseImpossibleTime(String time, String raw, int start) throws IOException {
        if (!looksLikeTime(time))
            throw errorAt(start, "'" + raw + "' has no time, or one not written as hh:mm:ss");
        refuseOutOfRange(number(time, 0, 2), 23, "hour", raw, start);
        refuseOutOfRange(number(time, 3, 2), 59, "minute", raw, start);
        // 60 and not 59: a leap second is a second TOML lets a document write
        refuseOutOfRange(number(time, 6, 2), 60, "second", raw, start);

        if (time.length() == 8)
            return;
        if (time.charAt(8) != '.' || time.length() == 9)
            throw errorAt(start, "'" + raw + "' has something after its seconds that is not a fraction");
        for (int i = 9; i < time.length(); i++)
            if (!isDigit(time.charAt(i)))
                throw errorAt(start, "'" + raw + "' has a fraction of a second that is not all digits");
    }

    /** An offset is <code>Z</code>, or a sign and an hour and a minute, and nothing else. */
    private void refuseImpossibleOffset(String offset, String raw, int start) throws IOException {
        if (offset.equals("Z") || offset.equals("z"))
            return;
        boolean signed = offset.charAt(0) == '+' || offset.charAt(0) == '-';
        if (!signed || offset.length() != 6 || !digitsAt(offset, 1, 2) || offset.charAt(3) != ':'
                || !digitsAt(offset, 4, 2))
            throw errorAt(start, "'" + raw + "' has an offset that is not Z or +hh:mm");
        refuseOutOfRange(number(offset, 1, 2), 23, "offset hour", raw, start);
        refuseOutOfRange(number(offset, 4, 2), 59, "offset minute", raw, start);
    }

    private void refuseOutOfRange(int value, int highest, String what, String raw, int start)
            throws IOException {
        if (value > highest)
            throw errorAt(start, "'" + raw + "' has " + what + " " + value + ", and the highest there is "
                    + "is " + highest);
    }

    private static int number(String text, int from, int count) {
        int value = 0;
        for (int i = from; i < from + count; i++)
            value = value * 10 + (text.charAt(i) - '0');
        return value;
    }

    private static boolean looksLikeDate(String raw) {
        return raw.length() >= 10 && digitsAt(raw, 0, 4) && raw.charAt(4) == '-'
                && digitsAt(raw, 5, 2) && raw.charAt(7) == '-' && digitsAt(raw, 8, 2)
                // exactly four digits of year: 10000-01-01 is a year RFC 3339 has no room for
                && (raw.length() == 10 || !isDigit(raw.charAt(10)));
    }

    private static boolean looksLikeTime(String raw) {
        return raw.length() >= 8 && digitsAt(raw, 0, 2) && raw.charAt(2) == ':'
                && digitsAt(raw, 3, 2) && raw.charAt(5) == ':' && digitsAt(raw, 6, 2);
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean digitsAt(String raw, int from, int count) {
        if (from + count > raw.length())
            return false;
        for (int i = from; i < from + count; i++)
            if (raw.charAt(i) < '0' || raw.charAt(i) > '9')
                return false;
        return true;
    }

    /** An integer or a float, canonicalised. See the class javadoc for why this one format is. */
    private String asNumber(String raw, int start) throws IOException {
        String sign = "";
        String body = raw;
        boolean signed = body.startsWith("+") || body.startsWith("-");
        if (signed) {
            if (body.startsWith("-"))
                sign = "-";
            body = body.substring(1);
        }

        if (body.equals("inf"))
            return sign + "Infinity";
        if (body.equals("nan"))
            return sign.isEmpty() ? "NaN" : "-NaN";

        if (body.length() > 2 && body.charAt(0) == '0') {
            char radix = body.charAt(1);
            if (radix == 'x' || radix == 'o' || radix == 'b') {
                if (signed)
                    throw errorAt(start, "'" + raw + "' cannot be signed: a hexadecimal, octal or binary "
                            + "integer has no sign in TOML");
                return radixInteger(body.substring(2), radix == 'x' ? 16 : radix == 'o' ? 8 : 2, raw, start);
            }
        }

        String digits = withoutUnderscores(body, raw, start, 10);
        refuseLeadingZero(digits, raw, start);
        try {
            if (isInteger(digits)) {
                // parsed with the sign attached, Long.MIN_VALUE having no positive counterpart, and
                // answered back through toString so that -0 comes out as the zero it is
                return Long.toString(Long.parseLong(sign + digits));
            }
            refuseMalformedFloat(digits, raw, start);
            double parsed = Double.parseDouble(digits);
            if (Double.isInfinite(parsed) || Double.isNaN(parsed))
                throw errorAt(start, "'" + raw + "' is not a number TOML can read");
            return sign + digits;
        } catch (NumberFormatException notANumber) {
            throw errorAt(start, "'" + raw + "' is not a value TOML can read");
        }
    }

    private String radixInteger(String body, int radix, String raw, int start) throws IOException {
        String digits = withoutUnderscores(body, raw, start, radix);
        if (digits.isEmpty())
            throw errorAt(start, "'" + raw + "' has no digits after its prefix");
        // Long.parseLong would read 0x-1 as -1: the sign belongs before the prefix, and TOML allows none
        if (digits.charAt(0) == '+' || digits.charAt(0) == '-')
            throw errorAt(start, "'" + raw + "' has a sign after its prefix");
        try {
            return Long.toString(Long.parseLong(digits, radix));
        } catch (NumberFormatException notANumber) {
            throw errorAt(start, "'" + raw + "' is not an integer of that base");
        }
    }

    /**
     * TOML lets an underscore separate digits, and it must lie between two of them: a leading, trailing or
     * doubled one is a mistake rather than decoration.
     */
    private String withoutUnderscores(String body, String raw, int start, int radix) throws IOException {
        if (body.indexOf('_') < 0)
            return body;
        StringBuilder out = new StringBuilder(body.length());
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c != '_') {
                out.append(c);
                continue;
            }
            boolean between = i > 0 && i < body.length() - 1
                    && Character.digit(body.charAt(i - 1), radix) >= 0
                    && Character.digit(body.charAt(i + 1), radix) >= 0;
            if (!between)
                throw errorAt(start, "'" + raw + "' has an underscore that is not between two digits");
        }
        return out.toString();
    }

    /**
     * A float is written the one way TOML writes one: digits, then a fraction or an exponent or both, and
     * digits on <b>both</b> sides of every dot and after the <code>e</code>.
     *
     * <p>
     * {@link Double#parseDouble} is far more permissive - it reads <code>.5</code>, <code>5.</code> and
     * <code>1e2.3</code> - and being more permissive than the specification is the one failure that matters
     * for a format this widely tooled: it means reading a file every other tool refuses, and such a file is
     * nearly always read by something else too.
     * </p>
     */
    private void refuseMalformedFloat(String digits, String raw, int start) throws IOException {
        String mantissa = digits;
        int e = indexOfExponent(digits);
        if (e >= 0) {
            String exponent = digits.substring(e + 1);
            mantissa = digits.substring(0, e);
            if (exponent.startsWith("+") || exponent.startsWith("-"))
                exponent = exponent.substring(1);
            if (!allDigits(exponent))
                throw errorAt(start, "'" + raw + "' has an exponent that is not a whole number");
        }

        int dot = mantissa.indexOf('.');
        if (dot < 0) {
            if (!allDigits(mantissa))
                throw errorAt(start, "'" + raw + "' is not a number TOML can read");
            return;
        }
        if (!allDigits(mantissa.substring(0, dot)) || !allDigits(mantissa.substring(dot + 1)))
            throw errorAt(start, "'" + raw + "' has a dot without digits on both sides of it");
    }

    private static int indexOfExponent(String digits) {
        int e = digits.indexOf('e');
        return e >= 0 ? e : digits.indexOf('E');
    }

    private static boolean allDigits(String digits) {
        if (digits.isEmpty())
            return false;
        for (int i = 0; i < digits.length(); i++)
            if (!isDigit(digits.charAt(i)))
                return false;
        return true;
    }

    private void refuseLeadingZero(String digits, String raw, int start) throws IOException {
        if (digits.length() > 1 && digits.charAt(0) == '0'
                && digits.charAt(1) != '.' && digits.charAt(1) != 'e' && digits.charAt(1) != 'E')
            throw errorAt(start, "'" + raw + "' cannot begin with a zero: write 1 rather than 01");
    }

    private static boolean isInteger(String digits) {
        if (digits.isEmpty())
            return false;
        for (int i = 0; i < digits.length(); i++)
            if (digits.charAt(i) < '0' || digits.charAt(i) > '9')
                return false;
        return true;
    }

    // ------------------------------------------------------------ scanning

    /** Whitespace, comments and blank lines, up to the start of the next expression. */
    private void skipToNextExpression() throws IOException {
        while (more()) {
            char c = peek();
            if (c == '\r') {
                // a carriage return is half of a line ending, never whitespace on its own: a bare one is a
                // control character loose in the document, and TOML says so
                if (at + 1 >= text.length() || text.charAt(at + 1) != '\n')
                    throw error("a carriage return has to be followed by a newline");
                at++;
            } else if (c == ' ' || c == '\t' || c == '\n') {
                at++;
            } else if (c == '#') {
                comment();
            } else {
                return;
            }
        }
    }

    /** Inside an array, a newline is whitespace and a comment may appear between elements. */
    private void skipArrayWhitespace() throws IOException {
        while (more()) {
            char c = peek();
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                at++;
            } else if (c == '#') {
                comment();
            } else {
                return;
            }
        }
    }

    private void comment() throws IOException {
        at++;
        while (more() && peek() != '\n') {
            // a CR that ends the line ends the comment; a bare one is a control character like any other
            if (peek() == 0x0D && at + 1 < text.length() && text.charAt(at + 1) == '\n')
                return;
            // the same rule as inside a string, DEL included: a comment is text, not a place to hide bytes
            refuseControl(peek());
            at++;
        }
    }

    /** After an expression only spaces and a comment may follow, and then the line has to end. */
    private void endOfLine() throws IOException {
        skipSpaces();
        if (peekIs('#'))
            comment();
        if (!more())
            return;
        char c = peek();
        if (c != '\n' && c != '\r')
            throw error("there is more on this line than one key or table");
    }

    private void skipSpaces() {
        while (more() && (peek() == ' ' || peek() == '\t'))
            at++;
    }

    private static String join(List<String> path) {
        String key = "";
        for (String part : path)
            key = PropertyKeys.child(key, part);
        return key;
    }

    /** The path as the document wrote it, for a message that a reader can match against the file. */
    private static String dot(List<String> path) {
        StringBuilder out = new StringBuilder();
        for (String part : path) {
            if (out.length() > 0)
                out.append('.');
            out.append(part);
        }
        return out.toString();
    }

    private void expect(char c) throws IOException {
        if (!more() || text.charAt(at) != c)
            throw error("'" + c + "' was expected");
        at++;
    }

    private boolean more() {
        return at < text.length();
    }

    private char peek() {
        return text.charAt(at);
    }

    private boolean peekIs(char c) {
        return more() && text.charAt(at) == c;
    }

    private IOException error(String what) {
        return errorAt(at, what);
    }

    /**
     * Every complaint carries the line and the column, which is the whole difference between a message a
     * reader can act on and one that sends them looking through the file by hand.
     */
    private IOException errorAt(int position, String what) {
        int line = 1;
        int column = 1;
        for (int i = 0; i < position && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new IOException(String.format("Line %d, column %d: %s", line, column, what));
    }
}
