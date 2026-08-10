/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.loaders;

import java.io.Serializable;

import static org.aeonbits.owner.util.Util.unsupported;

/**
 * The rules by which an {@link IniLoader} reads an INI file.
 * <p>
 * INI has no standard either, and the tools that read one disagree more than the <code>.env</code> tools do -
 * on the separator, on the comment characters, and above all on what a repeated key means. This class is the
 * bundle of answers, as {@link EnvDialect} is for <code>.env</code>: the named constants are the combinations
 * worth having, and any of them can be adjusted one answer at a time.
 * </p>
 * <p>
 * The default is {@link #INI}, the conservative common denominator: <code>=</code> separates,
 * <code>#</code> and <code>;</code> begin a comment only at the start of a line, a value keeps its quotes and
 * its backslashes, and a repeated key becomes a list.
 * </p>
 * <p>
 * No dialect interpolates. Python's <code>%(name)s</code> and OWNER's own <code>${…}</code> would otherwise
 * both be live in the same value and mean different things; {@link #PYTHON} therefore <b>refuses</b> a value
 * containing <code>%(…)s</code> rather than handing back the literal text, which would be the same
 * configuration silently meaning something else.
 * </p>
 *
 * @author Matteo Baccan
 * @see IniLoader
 * @since 2.0.0
 */
public final class IniDialect implements Serializable {

    private static final long serialVersionUID = 3186413516905193572L;

    /** What separates a key from its value. */
    public enum Separator {
        /** Only <code>=</code>, which is what git, systemd and the AWS credentials file use. */
        EQUALS,
        /** Either <code>=</code> or <code>:</code>, whichever comes first, as Python and Commons accept. */
        EQUALS_OR_COLON
    }

    /**
     * What to make of a key that appears more than once in the same section.
     * <p>
     * The field disagrees three ways: Python refuses the file, git and systemd and Commons Configuration read
     * a list, the AWS SDK keeps the last. {@link #LIST} is the default, and not by majority - it is the
     * answer this library already gave for repeated XML elements, and reading the same shape two ways would
     * be the incoherence.
     * </p>
     */
    public enum Duplicates {
        /** Number them, as repeated XML elements are numbered: <code>host[0]</code>, <code>host[1]</code>. */
        LIST,
        /** Refuse the file, naming the key and the line. */
        ERROR,
        /** Keep the first and ignore the rest. */
        FIRST,
        /** Keep the last, which is what the AWS SDK for Java does. */
        LAST
    }

    /** Whether a key is taken as written or folded. */
    public enum KeyCase {
        /** As written. */
        LITERAL,
        /** Lower case, as Python's <code>optionxform</code> does - to the keys, and not to the sections. */
        LOWER
    }

    /** What to make of a name on a line of its own, with no separator after it. */
    public enum BareKeys {
        /** Refuse the file: a line that is neither a comment nor an assignment is malformed. */
        ERROR,
        /** Skip the line. */
        IGNORE,
        /** Read it as <code>true</code>, which is what git config means by it. */
        TRUE
    }

    /** Whether the quotes around a value delimit it or belong to it. */
    public enum Quotes {
        /** Part of the value. */
        LITERAL,
        /** Delimiters, with <code>\n</code>, <code>\t</code>, <code>\b</code>, <code>\"</code> and
         * <code>\\</code> expanded inside them, as git config does. */
        DELIMIT
    }

    /** How a value may be continued on the line after it. */
    public enum Continuation {
        /** It may not. */
        NONE,
        /** A line ending in a backslash joins the next, the backslash and the newline being dropped. */
        BACKSLASH,
        /** A line indented more deeply than the key joins the value, as Python allows. */
        INDENT
    }

    /**
     * The conservative common denominator, and the default. Every tool in the survey agrees with all of it,
     * which is the point: it reads what they all read and transforms nothing.
     */
    public static final IniDialect INI = new IniDialect("ini", Separator.EQUALS, Duplicates.LIST,
            KeyCase.LITERAL, BareKeys.ERROR, Quotes.LITERAL, Continuation.NONE, false, false, false, false);

    /**
     * The rules of <code>git config</code>: a subsection in the section header, quotes that delimit and
     * escapes inside them, a comment that may follow a value, a backslash that continues a line, and a bare
     * key that means true.
     * <p>
     * The subsections are why this one is worth a name of its own rather than a set of rules. A
     * <code>[remote "origin"]</code> holding a <code>url</code> becomes <code>remote.origin.url</code>, which
     * is the key <code>git config</code> itself prints - so a mapping interface written against this reads
     * the same names the tool does.
     * </p>
     */
    public static final IniDialect GIT = INI.renamed("git")
            .withSubsections(true)
            .withQuotes(Quotes.DELIMIT)
            .withInlineComments(true)
            .withContinuation(Continuation.BACKSLASH)
            .withBareKeys(BareKeys.TRUE);

    /**
     * The rules of Python's <code>configparser</code>, as far as they can be honoured: <code>:</code> accepted
     * beside <code>=</code>, keys folded to lower case, a duplicate key refused, a value continued by
     * indentation, and the <code>[DEFAULT]</code> section inherited by every other one.
     * <p>
     * One thing cannot be honoured, and is refused rather than half-honoured: <code>ConfigParser</code>
     * interpolates <code>%(name)s</code> by default, and this library will not, since it expands
     * <code>${…}</code> itself after loading and across every source. A value containing
     * <code>%(…)s</code> read under this dialect is therefore an error naming the key, not a literal - a
     * configuration that means one thing to Python and another here, quietly, is the outcome worth spending
     * five lines to avoid.
     * </p>
     */
    public static final IniDialect PYTHON = INI.renamed("python")
            .withSeparator(Separator.EQUALS_OR_COLON)
            .withKeyCase(KeyCase.LOWER)
            .withDuplicates(Duplicates.ERROR)
            .withContinuation(Continuation.INDENT)
            .withDefaultSectionInherited(true)
            .withInterpolationRefused(true);

    private final String name;
    private final Separator separator;
    private final Duplicates duplicates;
    private final KeyCase keyCase;
    private final BareKeys bareKeys;
    private final Quotes quotes;
    private final Continuation continuation;
    private final boolean inlineComments;
    private final boolean subsections;
    private final boolean defaultSectionInherited;
    private final boolean interpolationRefused;

    private IniDialect(String name, Separator separator, Duplicates duplicates, KeyCase keyCase,
                       BareKeys bareKeys, Quotes quotes, Continuation continuation, boolean inlineComments,
                       boolean subsections, boolean defaultSectionInherited, boolean interpolationRefused) {
        this.name = name;
        this.separator = separator;
        this.duplicates = duplicates;
        this.keyCase = keyCase;
        this.bareKeys = bareKeys;
        this.quotes = quotes;
        this.continuation = continuation;
        this.inlineComments = inlineComments;
        this.subsections = subsections;
        this.defaultSectionInherited = defaultSectionInherited;
        this.interpolationRefused = interpolationRefused;
    }

    /**
     * Returns the named dialect: <code>ini</code>, <code>git</code> or <code>python</code>.
     *
     * @param name the name of the dialect, case insensitive.
     * @return the dialect with that name.
     * @throws UnsupportedOperationException if no dialect has that name.
     */
    public static IniDialect named(String name) {
        if (name != null) {
            String lower = name.trim().toLowerCase();
            if (INI.name.equals(lower)) return INI;
            if (GIT.name.equals(lower)) return GIT;
            if (PYTHON.name.equals(lower)) return PYTHON;
        }
        throw unsupported("Unknown INI dialect '%s'; the dialects are 'ini', 'git' and 'python'", name);
    }

    private IniDialect renamed(String newName) {
        return new IniDialect(newName, separator, duplicates, keyCase, bareKeys, quotes, continuation,
                inlineComments, subsections, defaultSectionInherited, interpolationRefused);
    }

    /**
     * Whether <code>:</code> separates a key from its value as well as <code>=</code>.
     *
     * @param value the new setting.
     * @return a dialect that differs from this one only in that setting.
     */
    public IniDialect withSeparator(Separator value) {
        return new IniDialect(name, value, duplicates, keyCase, bareKeys, quotes, continuation, inlineComments,
                subsections, defaultSectionInherited, interpolationRefused);
    }

    /**
     * What a key repeated inside one section means.
     *
     * @param value the new setting.
     * @return a dialect that differs from this one only in that setting.
     */
    public IniDialect withDuplicates(Duplicates value) {
        return new IniDialect(name, separator, value, keyCase, bareKeys, quotes, continuation, inlineComments,
                subsections, defaultSectionInherited, interpolationRefused);
    }

    /**
     * Whether a key is folded to lower case. Section names are never folded, which is what Python does and
     * therefore what a file written for it expects.
     *
     * @param value the new setting.
     * @return a dialect that differs from this one only in that setting.
     */
    public IniDialect withKeyCase(KeyCase value) {
        return new IniDialect(name, separator, duplicates, value, bareKeys, quotes, continuation, inlineComments,
                subsections, defaultSectionInherited, interpolationRefused);
    }

    /**
     * What a name with no separator after it means.
     *
     * @param value the new setting.
     * @return a dialect that differs from this one only in that setting.
     */
    public IniDialect withBareKeys(BareKeys value) {
        return new IniDialect(name, separator, duplicates, keyCase, value, quotes, continuation, inlineComments,
                subsections, defaultSectionInherited, interpolationRefused);
    }

    /**
     * Whether matching quotes around a value delimit it, with escapes expanded inside them.
     *
     * @param value the new setting.
     * @return a dialect that differs from this one only in that setting.
     */
    public IniDialect withQuotes(Quotes value) {
        return new IniDialect(name, separator, duplicates, keyCase, bareKeys, value, continuation, inlineComments,
                subsections, defaultSectionInherited, interpolationRefused);
    }

    /**
     * How a value may run onto the line after it.
     *
     * @param value the new setting.
     * @return a dialect that differs from this one only in that setting.
     */
    public IniDialect withContinuation(Continuation value) {
        return new IniDialect(name, separator, duplicates, keyCase, bareKeys, quotes, value, inlineComments,
                subsections, defaultSectionInherited, interpolationRefused);
    }

    /**
     * Whether a <code>#</code> or <code>;</code> after a value starts a comment. When it does not, one is part
     * of the value - which is why this is off by default: a password reading <code>abc#123</code> would
     * otherwise lose half of itself.
     *
     * @param value the new setting.
     * @return a dialect that differs from this one only in that setting.
     */
    public IniDialect withInlineComments(boolean value) {
        return new IniDialect(name, separator, duplicates, keyCase, bareKeys, quotes, continuation, value,
                subsections, defaultSectionInherited, interpolationRefused);
    }

    /**
     * Whether a quoted name in a section header is a subsection, so that <code>[remote "origin"]</code> is
     * <code>remote.origin</code>.
     *
     * @param value the new setting.
     * @return a dialect that differs from this one only in that setting.
     */
    public IniDialect withSubsections(boolean value) {
        return new IniDialect(name, separator, duplicates, keyCase, bareKeys, quotes, continuation, inlineComments,
                value, defaultSectionInherited, interpolationRefused);
    }

    /**
     * Whether a section named <code>DEFAULT</code> supplies values to every other section, as Python's does,
     * rather than being an ordinary section of that name.
     *
     * @param value the new setting.
     * @return a dialect that differs from this one only in that setting.
     */
    public IniDialect withDefaultSectionInherited(boolean value) {
        return new IniDialect(name, separator, duplicates, keyCase, bareKeys, quotes, continuation, inlineComments,
                subsections, value, interpolationRefused);
    }

    /**
     * Whether a value containing <code>%(name)s</code> is refused. It is what Python would interpolate and
     * this library will not, so passing it through would be the same file meaning two things.
     *
     * @param value the new setting.
     * @return a dialect that differs from this one only in that setting.
     */
    public IniDialect withInterpolationRefused(boolean value) {
        return new IniDialect(name, separator, duplicates, keyCase, bareKeys, quotes, continuation, inlineComments,
                subsections, defaultSectionInherited, value);
    }

    /** The name of this dialect, for a message that has to say which rules were in force. */
    public String name() {
        return name;
    }

    /** @return what separates a key from its value. */
    public Separator separator() {
        return separator;
    }

    /** @return what a key repeated in one section means. */
    public Duplicates duplicates() {
        return duplicates;
    }

    /** @return whether a key is folded to lower case. */
    public KeyCase keyCase() {
        return keyCase;
    }

    /** @return what a name with no separator means. */
    public BareKeys bareKeys() {
        return bareKeys;
    }

    /** @return whether quotes around a value delimit it. */
    public Quotes quotes() {
        return quotes;
    }

    /** @return how a value may run onto the next line. */
    public Continuation continuation() {
        return continuation;
    }

    /** @return whether a comment may follow a value. */
    public boolean isInlineComments() {
        return inlineComments;
    }

    /** @return whether a quoted name in a section header is a subsection. */
    public boolean isSubsections() {
        return subsections;
    }

    /** @return whether a <code>DEFAULT</code> section supplies values to the others. */
    public boolean isDefaultSectionInherited() {
        return defaultSectionInherited;
    }

    /** @return whether a value holding <code>%(name)s</code> is refused. */
    public boolean isInterpolationRefused() {
        return interpolationRefused;
    }

    @Override
    public String toString() {
        return name;
    }
}
