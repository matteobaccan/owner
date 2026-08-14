/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;


import org.aeonbits.owner.util.Util;

import java.io.Serializable;
import java.util.Properties;

import static org.aeonbits.owner.util.Util.expandUserHome;

/**
 * This class is used to expand variables in the format <code>${variable}</code>$, using values from
 * {@link System#getenv()}, {@link System#getProperties()} and the <code>Properties</code> object specified in the
 * constructor (in inverse order; first match is accepted).
 *
 * @author Luigi R. Viggiano
 */
class VariablesExpander implements Serializable {

    private final StrSubstitutor substitutor;

    /**
     * The strictness comes from the same properties the variables do, since a
     * <code>@Sources</code> spec is expanded before there is a Config object to ask. Under
     * <code>owner.strict</code> a spec naming a variable nobody set is refused, which is the same case as a
     * path spelt wrong and the one it is hardest to see: <code>file:${app.home}/app.properties</code> with
     * nothing setting <code>app.home</code> quietly becomes <code>file:/app.properties</code>.
     */
    VariablesExpander(Properties props) {
        this(props, new HandlersManager());
    }

    /**
     * A <code>@Sources</code> spec sees the handlers as a value does, which is not a convenience but the
     * absence of an exception: the marker is expansion, and this is the expansion that runs on a spec. It
     * is also the one place a handler earns its keep twice over — <code>file:${$vault::…}</code> keeps a
     * path out of the file, and a spec naming a handler nobody registered is refused here rather than
     * quietly becoming <code>file:</code>.
     *
     * @param handlers the handlers registered on the factory creating the configuration.
     */
    VariablesExpander(Properties props, HandlersManager handlers) {
        Properties variables = new Properties();
        variables.putAll(Util.system().getenv());
        variables.putAll(Util.system().getProperties());
        variables.putAll(props);
        substitutor = new StrSubstitutor(variables,
                Boolean.parseBoolean(props.getProperty(PropertiesManager.STRICT)), handlers);
    }

    String expand(String path) {
        String expanded = expandUserHome(path);
        return substitutor.replace(expanded);
    }

}
