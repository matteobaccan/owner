/*
 * Copyright (c) 2012, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;


import java.util.Properties;

/**
 * This class is used to expand variables in the format <tt>${variable}</tt>$, using values from {@link System#getenv()}
 * and {@link System#getProperties()} (in this order; first match is accepted).
 *
 * @author Luigi R. Viggiano
 */
class SystemVariablesExpander {

    private final StrSubstitutor substitutor;

    SystemVariablesExpander() {
        Properties variables = new Properties();
        variables.putAll(System.getenv());
        variables.putAll(System.getProperties());
        substitutor = new StrSubstitutor(variables);
    }


    String expand(String path) {
        if (path.indexOf('~') != -1)
            path = path.replace("~", "${user.home}");
        return substitutor.replace(path);
    }
}
