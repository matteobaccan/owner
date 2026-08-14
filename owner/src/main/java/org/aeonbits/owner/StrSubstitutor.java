/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.compile;
import static org.aeonbits.owner.util.Util.system;
import static org.aeonbits.owner.util.Util.unsupported;

/**
 * <p>
 * Substitutes variables within a string by values.
 * </p>
 * <p>
 * This class takes a piece of text and substitutes all the variables within it. The definition of a variable is
 * <code>${variableName}</code>.
 * </p>
 * <p>
 * Typical usage of this class follows the following pattern: First an instance is created and initialized with the
 * values that contains the values for the available variables. If a prefix and/or suffix for variables should be used
 * other than the default ones, the appropriate settings can be performed. After that the <code>replace()</code> method
 * can be called passing in the source text for interpolation. In the returned text all variable references (as long as
 * their values are known) will be resolved. The following example demonstrates this:
 * </p>
 * <pre>
 * Map valuesMap = new HashMap();
 * valuesMap.put(&quot;animal&quot;, &quot;quick brown fox&quot;);
 * valuesMap.put(&quot;target&quot;, &quot;lazy dog&quot;);
 * String templateString = &quot;The ${animal} jumped over the ${target}.&quot;;
 * StrSubstitutor sub = new StrSubstitutor(valuesMap);
 * String resolvedString = sub.replace(templateString);
 * </pre>
 * yielding:
 * <pre>
 *      The quick brown fox jumped over the lazy dog.
 * </pre>
 * <p>
 * A variable can be nested inside another one, in which case the inner expression is expanded first and the
 * result is looked up as a key: with <code>env=dev</code>, <code>${servers.${env}.url}</code> reads the key
 * <code>servers.dev.url</code>. Nesting can be switched off for the whole JVM through the
 * {@link #NESTED_VARIABLE_EXPANSION} system property.
 * </p>
 *
 * @author Luigi R. Viggiano
 */
class StrSubstitutor implements Serializable {

    /**
     * Name of the system property switching off the expansion of nested variables, for the whole JVM:
     * <code>-Downer.nested.variable.expansion=false</code> restores the substitution of the previous
     * releases, in which every <code>${</code> is closed by the first <code>}</code> that follows it.
     */
    static final String NESTED_VARIABLE_EXPANSION = "owner.nested.variable.expansion";

    private static final Pattern PATTERN = compile("\\$\\{(.+?)\\}");
    private static final char DEFAULT_VALUE_SEPARATOR = ':';
    private static final String VARIABLE_START = "${";

    private final Properties values;
    private final boolean nested;

    /**
     * The handlers a <code>${$name::payload}</code> marker can name. Never <code>null</code>: an empty
     * one is what a substitution with no handlers registered uses, so that a marker written against a
     * factory that has none is refused by the same path as a misspelt name, and says so.
     */
    private final HandlersManager handlers;

    /**
     * Whether an expression that resolves to nothing is refused rather than replaced by the empty string.
     * See {@link #resolve}.
     */
    private final boolean strict;

    /**
     * Creates a new instance and initializes it. Uses defaults for variable prefix and suffix and the escaping
     * character.
     *
     * @param values the variables' values, may be null
     */
    StrSubstitutor(Properties values) {
        this(values, false);
    }

    /**
     * @param values the variables' values, may be null
     * @param strict <code>true</code> to refuse an expression that resolves to nothing, rather than
     *               replacing it with the empty string. See <code>owner.strict</code> on {@link Factory}.
     */
    StrSubstitutor(Properties values, boolean strict) {
        this(values, strict, new HandlersManager());
    }

    /**
     * @param values   the variables' values, may be null
     * @param strict   <code>true</code> to refuse an expression that resolves to nothing, rather than
     *                 replacing it with the empty string. See <code>owner.strict</code> on {@link Factory}.
     * @param handlers the handlers a <code>${$name::payload}</code> marker may name.
     */
    StrSubstitutor(Properties values, boolean strict, HandlersManager handlers) {
        this.values = values;
        this.strict = strict;
        this.handlers = handlers;
        this.nested = !"false".equalsIgnoreCase(system().getProperty(NESTED_VARIABLE_EXPANSION));
    }

    /**
     * Replaces all the occurrences of variables with their matching values from the resolver using the given source
     * string as a template.
     *
     * @param source the string to replace in, null returns null
     * @return the result of the replace operation
     */
    String replace(String source) {
        if (source == null)
            return null;
        return replace(source, new LinkedHashSet<String>());
    }

    /**
     * @param resolving the expressions being resolved further up the recursion, in order, used to detect a
     *                  circular reference before it exhausts the stack.
     */
    private String replace(String source, Set<String> resolving) {
        return nested ? replaceNested(source, resolving) : replaceFlat(source, resolving);
    }

    /**
     * The substitution as it was up to 1.0.12, kept for the {@link #NESTED_VARIABLE_EXPANSION} switch: the
     * expression is whatever sits between <code>${</code> and the first <code>}</code> that follows it, and it
     * is looked up as it is written.
     */
    private String replaceFlat(String source, Set<String> resolving) {
        Matcher m = PATTERN.matcher(source);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String replacement = resolve(m.group(1), resolving);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * The substitution supporting nested variables: a <code>${</code> is closed by the <code>}</code> that
     * matches it, so that the expression can itself contain variables, and the expression is expanded before
     * being looked up as a key. That is what makes <code>${servers.${env}.url}</code> read the key named by
     * the value of <code>env</code>.
     * <p>
     * Only the <code>${</code> sequence opens a nesting level: a lone brace inside an expression is ordinary
     * text, exactly as it was before, so a key such as <code>a{b</code> keeps resolving.</p>
     */
    private String replaceNested(String source, Set<String> resolving) {
        StringBuilder sb = new StringBuilder();
        int index = 0;
        while (index < source.length()) {
            int start = source.indexOf(VARIABLE_START, index);
            if (start == -1)
                break;
            int end = closingBrace(source, start);
            if (end == -1) {
                // not a variable: leave the ${ where it is and keep looking at what follows it
                sb.append(source, index, start + VARIABLE_START.length());
                index = start + VARIABLE_START.length();
                continue;
            }
            sb.append(source, index, start);
            String expression = replace(source.substring(start + VARIABLE_START.length(), end), resolving);
            sb.append(resolve(expression, resolving));
            index = end + 1;
        }
        sb.append(source, index, source.length());
        return sb.toString();
    }

    /**
     * Returns the index of the <code>}</code> closing the <code>${</code> that starts at the given index,
     * skipping over the nested <code>${...}</code> expressions met along the way.
     *
     * @return the index of the closing brace; <code>-1</code> when there is no brace matching it, and also when
     *         the expression is empty, since <code>${}</code> has never been a variable.
     */
    private static int closingBrace(String source, int start) {
        int expression = start + VARIABLE_START.length();
        int depth = 1;
        for (int i = expression; i < source.length(); i++) {
            if (source.startsWith(VARIABLE_START, i)) {
                depth++;
                i++;
            } else if (source.charAt(i) == '}' && --depth == 0) {
                return (i == expression) ? -1 : i;
            }
        }
        return -1;
    }

    /**
     * Resolves the content of a single <code>${...}</code> expression.
     * <p>
     * The expression is looked up as a property key first, in its entirety: a key that happens to contain a
     * <code>:</code> therefore keeps resolving as it always did. Only when that lookup fails is the first
     * <code>:</code> read as the separator introducing a default value, so that <code>${key:default}</code> falls
     * back to <code>default</code> instead of to the empty string. Everything after that first colon is the
     * default, colons included, which keeps URLs, Windows paths and <code>host:port</code> pairs intact.</p>
     * <p>
     * A property whose value leads back to the property itself cannot be resolved, and is reported as the
     * configuration error it is rather than being followed until the stack runs out. A default value does not
     * make the reference resolvable, so it does not rescue it either: <code>db.host=${db.host:localhost}</code>
     * is circular, and writing <code>db.host=localhost</code> is what was meant.</p>
     *
     * <p>
     * An expression that begins with <code>$</code> and contains <code>::</code> is not a key at all: it
     * names a {@link org.aeonbits.owner.handlers.ValueHandler} and is answered by it. That is decided
     * before the key lookup, and what the handler answers is returned <b>as it is</b> - see
     * {@link #resolveMarker}.</p>
     *
     * @param expression the text between <code>${</code> and <code>}</code>.
     * @param resolving  the expressions being resolved further up the recursion, in order.
     * @return the replacement text; the empty string when nothing can be resolved and no default is given.
     * @throws IllegalArgumentException if the expression is already being resolved further up the recursion.
     */
    private String resolve(String expression, Set<String> resolving) {
        if (HandlersManager.isMarker(expression))
            return resolveMarker(expression);

        if (!resolving.add(expression))
            throw circularReference(expression, resolving);
        try {
            String value = values.getProperty(expression);
            if (value != null)
                return replace(value, resolving);

            int separator = expression.indexOf(DEFAULT_VALUE_SEPARATOR);
            if (separator == -1) {
                // nothing to resolve it to and no default offered: the empty string is what this library
                // has always answered, and it is also what a misspelt variable answers. Only a
                // configuration that asked to be strict is told the two apart - see owner.strict
                if (strict)
                    throw unsupported("The variable ${%s} resolves to nothing: no property, no system "
                                    + "property and no environment variable goes by that name, and no "
                                    + "default value was written for it. Without %s this is the empty "
                                    + "string, which is also what a misspelt name gives. Write ${%s:} if "
                                    + "the empty string is what is meant.",
                            expression, PropertiesManager.STRICT, expression);
                return "";
            }

            value = values.getProperty(expression.substring(0, separator));
            return (value != null) ? replace(value, resolving) : expression.substring(separator + 1);
        } finally {
            resolving.remove(expression);
        }
    }

    /**
     * Answers a marker through the handler it names.
     * <p>
     * Two things this deliberately does not do. It does not put the expression in the set of what is being
     * resolved, because a payload is not a reference: nothing it contains is looked up here, so there is no
     * cycle to detect - and a second value legitimately encrypted to the same cipher text would otherwise
     * be reported as one.</p>
     * <p>
     * And it does <b>not</b> expand what comes back. A value read from a property is expanded again,
     * because that is how <code>a=${b}</code> works; what a handler answers is a secret, a token or a file,
     * and text arriving from outside the configuration is exactly the text that must not be read as a
     * template. A password that happens to contain <code>${</code> is a password.</p>
     */
    private String resolveMarker(String expression) {
        return handlers.resolve(expression);
    }

    private static IllegalArgumentException circularReference(String expression, Set<String> resolving) {
        StringBuilder chain = new StringBuilder();
        boolean started = false;
        for (String step : resolving) {
            started |= step.equals(expression);
            if (started)
                chain.append("${").append(step).append("} -> ");
        }
        chain.append("${").append(expression).append('}');
        return new IllegalArgumentException("Circular variable reference: " + chain);
    }

    /**
     * Returns a string modified according to the supplied source and arguments.<br/>
     * If the source string has pattern-replacement content like {@code "a.${var}.b"},
     * the pattern is replaced with the property value of "var".<br/>
     * Otherwise the return string is formatted by source and arguments as with {@link String#format(String, Object...)}
     *
     * @param source A source format string. {@code null} returns {@code null}
     * @param args Arguments referenced by the format specifiers in the source string.
     * @return formatted string
     */
    String replace(String source, Object... args) {
        if (source == null)
            return null;
        Matcher m = PATTERN.matcher(source);
        return m.find() ? replace(source) : String.format(source, args);
    }
}
