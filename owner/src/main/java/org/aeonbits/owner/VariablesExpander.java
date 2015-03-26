/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;


import java.io.Serializable;
import java.util.Properties;

import static org.aeonbits.owner.Util.expandUserHome;

/**
 * This class is used to expand variables in the format <tt>${variable}</tt>$, using values from
 * {@link System#getenv()}, {@link System#getProperties()} and the <tt>Properties</tt> object specified in the
 * constructor (in inverse order; first match is accepted).
 *
 * @author Luigi R. Viggiano
 */
class VariablesExpander implements Serializable {

    private final StrSubstitutor substitutor;

    VariablesExpander(Properties props) {
        Properties variables = new Properties();
        variables.putAll(Util.system().getenv());
        variables.putAll(Util.system().getProperties());
        variables.putAll(props);
        substitutor = new StrSubstitutor(variables);
    }

    String expand(String path) {
        String expanded = expandUserHome(path);
        return substitutor.replace(expanded);
    }

}
