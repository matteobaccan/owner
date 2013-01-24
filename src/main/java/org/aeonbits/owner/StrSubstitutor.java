/*
 * Copyright (c) 2012, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.compile;

/**
 * Substitutes variables within a string by values.
 * <p>
 * This class takes a piece of text and substitutes all the variables within it.
 * The definition of a variable is <code>${variableName}</code>.
 * <p>
 * Typical usage of this class follows the following pattern: First an instance is created
 * and initialized with the valueMap that contains the values for the available variables.
 * If a prefix and/or suffix for variables should be used other than the default ones,
 * the appropriate settings can be performed. After that the <code>replace()</code>
 * method can be called passing in the source text for interpolation. In the returned
 * text all variable references (as long as their values are known) will be resolved.
 * The following example demonstrates this:
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
class StrSubstitutor {
    private Map<String, String> valueMap;
    private static final Pattern PATTERN = compile("\\$\\{(.+?)\\}");

    /**
     * Creates a new instance and initializes it. Uses defaults for variable
     * prefix and suffix and the escaping character.
     *
     * @param valueMap  the valueMap with the variables' values, may be null
     */
    public StrSubstitutor(Map<String, String> valueMap) {
        this.valueMap = valueMap;
    }

    /**
     * Replaces all the occurrences of variables with their matching values
     * from the resolver using the given source string as a template.
     *
     * @param source  the string to replace in, null returns null
     * @return the result of the replace operation
     */
    public String replace(String source) {
        if (source == null) return null;
        Matcher m = PATTERN.matcher(source);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String var = m.group(1);
            String replacement = replace(valueMap.get(var));
            m.appendReplacement(sb, replacement);
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
