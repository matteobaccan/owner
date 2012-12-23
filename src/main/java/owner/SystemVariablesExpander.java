/*
 * Copyright (c) 2012, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package owner;


import org.apache.commons.lang.text.StrSubstitutor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This class is used to expand variables in the format <tt>${variable}</tt>$, using values from {@link System#getenv()}
 * and {@link System#getProperties()} (in this order; first match is accepted).
 *
 * @author Luigi R. Viggiano
 */
class SystemVariablesExpander {

    private final StrSubstitutor substitutor;

    SystemVariablesExpander() {
        Map<Object, Object> properties = new LinkedHashMap<Object, Object>(System.getenv());
        properties.putAll(System.getProperties());
        substitutor = new StrSubstitutor(properties);
    }

    String expand(String path) {
        if (path.indexOf('~') != -1)
            path = path.replace("~", "${user.home}");
        return substitutor.replace(path);
    }
}
