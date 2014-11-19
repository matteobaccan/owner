/*
 * Copyright (c) 2012-2014, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import static java.util.regex.Pattern.compile;
import static org.aeonbits.owner.Util.fixBackslashForRegex;
import static org.aeonbits.owner.Util.unsupported;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.aeonbits.owner.Config.SubstitutorClasses;

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

    private static final Pattern PATTERN = compile("\\$\\{(.+?)\\}");
    private static final String DEFAULT_SUBSTITUTOR_NAME = "";
    private static final int TO_SIZE_2 = 2;
    private final Map<String, Substitutor> substitutors = new HashMap<String, Substitutor>();

    /**
     * Creates a new instance and initializes it. Uses defaults for variable prefix and suffix and the escaping
     * character.
     *
     * @param values the variables' values, may be null
     */
    StrSubstitutor(Properties values) {
        substitutors.put(DEFAULT_SUBSTITUTOR_NAME, new DefaultSubstitutor(values));
    }

    StrSubstitutor(Properties values, Class<? extends Config> clazz) {
        substitutors.put(DEFAULT_SUBSTITUTOR_NAME, new DefaultSubstitutor(values));
        addCustomSubstitutors(clazz);
    }

    private void addCustomSubstitutors(Class<? extends Config> configClazz) {
        SubstitutorClasses annotation = configClazz.getAnnotation(SubstitutorClasses.class);
        if (annotation != null) {
            checkSameSize(annotation);
            for (int i = 0; i < annotation.names().length; i++) {
                String name = annotation.names()[i];
                Class<? extends Substitutor> clazz = annotation.classes()[i];
                try {
                    substitutors.put(name, clazz.newInstance());
                } catch (Exception e) {
                    throw unsupported(e,
                            "Substitutor class '%s' cannot be instantiated; see the cause below in the stack trace",
                            clazz.getCanonicalName());
                }
            }
        }

    }

    private void checkSameSize(SubstitutorClasses annotation) {
        int numOfNames = annotation.names().length;
        int numOfClasses = annotation.classes().length;
        if (numOfNames != numOfClasses) {
            throw unsupported(
                    "Mismatch in number of names (%s) and classes (%s) in annotation %s",
                    numOfNames, numOfClasses, annotation);
        }
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
            String value = replaceValueUsingSubstitutors(m.group(1));
            String replacement = (value != null) ? replace(value) : "";
            m.appendReplacement(sb, fixBackslashForRegex(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String replaceValueUsingSubstitutors(final String strToReplace) {
        String name = DEFAULT_SUBSTITUTOR_NAME;
        String value = strToReplace;
        if (strToReplace.contains(":")) {
            String[] pair = strToReplace.split(":", TO_SIZE_2);
            name = pair[0];
            value = pair[1];
        }
        if (name.equals(DEFAULT_SUBSTITUTOR_NAME)) {
            return substitutors.get(DEFAULT_SUBSTITUTOR_NAME).replace(strToReplace);
        }
        if (substitutors.containsKey(name)) {
            return substitutors.get(name).replace(value);
        }
        return substitutors.get(DEFAULT_SUBSTITUTOR_NAME).replace(strToReplace);
    }

}
