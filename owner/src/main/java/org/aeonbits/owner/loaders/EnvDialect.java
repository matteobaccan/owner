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
 * The rules by which a {@link DotEnvLoader} reads a <code>.env</code> file.
 * <p>
 * There is no standard for <code>.env</code>: every tool that reads one has invented its own rules, and they
 * disagree on the points that matter most — whether quotes delimit a value or belong to it above all. A dialect
 * is therefore not a format but a name for a bundle of answers, and this class is that bundle. The named
 * constants are the common combinations; any of them can be adjusted one answer at a time with the
 * <code>with…</code> methods, so a tool not named here can still be described.
 * </p>
 * <p>
 * The default is {@link #DOCKER}, which does nothing at all to a value: whatever follows the <code>=</code> is
 * the value. It is the behaviour of <code>docker run --env-file</code>, it is close to what SmallRye Config does
 * in the Java ecosystem, and on a format with no standard the defensible default is the one that transforms
 * nothing. When it is the wrong guess it is wrong visibly — a value arrives with its quotes still attached,
 * which is noticed at once — where stripping quotes that were meant to be kept removes characters and says
 * nothing.
 * </p>
 * <p>
 * No dialect interpolates <code>${…}</code>. OWNER expands variables in property values itself, after loading
 * and across every source, so a loader doing its own would expand them twice.
 * </p>
 *
 * @author Matteo Baccan
 * @see DotEnvLoader
 * @since 2.0.0
 */
public final class EnvDialect implements Serializable {

    private static final long serialVersionUID = 7092348271459086361L;

    /**
     * What to make of a line that names a variable without assigning anything to it, as in a bare
     * <code>HOME</code> on a line of its own.
     */
    public enum BareNames {
        /** Take the value from the environment of the running process, and skip the line if it has none. */
        FROM_ENVIRONMENT,
        /** Skip the line. */
        IGNORE,
        /** Refuse the file. */
        ERROR
    }

    /**
     * The rules of <code>docker run --env-file</code>, and the default: quotes are part of the value, nothing is
     * unescaped, <code>#</code> starts a comment only at the beginning of a line, and a bare name takes its
     * value from the environment.
     */
    public static final EnvDialect DOCKER =
            new EnvDialect("docker", false, false, false, false, false, false, BareNames.FROM_ENVIRONMENT);

    /**
     * The rules of the <code>dotenv</code> family — the Node, Python, Ruby and Go packages, and
     * <code>dotenv-java</code>: quotes delimit, escape sequences are expanded inside double quotes, a leading
     * <code>export</code> is dropped, a <code>#</code> preceded by whitespace starts a comment, and a quoted
     * value may span lines.
     */
    public static final EnvDialect DOTENV = DOCKER.renamed("dotenv")
            .withQuotesStripped(true)
            .withEscapesExpanded(true)
            .withExportPrefixStripped(true)
            .withInlineComments(true)
            .withMultilineValues(true)
            .withBareNames(BareNames.IGNORE);

    /**
     * The rules of <code>env_file</code> in Docker Compose, which — unlike <code>docker run --env-file</code>,
     * and this is the trap — does treat quotes as delimiters. It recognises no <code>export</code> prefix and
     * does not accept a value spanning lines.
     */
    public static final EnvDialect COMPOSE = DOCKER.renamed("compose")
            .withQuotesStripped(true)
            .withEscapesExpanded(true)
            .withInlineComments(true);

    private final String name;
    private final boolean quotesStripped;
    private final boolean escapesExpanded;
    private final boolean exportPrefixStripped;
    private final boolean inlineComments;
    private final boolean multilineValues;
    private final boolean lineContinuation;
    private final BareNames bareNames;

    private EnvDialect(String name, boolean quotesStripped, boolean escapesExpanded, boolean exportPrefixStripped,
                       boolean inlineComments, boolean multilineValues, boolean lineContinuation,
                       BareNames bareNames) {
        this.name = name;
        this.quotesStripped = quotesStripped;
        this.escapesExpanded = escapesExpanded;
        this.exportPrefixStripped = exportPrefixStripped;
        this.inlineComments = inlineComments;
        this.multilineValues = multilineValues;
        this.lineContinuation = lineContinuation;
        this.bareNames = bareNames;
    }

    /**
     * Returns the named dialect: <code>docker</code>, <code>dotenv</code> or <code>compose</code>.
     *
     * @param name the name of the dialect, case insensitive.
     * @return the dialect with that name.
     * @throws UnsupportedOperationException if no dialect has that name.
     */
    public static EnvDialect named(String name) {
        if (name != null) {
            String lower = name.trim().toLowerCase();
            if (DOCKER.name.equals(lower)) return DOCKER;
            if (DOTENV.name.equals(lower)) return DOTENV;
            if (COMPOSE.name.equals(lower)) return COMPOSE;
        }
        throw unsupported("Unknown .env dialect '%s'; the dialects are 'docker', 'dotenv' and 'compose'", name);
    }

    private EnvDialect renamed(String newName) {
        return new EnvDialect(newName, quotesStripped, escapesExpanded, exportPrefixStripped, inlineComments,
                multilineValues, lineContinuation, bareNames);
    }

    /**
     * Whether a value wrapped in matching quotes has them removed, rather than keeping them as part of it.
     *
     * @param value the new setting.
     * @return a dialect that differs from this one only in that setting.
     */
    public EnvDialect withQuotesStripped(boolean value) {
        return new EnvDialect(name, value, escapesExpanded, exportPrefixStripped, inlineComments, multilineValues,
                lineContinuation, bareNames);
    }

    /**
     * Whether <code>\n</code>, <code>\t</code> and their like are expanded inside a double-quoted value. Only
     * meaningful when quotes are stripped; an escape sequence that is not recognised is left as written.
     *
     * @param value the new setting.
     * @return a dialect that differs from this one only in that setting.
     */
    public EnvDialect withEscapesExpanded(boolean value) {
        return new EnvDialect(name, quotesStripped, value, exportPrefixStripped, inlineComments, multilineValues,
                lineContinuation, bareNames);
    }

    /**
     * Whether a leading <code>export</code> is dropped, so that the file can also be sourced by a shell.
     *
     * @param value the new setting.
     * @return a dialect that differs from this one only in that setting.
     */
    public EnvDialect withExportPrefixStripped(boolean value) {
        return new EnvDialect(name, quotesStripped, escapesExpanded, value, inlineComments, multilineValues,
                lineContinuation, bareNames);
    }

    /**
     * Whether a <code>#</code> preceded by whitespace and outside quotes starts a comment that runs to the end of
     * the line. When it does not, a <code>#</code> anywhere but at the start of a line is part of the value.
     *
     * @param value the new setting.
     * @return a dialect that differs from this one only in that setting.
     */
    public EnvDialect withInlineComments(boolean value) {
        return new EnvDialect(name, quotesStripped, escapesExpanded, exportPrefixStripped, value, multilineValues,
                lineContinuation, bareNames);
    }

    /**
     * Whether a quoted value may run past the end of its line, up to the closing quote. Only meaningful when
     * quotes are stripped.
     *
     * @param value the new setting.
     * @return a dialect that differs from this one only in that setting.
     */
    public EnvDialect withMultilineValues(boolean value) {
        return new EnvDialect(name, quotesStripped, escapesExpanded, exportPrefixStripped, inlineComments, value,
                lineContinuation, bareNames);
    }

    /**
     * Whether a line ending in a backslash is joined to the one after it, as an
     * <code>EnvironmentFile</code> read by systemd allows.
     *
     * @param value the new setting.
     * @return a dialect that differs from this one only in that setting.
     */
    public EnvDialect withLineContinuation(boolean value) {
        return new EnvDialect(name, quotesStripped, escapesExpanded, exportPrefixStripped, inlineComments,
                multilineValues, value, bareNames);
    }

    /**
     * What to make of a line that names a variable without assigning to it.
     *
     * @param value the new setting.
     * @return a dialect that differs from this one only in that setting.
     */
    public EnvDialect withBareNames(BareNames value) {
        if (value == null)
            throw new IllegalArgumentException("bare names policy can't be null");
        return new EnvDialect(name, quotesStripped, escapesExpanded, exportPrefixStripped, inlineComments,
                multilineValues, lineContinuation, value);
    }

    /**
     * The name of the dialect this one is derived from. It does not change when a setting is adjusted, so it
     * identifies the starting point rather than the result.
     *
     * @return the name.
     */
    public String name() {
        return name;
    }

    /** @return whether matching quotes around a value are removed. */
    public boolean isQuotesStripped() {
        return quotesStripped;
    }

    /** @return whether escape sequences are expanded inside a double-quoted value. */
    public boolean isEscapesExpanded() {
        return escapesExpanded;
    }

    /** @return whether a leading <code>export</code> is dropped. */
    public boolean isExportPrefixStripped() {
        return exportPrefixStripped;
    }

    /** @return whether a <code>#</code> preceded by whitespace starts a comment. */
    public boolean isInlineComments() {
        return inlineComments;
    }

    /** @return whether a quoted value may span lines. */
    public boolean isMultilineValues() {
        return multilineValues;
    }

    /** @return whether a line ending in a backslash is joined to the next. */
    public boolean isLineContinuation() {
        return lineContinuation;
    }

    /** @return what is done with a line that names a variable without assigning to it. */
    public BareNames bareNames() {
        return bareNames;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof EnvDialect)) return false;
        EnvDialect that = (EnvDialect) other;
        return quotesStripped == that.quotesStripped
                && escapesExpanded == that.escapesExpanded
                && exportPrefixStripped == that.exportPrefixStripped
                && inlineComments == that.inlineComments
                && multilineValues == that.multilineValues
                && lineContinuation == that.lineContinuation
                && bareNames == that.bareNames
                && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + (quotesStripped ? 1 : 0);
        result = 31 * result + (escapesExpanded ? 1 : 0);
        result = 31 * result + (exportPrefixStripped ? 1 : 0);
        result = 31 * result + (inlineComments ? 1 : 0);
        result = 31 * result + (multilineValues ? 1 : 0);
        result = 31 * result + (lineContinuation ? 1 : 0);
        return 31 * result + bareNames.hashCode();
    }

    @Override
    public String toString() {
        return "EnvDialect[" + name
                + ", quotesStripped=" + quotesStripped
                + ", escapesExpanded=" + escapesExpanded
                + ", exportPrefixStripped=" + exportPrefixStripped
                + ", inlineComments=" + inlineComments
                + ", multilineValues=" + multilineValues
                + ", lineContinuation=" + lineContinuation
                + ", bareNames=" + bareNames + "]";
    }
}
