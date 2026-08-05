/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import java.io.Serializable;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.compile;

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
 *
 * @author Luigi R. Viggiano
 */
class StrSubstitutor implements Serializable {

    private final Properties values;
    private static final Pattern PATTERN = compile("\\$\\{(.+?)\\}");
    private static final char DEFAULT_VALUE_SEPARATOR = ':';

    /**
     * Creates a new instance and initializes it. Uses defaults for variable prefix and suffix and the escaping
     * character.
     *
     * @param values the variables' values, may be null
     */
    StrSubstitutor(Properties values) {
        this.values = values;
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
        Matcher m = PATTERN.matcher(source);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String replacement = resolve(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Resolves the content of a single <code>${...}</code> expression.
     * <p>
     * The expression is looked up as a property key first, in its entirety: a key that happens to contain a
     * <code>:</code> therefore keeps resolving as it always did. Only when that lookup fails is the first
     * <code>:</code> read as the separator introducing a default value, so that <code>${key:default}</code> falls
     * back to <code>default</code> instead of to the empty string. Everything after that first colon is the
     * default, colons included, which keeps URLs, Windows paths and <code>host:port</code> pairs intact.</p>
     *
     * @param expression the text between <code>${</code> and <code>}</code>.
     * @return the replacement text; the empty string when nothing can be resolved and no default is given.
     */
    private String resolve(String expression) {
        String value = values.getProperty(expression);
        if (value != null)
            return replace(value);

        int separator = expression.indexOf(DEFAULT_VALUE_SEPARATOR);
        if (separator == -1)
            return "";

        value = values.getProperty(expression.substring(0, separator));
        return (value != null) ? replace(value) : expression.substring(separator + 1);
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
