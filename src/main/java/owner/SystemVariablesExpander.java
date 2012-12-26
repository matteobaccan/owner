/*
 * Copyright (c) 2012, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package owner;


import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

/**
 * This class is used to expand variables in the format <tt>${variable}</tt>$, using values from {@link System#getenv()}
 * and {@link System#getProperties()} (in this order; first match is accepted).
 *
 * @author Luigi R. Viggiano
 */
class SystemVariablesExpander {

    private final StrSubstitutor substitutor;

    SystemVariablesExpander() {
        Map<String, String> variables = new LinkedHashMap<String, String>(System.getenv());
        addAll(variables, System.getProperties());
        substitutor = new StrSubstitutor(variables);
    }

    private void addAll(Map<String, String> variables, Properties properties) {
        Set<Entry<Object, Object>> entries = properties.entrySet();
        for (Entry<Object, Object> entry : entries)
            variables.put((String) entry.getKey(), (String) entry.getValue());
    }

    String expand(String path) {
        if (path.indexOf('~') != -1)
            path = path.replace("~", "${user.home}");
        return substitutor.replace(path);
    }
}
