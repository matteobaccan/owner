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
        String key = join(path);
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
        String key = join(path);
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

    /**
     * Every part of a header's path above the last names a table too, implicitly. Claiming them is what
     * makes <code>[a.b]</code> after <code>a = 1</code> a refusal rather than a key called
     * <code>a.b</code> sitting beside a value called <code>a</code>.
     */
    private void claimParents(List<String> path, int headerAt) throws IOException {
        String walked = "";
        for (int i = 0; i < path.size() - 1; i++) {
            walked = PropertyKeys.child(walked, path.get(i));
            if (values.contains(walked))
                throw errorAt(headerAt, "'" + walked + "' is a value, so it cannot also hold a table");
            if (inline.contains(walked))
                throw errorAt(headerAt, "'" + walked + "' is an inline table, which cannot be extended");
            tables.add(walked);
        }
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
        int index = 0;
        while (true) {
            skipArrayWhitespace();
            if (peekIs(']')) {
                at++;
                if (index == 0) {
                    values.add(key);
                    into.setProperty(key, "");
                }
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
                out.append(c);
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
            out.append(text.charAt(at++));
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
        int value = 0;
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
        return value;
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
        String candidate = raw;
        if (candidate.length() > 10 && candidate.charAt(10) == ' ' && looksLikeDate(candidate))
            candidate = candidate.substring(0, 10) + 'T' + candidate.substring(11);
        if (looksLikeDate(candidate) || looksLikeTime(candidate)) {
            if (candidate.indexOf(' ') >= 0)
                throw errorAt(start, "'" + raw + "' is not a date or a time TOML can read");
            return candidate;
        }
        return null;
    }

    private static boolean looksLikeDate(String raw) {
        return raw.length() >= 10 && digitsAt(raw, 0, 4) && raw.charAt(4) == '-'
                && digitsAt(raw, 5, 2) && raw.charAt(7) == '-' && digitsAt(raw, 8, 2);
    }

    private static boolean looksLikeTime(String raw) {
        return raw.length() >= 8 && digitsAt(raw, 0, 2) && raw.charAt(2) == ':'
                && digitsAt(raw, 3, 2) && raw.charAt(5) == ':' && digitsAt(raw, 6, 2);
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
        if (body.startsWith("+")) {
            body = body.substring(1);
        } else if (body.startsWith("-")) {
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
                if (!sign.isEmpty())
                    throw errorAt(start, "'" + raw + "' cannot be signed: a hexadecimal, octal or binary "
                            + "integer has no sign in TOML");
                return radixInteger(body.substring(2), radix == 'x' ? 16 : radix == 'o' ? 8 : 2, raw, start);
            }
        }

        String digits = withoutUnderscores(body, raw, start);
        refuseLeadingZero(digits, raw, start);
        try {
            if (isInteger(digits)) {
                Long.parseLong(digits);
                return sign + digits;
            }
            double parsed = Double.parseDouble(digits);
            if (Double.isInfinite(parsed) || Double.isNaN(parsed))
                throw errorAt(start, "'" + raw + "' is not a number TOML can read");
            return sign + digits;
        } catch (NumberFormatException notANumber) {
            throw errorAt(start, "'" + raw + "' is not a value TOML can read");
        }
    }

    private String radixInteger(String body, int radix, String raw, int start) throws IOException {
        String digits = withoutUnderscores(body, raw, start);
        if (digits.isEmpty())
            throw errorAt(start, "'" + raw + "' has no digits after its prefix");
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
    private String withoutUnderscores(String body, String raw, int start) throws IOException {
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
                    && Character.digit(body.charAt(i - 1), 16) >= 0
                    && Character.digit(body.charAt(i + 1), 16) >= 0;
            if (!between)
                throw errorAt(start, "'" + raw + "' has an underscore that is not between two digits");
        }
        return out.toString();
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
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
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
            char c = peek();
            if (c < 0x20 && c != '\t')
                throw error("a comment cannot hold a control character");
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
