/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.formats.yaml;

import org.aeonbits.owner.loaders.PropertyKeys;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Reads the subset of YAML a configuration is written in, flattening it as it goes.
 * <p>
 * <b>What makes this 700 lines rather than 5000 is what the loader contract does not ask for.</b> We hand
 * back text and the mapping interface declares the types, so YAML's implicit type resolution — the one
 * where <code>no</code> becomes <code>false</code> and a Norwegian country code turns into a boolean — is
 * work that simply does not arise here. A scalar is kept as it was written, and what it means is the
 * business of the converter the method asks for.
 * </p>
 * <p>
 * <b>What is left out raises an error and is never guessed at.</b> Anchors, aliases and merge keys, tags,
 * complex keys, a value continued on the next line without <code>|</code> or <code>&gt;</code>, and a
 * second document in the same file: each of those is refused by name, with the line it is on. A parser
 * that half-understood them would change the meaning of a file rather than decline it, which is the one
 * outcome worth avoiding.
 * </p>
 *
 * @author Matteo Baccan
 * @since 2.0.0
 */
final class YamlParser {

    private final List<String> lines;
    private final Properties into;
    private int at;

    YamlParser(String text, Properties into) {
        this.lines = new ArrayList<>();
        for (String line : text.split("\n", -1))
            this.lines.add(line.endsWith("\r") ? line.substring(0, line.length() - 1) : line);
        this.into = into;
    }

    void parse() throws IOException {
        skipBlanks();
        if (more() && current().trim().equals("---")) {
            at++;
            skipBlanks();
        }
        if (!more())
            return;

        int indent = indentOf(current());
        if (indent != 0)
            throw error("the document begins indented, so nothing says what it is indented under");
        block(0, null);

        skipBlanks();
        if (more())
            throw error(current().trim().startsWith("---")
                    ? "a second document begins here, and a configuration is one document. Split the file, "
                            + "or keep the document you mean and remove the others"
                    : "this line is outside everything above it");
    }

    /** A block is a mapping or a sequence, and its first line says which. */
    private void block(int indent, String prefix) throws IOException {
        if (isItem(current().substring(indent)))
            sequence(indent, prefix);
        else
            mapping(indent, prefix);
    }

    private void mapping(int indent, String prefix) throws IOException {
        Set<String> seen = new HashSet<>();
        while (true) {
            skipBlanks();
            if (!more())
                return;

            int found = indentOf(current());
            if (found < indent)
                return;
            String content = current().substring(found);
            if (isDocumentMarker(content))
                return;
            if (found > indent)
                throw error("this line is indented further than the one above it, and nothing here opens a "
                        + "block: a value on more than one line needs '|' or '>'");
            if (isItem(content))
                throw error("a sequence item where a name was expected");

            refuseWhatIsNotSupported(content);
            int colon = colonOf(content);
            if (colon < 0)
                throw error("a name here needs a colon after it");

            String name = unquote(content.substring(0, colon).trim());
            if (name.isEmpty())
                throw error("a name here is empty");
            if (!seen.add(name))
                throw error("the name '" + name + "' is given twice in the same block");

            String rest = content.substring(colon + 1).trim();
            at++;
            value(rest, indent, PropertyKeys.child(prefix, name));
        }
    }

    private void sequence(int indent, String prefix) throws IOException {
        int index = 0;
        while (true) {
            skipBlanks();
            if (!more())
                return;

            int found = indentOf(current());
            if (found < indent)
                return;
            String content = current().substring(found);
            if (isDocumentMarker(content))
                return;
            if (found > indent)
                throw error("this line is indented further than the item above it");
            if (!isItem(content))
                return;

            String key = PropertyKeys.element(prefix, index++);
            String rest = content.length() > 1 ? content.substring(2).trim() : "";
            if (rest.isEmpty()) {
                at++;
                nested(indent, key, "an item with nothing on it needs something indented under it");
                continue;
            }

            // "- host: alpha" opens a mapping whose first entry is on this very line. Rewriting the line
            // without its dash, at the column the content already sits in, is what lets the ordinary
            // mapping loop read it and everything indented to match
            if (colonOf(rest) >= 0 && !isFlow(rest)) {
                int inner = found + 2;
                lines.set(at, blanks(inner) + rest);
                mapping(inner, key);
                continue;
            }

            at++;
            scalar(rest, key);
        }
    }

    /** What follows a name: nothing, a block below, a block scalar, or a scalar on the same line. */
    private void value(String rest, int indent, String key) throws IOException {
        if (rest.isEmpty()) {
            skipBlanks();
            if (more() && indentOf(current()) > indent) {
                block(indentOf(current()), key);
                return;
            }
            // 'host:' with nothing under it is a null, and a null writes no key: see YamlLoader
            return;
        }
        if (rest.equals("|") || rest.equals(">") || rest.startsWith("|") || rest.startsWith(">")) {
            blockScalar(rest, indent, key);
            return;
        }
        int here = at - 1;
        scalar(rest, key);
        skipBlanks();
        if (more() && indentOf(current()) > indentOf(lines.get(here)))
            throw error("this line is indented under a name that already has a value on it: a value on "
                    + "more than one line needs '|' to keep its line breaks or '>' to fold them");
    }

    /**
     * A block scalar, <code>|</code> to keep the line breaks and <code>&gt;</code> to fold them into
     * spaces, with the three chomping indicators.
     */
    private void blockScalar(String header, int indent, String key) throws IOException {
        boolean folded = header.charAt(0) == '>';
        String rest = header.substring(1).trim();
        boolean keep = rest.equals("+");
        boolean strip = rest.equals("-");
        if (!rest.isEmpty() && !keep && !strip)
            throw error("'" + rest + "' is not something that can follow '" + header.charAt(0)
                    + "': only '+' to keep the blank lines at the end and '-' to strip them");

        List<String> collected = new ArrayList<>();
        int blockIndent = -1;
        while (more()) {
            String line = current();
            if (line.trim().isEmpty()) {
                collected.add("");
                at++;
                continue;
            }
            int found = indentOf(line);
            if (found <= indent)
                break;
            if (blockIndent < 0)
                blockIndent = found;
            if (found < blockIndent)
                break;
            collected.add(line.substring(blockIndent));
            at++;
        }

        while (!collected.isEmpty() && collected.get(collected.size() - 1).isEmpty())
            collected.remove(collected.size() - 1);

        StringBuilder value = new StringBuilder();
        for (int i = 0; i < collected.size(); i++) {
            String line = collected.get(i);
            if (i > 0)
                value.append(folded && !line.isEmpty() && !collected.get(i - 1).isEmpty() ? " " : "\n");
            value.append(line);
        }
        if (!strip && value.length() > 0)
            value.append('\n');
        if (keep)
            value.append('\n');

        into.setProperty(key, value.toString());
    }

    /** A scalar written on one line: plain, quoted, or a flow collection. */
    private void scalar(String text, String key) throws IOException {
        refuseWhatIsNotSupported(text);
        if (isFlow(text)) {
            flow(text, key);
            return;
        }
        String value = unquote(cutComment(text));
        if (isNull(value))
            return;
        into.setProperty(key, value);
    }

    private static boolean isNull(String value) {
        return value.isEmpty() || value.equals("~") || value.equals("null");
    }

    // ---------------------------------------------------------------- flow style

    private static boolean isFlow(String text) {
        return text.startsWith("[") || text.startsWith("{");
    }

    /**
     * The flow styles, <code>[a, b]</code> and <code>{a: 1}</code>. They are here because a configuration
     * uses them daily — <code>ports: [80, 443]</code> — and because a JSON document is valid YAML, so a
     * subset without them would refuse files that are not exotic in the least.
     */
    private void flow(String text, String key) throws IOException {
        String body = cutFlowComment(text);
        List<String> parts = split(body.substring(1, body.length() - 1).trim(),
                body.charAt(0) == '[' ? ']' : '}');
        boolean sequence = body.charAt(0) == '[';
        if (parts.isEmpty()) {
            if (sequence)
                into.setProperty(key, "");
            return;
        }
        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i).trim();
            if (sequence) {
                scalar(part, PropertyKeys.element(key, i));
                continue;
            }
            int colon = colonOf(part);
            if (colon < 0)
                throw error("'" + part + "' needs a colon: it is inside braces, so it is a name and a value");
            scalar(part.substring(colon + 1).trim(),
                    PropertyKeys.child(key, unquote(part.substring(0, colon).trim())));
        }
    }

    /** The closing bracket, found by counting nesting and ignoring what is inside quotes. */
    private String cutFlowComment(String text) throws IOException {
        int depth = 0;
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
                continue;
            }
            if (c == '"' || c == '\'') quote = c;
            else if (c == '[' || c == '{') depth++;
            else if (c == ']' || c == '}') {
                depth--;
                if (depth == 0)
                    return text.substring(0, i + 1);
            }
        }
        throw error("this collection is never closed");
    }

    private List<String> split(String body, char closing) throws IOException {
        List<String> parts = new ArrayList<>();
        if (body.isEmpty())
            return parts;

        int depth = 0;
        char quote = 0;
        int start = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
                continue;
            }
            if (c == '"' || c == '\'') quote = c;
            else if (c == '[' || c == '{') depth++;
            else if (c == ']' || c == '}') depth--;
            else if (c == ',' && depth == 0) {
                parts.add(body.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(body.substring(start));
        if (parts.size() > 1 && parts.get(parts.size() - 1).trim().isEmpty())
            throw error("there is a comma after the last item of this " + (closing == ']' ? "list" : "block"));
        return parts;
    }

    // ---------------------------------------------------------------- text

    /**
     * Refuses what this subset does not read, by name and on the line it is on.
     * <p>
     * Guessing at any of these would change what a file means rather than decline to read it, which is the
     * one thing a configuration library must not do.
     * </p>
     */
    private void refuseWhatIsNotSupported(String content) throws IOException {
        if (content.startsWith("<<:"))
            throw error("a merge key is not read: write the keys out, or merge the sources with "
                    + "@LoadPolicy(MERGE)");
        if (content.startsWith("? "))
            throw error("a complex key is not read: a name here is a plain scalar");
        if (content.startsWith("&") || content.startsWith("*"))
            throw error("an anchor or an alias is not read: write the value where it is used");
        if (content.startsWith("!"))
            throw error("a tag is not read: the type of a value is decided by the method that reads it");
    }

    /** The colon that separates a name from its value: the first one outside quotes and outside brackets. */
    private static int colonOf(String text) {
        int depth = 0;
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
                continue;
            }
            if (c == '"' || c == '\'') quote = c;
            else if (c == '[' || c == '{') depth++;
            else if (c == ']' || c == '}') depth--;
            else if (c == ':' && depth == 0 && (i + 1 == text.length()
                    || text.charAt(i + 1) == ' ' || text.charAt(i + 1) == '\t'))
                return i;
        }
        return -1;
    }

    /** A comment is a hash at the beginning or after a space, and only outside quotes. */
    private static String cutComment(String text) {
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
                continue;
            }
            if (c == '"' || c == '\'')
                quote = c;
            else if (c == '#' && (i == 0 || text.charAt(i - 1) == ' ' || text.charAt(i - 1) == '\t'))
                return text.substring(0, i).trim();
        }
        return text.trim();
    }

    /**
     * Quotes, if there are any, and the escapes inside double quotes. A single-quoted scalar has one
     * escape and it is <code>''</code>, which is the whole of what YAML says about it.
     */
    private String unquote(String text) throws IOException {
        if (text.length() < 2)
            return text;
        char quote = text.charAt(0);
        if (quote != '"' && quote != '\'' || text.charAt(text.length() - 1) != quote)
            return text;

        String inner = text.substring(1, text.length() - 1);
        if (quote == '\'')
            return inner.replace("''", "'");

        StringBuilder value = new StringBuilder(inner.length());
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c != '\\' || i + 1 == inner.length()) {
                value.append(c);
                continue;
            }
            char escaped = inner.charAt(++i);
            switch (escaped) {
                case 'n': value.append('\n'); break;
                case 't': value.append('\t'); break;
                case 'r': value.append('\r'); break;
                case 'b': value.append('\b'); break;
                case 'f': value.append('\f'); break;
                case '0': value.append('\0'); break;
                case '"': value.append('"'); break;
                case '\\': value.append('\\'); break;
                case 'u': value.append(unicode(inner, i + 1)); i += 4; break;
                default: value.append('\\').append(escaped);
            }
        }
        return value.toString();
    }

    /** The four digits of a <code>\ u</code> escape, read and added up in one pass: see JsonParser. */
    private char unicode(String text, int from) throws IOException {
        if (from + 4 > text.length())
            throw error("a \\u escape needs four hexadecimal digits");
        int value = 0;
        for (int i = from; i < from + 4; i++) {
            int digit = Character.digit(text.charAt(i), 16);
            if (digit < 0)
                throw error("'" + text.substring(from, from + 4) + "' is not four hexadecimal digits");
            value = value * 16 + digit;
        }
        return (char) value;
    }

    // ---------------------------------------------------------------- lines

    private static boolean isItem(String content) {
        return content.equals("-") || content.startsWith("- ");
    }

    /**
     * The line that opens another document. It ends whatever block is being read, so that the complaint
     * comes from {@link #parse()} — which knows that a configuration is one document and can say so —
     * rather than from a mapping wondering why this line has no colon.
     */
    private static boolean isDocumentMarker(String content) {
        return content.equals("---") || content.startsWith("--- ");
    }

    private static String blanks(int count) {
        StringBuilder blanks = new StringBuilder(count);
        for (int i = 0; i < count; i++)
            blanks.append(' ');
        return blanks.toString();
    }

    private void nested(int indent, String key, String complaint) throws IOException {
        skipBlanks();
        if (!more() || indentOf(current()) <= indent)
            throw error(complaint);
        block(indentOf(current()), key);
    }

    private void skipBlanks() {
        while (more()) {
            String trimmed = current().trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#") && !trimmed.equals("..."))
                return;
            at++;
        }
    }

    private boolean more() {
        return at < lines.size();
    }

    private String current() {
        return lines.get(at);
    }

    /**
     * How far a line is indented. A tab is refused rather than counted: YAML does not allow one in
     * indentation, and a file mixing the two reads differently in every editor it is opened in.
     */
    private int indentOf(String line) {
        int indent = 0;
        while (indent < line.length() && line.charAt(indent) == ' ')
            indent++;
        return indent;
    }

    private IOException error(String what) {
        int line = Math.min(at + 1, lines.size());
        return new IOException(String.format("Line %d: %s", line, what));
    }

    /** Refuses a tab used as indentation, which YAML forbids and which no two editors agree about. */
    void refuseTabs() throws IOException {
        for (at = 0; at < lines.size(); at++) {
            String line = current();
            for (int i = 0; i < line.length(); i++) {
                if (line.charAt(i) == '\t')
                    throw error("a tab is used to indent this line, which YAML does not allow: use spaces");
                if (line.charAt(i) != ' ')
                    break;
            }
        }
        at = 0;
    }
}
