/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.extras.jndi;

import org.aeonbits.owner.handlers.ValueHandler;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.Hashtable;
import java.util.Map;

/**
 * Reads a <b>single</b> JNDI entry into a value:
 *
 * <pre>
 *     db.password = ${$jndi::comp/env/db/password}
 *     jdbc.url    = jdbc:h2:mem:test?password=${db.password}
 * </pre>
 *
 * <pre>
 *     ConfigFactory.registerValueHandler(new JndiHandler());
 * </pre>
 *
 * <p>
 * The counterpart of {@link JndiLoader}, and the division between them is the one that makes each of them
 * simple: <b>the loader reads a context and the handler reads an entry.</b> A source is a set of keys, so
 * a name bound to one value has no place in <code>@Sources</code> - there would be no key for it - and a
 * context has no place in a value, for the mirror reason.
 * </p>
 * <p>
 * The names accepted are the loader's, and so is the refusal: a name carrying a scheme other than
 * <code>java</code> is refused rather than resolved over the network. See {@link JndiNames}, and note
 * that a marker sits in a <i>value</i>, which is a place even easier to write into than a source spec.
 * </p>
 * <p>
 * What is bound must be a scalar - a <code>String</code>, a <code>Number</code>, a <code>Boolean</code> or
 * a <code>Character</code>, the types an <code>env-entry</code> declares. A <code>DataSource</code> is not
 * a value, and neither is a context.
 * </p>
 *
 * @author Matteo Baccan
 * @see JndiLoader
 */
public class JndiHandler implements ValueHandler {

    private static final long serialVersionUID = 3061954744125712148L;

    /** The name values refer to this handler by. */
    public static final String DEFAULT_NAME = "jndi";

    /** The name values refer to this handler by. */
    private final String name;

    /** See {@link JndiLoader#JndiLoader(Hashtable)}: transient, because an environment carries credentials. */
    private final transient Hashtable<?, ?> environment;

    /** A handler under the name <code>jndi</code>, against the ambient JNDI environment. */
    public JndiHandler() {
        this(DEFAULT_NAME, null);
    }

    /**
     * A handler against a JNDI environment of your own.
     *
     * @param environment the environment handed to {@link InitialContext}, or <code>null</code> for the
     *                    ambient one. Copied, so the caller may keep using its own.
     */
    public JndiHandler(Map<?, ?> environment) {
        this(DEFAULT_NAME, environment);
    }

    /**
     * A handler under a name of your own, which is how two providers are readable at once.
     *
     * @param name        the name values refer to this handler by.
     * @param environment the environment handed to {@link InitialContext}, or <code>null</code> for the
     *                    ambient one.
     */
    public JndiHandler(String name, Map<?, ?> environment) {
        this.name = name;
        this.environment = environment == null ? null : new Hashtable<Object, Object>(environment);
    }

    @Override
    public String name() {
        return name;
    }

    /**
     * Looks the name up and answers with the value bound to it.
     *
     * @throws IllegalArgumentException if the name carries a scheme other than <code>java</code>, if
     *                                  nothing is bound to it, or if what is bound is not a scalar.
     */
    @Override
    public String resolve(String payload) {
        String jndiName;
        try {
            jndiName = JndiNames.resolve(payload, "${$" + name + "::...}");
        } catch (UnsupportedOperationException refused) {
            // the SPI's contract is IllegalArgumentException; the message is the part worth keeping
            throw new IllegalArgumentException(refused.getMessage(), refused);
        }

        Context root = null;
        try {
            root = environment == null ? new InitialContext() : new InitialContext(environment);
            Object bound = root.lookup(jndiName);
            if (bound instanceof String || bound instanceof Number
                    || bound instanceof Boolean || bound instanceof Character)
                return String.valueOf(bound);

            if (bound instanceof Context)
                throw new IllegalArgumentException(jndiName + " is a context and not a value. A whole "
                        + "context is a source: name it in @Sources as jndi:" + payload + " and let the "
                        + "loader read it.");

            throw new IllegalArgumentException(jndiName + " is bound to a "
                    + (bound == null ? "null" : bound.getClass().getName())
                    + ", which is not a value a property can hold.");
        } catch (NamingException e) {
            // throwing is the contract: answering with the empty string would report a failure as a value
            throw new IllegalArgumentException("could not read " + jndiName + ": " + e.getMessage(), e);
        } finally {
            closeQuietly(root);
        }
    }

    private static void closeQuietly(Context context) {
        if (context == null)
            return;
        try {
            context.close();
        } catch (NamingException closingIsNotTheFailureWorthReporting) {
            // the lookup either answered or threw; how the context closed changes neither
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[name=" + name + "]";
    }
}
