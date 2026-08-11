/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.formats.json;

import org.aeonbits.owner.loaders.PropertyKeys;

import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Reads a JSON document into properties, flattening it as it goes.
 * <p>
 * <b>RFC 8259</b>, and no more: no comments, no trailing commas, no unquoted names, no single quotes.
 * Those are JSON5 and JavaScript, and a parser that accepted them would read a file that other tools
 * refuse — which is the failure a configuration library can least afford, since the same file is nearly
 * always read by something else as well.
 * </p>
 * <p>
 * There is no intermediate tree: a name is pushed, its value emitted, the name popped. A configuration is
 * read once at startup and thrown away, so a model of the document would be built only to be walked once
 * and dropped. The keys are made by {@link PropertyKeys}, the same convention every loader flattens with,
 * which is what lets a nested interface read what this produces.
 * </p>
 *
 * @author Matteo Baccan
 * @since 2.0.0
 */
final class JsonParser {

    private final String text;
    private final Properties into;
    private int at;

    JsonParser(String text, Properties into) {
        this.text = text;
        this.into = into;
    }

    /**
     * Reads the whole document.
     *
     * @throws IOException if it is not a JSON document, or not one a configuration can be made of.
     */
    void parse() throws IOException {
        skipWhitespace();
        if (!more() || peek() != '{')
            throw error("a configuration has to be a JSON object, a set of names and values");
        object(null);
        skipWhitespace();
        if (more())
            throw error("there is more text after the end of the document");
    }

    /**
     * An object, whose names become the next part of the key.
     * <p>
     * <b>A repeated name is refused.</b> RFC 8259 leaves the case undefined and most parsers keep the last
     * one, which reads half of an ambiguous document rather than saying it is ambiguous. JSON has a real
     * way of writing a list, so a repeated name is a mistake rather than a shorthand — which is why the
     * answer here differs from the one INI and XML give to a repeated key.
     * </p>
     */
    private void object(String parent) throws IOException {
        expect('{');
        skipWhitespace();
        if (peekIs('}')) {
            at++;
            return;
        }

        Set<String> seen = new HashSet<>();
        while (true) {
            skipWhitespace();
            int nameAt = at;
            String name = string();
            if (!seen.add(name))
                throw errorAt(nameAt, "the name '" + name + "' is given twice in the same object");

            skipWhitespace();
            expect(':');
            skipWhitespace();
            value(PropertyKeys.child(parent, name));

            skipWhitespace();
            if (peekIs(',')) {
                at++;
                continue;
            }
            expect('}');
            return;
        }
    }

    /**
     * An array, whose elements are numbered from zero.
     * <p>
     * An <b>empty</b> array writes an empty value rather than nothing, because an empty value is already
     * read as an empty collection and that is a faithful reading of <code>[]</code> — including the
     * consequence that it overrides a <code>@DefaultValue</code>, which is what the document says.
     * </p>
     */
    private void array(String key) throws IOException {
        expect('[');
        skipWhitespace();
        if (peekIs(']')) {
            at++;
            into.setProperty(key, "");
            return;
        }

        int index = 0;
        while (true) {
            skipWhitespace();
            value(PropertyKeys.element(key, index++));
            skipWhitespace();
            if (peekIs(',')) {
                at++;
                continue;
            }
            expect(']');
            return;
        }
    }

    private void value(String key) throws IOException {
        if (!more())
            throw error("a value was expected");
        char c = peek();
        switch (c) {
            case '{':
                object(key);
                return;
            case '[':
                array(key);
                return;
            case '"':
                into.setProperty(key, string());
                return;
            case 't':
                literal("true");
                into.setProperty(key, "true");
                return;
            case 'f':
                literal("false");
                into.setProperty(key, "false");
                return;
            case 'n':
                literal("null");
                // and nothing is written: see JsonLoader, where the reasoning for it lives
                return;
            default:
                into.setProperty(key, number());
        }
    }

    /**
     * A string, with the six escapes and <code>\ uXXXX</code> expanded.
     * <p>
     * A surrogate pair needs nothing of its own: the two escapes are two <code>char</code>s and a Java
     * string is UTF-16, so appending both is what makes them one character again.
     * </p>
     */
    private String string() throws IOException {
        expect('"');
        StringBuilder value = new StringBuilder();
        while (true) {
            if (!more())
                throw error("the string was never closed");
            char c = text.charAt(at++);
            if (c == '"')
                return value.toString();
            if (c < 0x20)
                throw errorAt(at - 1, "a string cannot hold the control character 0x"
                        + Integer.toHexString(c) + "; write it as an escape");
            if (c != '\\') {
                value.append(c);
                continue;
            }
            value.append(escape());
        }
    }

    private char escape() throws IOException {
        if (!more())
            throw error("the string ends in the middle of an escape");
        char c = text.charAt(at++);
        switch (c) {
            case '"': return '"';
            case '\\': return '\\';
            case '/': return '/';
            case 'b': return '\b';
            case 'f': return '\f';
            case 'n': return '\n';
            case 'r': return '\r';
            case 't': return '\t';
            case 'u': return unicode();
            default: throw errorAt(at - 1, "'\\" + c + "' is not an escape");
        }
    }

    /**
     * The four hexadecimal digits of a <code>\ u</code> escape, read and added up in the same pass.
     * <p>
     * Building the number here rather than handing the four characters to {@code Integer.parseInt} is not
     * a micro-optimisation: it is what makes the absence of a failure visible. Four digits that have each
     * been checked cannot overflow a <code>char</code> and cannot fail to parse, but a reader — and an
     * analyser — has to follow the loop above to know it, and only one of the two ever does.
     * </p>
     */
    private char unicode() throws IOException {
        if (at + 4 > text.length())
            throw error("a \\u escape needs four hexadecimal digits");
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int digit = Character.digit(text.charAt(at + i), 16);
            if (digit < 0)
                throw errorAt(at, "'" + text.substring(at, at + 4) + "' is not four hexadecimal digits");
            value = value * 16 + digit;
        }
        at += 4;
        return (char) value;
    }

    /**
     * A number, checked for shape and then kept <b>as it was written</b>.
     * <p>
     * Reading it into a <code>double</code> and printing it back would change what the file says:
     * <code>1e3</code> would become <code>1000.0</code>, a long past 2^53 would lose its last digits, and
     * a trailing zero that mattered to somebody would go. Every value in this library is text until a
     * converter is asked for a type, and a number is no exception.
     * </p>
     */
    private String number() throws IOException {
        int start = at;
        boolean signed = peekIs('-');
        if (signed)
            at++;
        integer(signed);
        if (peekIs('.')) {
            at++;
            digits("a number needs a digit after its decimal point");
        }
        if (peekIs('e') || peekIs('E')) {
            at++;
            if (peekIs('+') || peekIs('-'))
                at++;
            digits("a number needs a digit after its exponent");
        }
        return text.substring(start, at);
    }

    /**
     * The part before the decimal point, which is the one place JSON is stricter than a reader expects:
     * <b>a leading zero is not allowed</b>. Accepting <code>01</code> would mean reading a file that every
     * other tool refuses, which is the thing a configuration library can least afford — the same file is
     * nearly always read by something else too.
     * <p>
     * It is also where a value that begins no JSON value at all is caught, since by here every other
     * beginning has been tried.
     * </p>
     */
    private void integer(boolean signed) throws IOException {
        if (!more() || peek() < '0' || peek() > '9') {
            if (signed)
                throw error("a number needs a digit after its sign");
            throw error(more() ? "'" + peek() + "' begins no value that JSON has"
                    : "a value was expected and the document ends");
        }
        if (peek() != '0') {
            digits("a number needs a digit");
            return;
        }
        at++;
        if (more() && peek() >= '0' && peek() <= '9')
            throw errorAt(at - 1, "a number cannot begin with a zero: write 1 rather than 01");
    }

    private void digits(String wanted) throws IOException {
        int start = at;
        while (more() && peek() >= '0' && peek() <= '9')
            at++;
        if (start == at)
            throw error(wanted);
    }

    private void literal(String word) throws IOException {
        if (!text.startsWith(word, at))
            throw error("'" + word + "' was expected");
        at += word.length();
    }

    private void expect(char c) throws IOException {
        if (!more() || text.charAt(at) != c)
            throw error("'" + c + "' was expected");
        at++;
    }

    private void skipWhitespace() {
        while (more()) {
            char c = text.charAt(at);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r')
                return;
            at++;
        }
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
