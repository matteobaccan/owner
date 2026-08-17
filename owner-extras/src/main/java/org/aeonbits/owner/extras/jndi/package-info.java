/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
/**
 * Reading a configuration out of JNDI, which is where an application server keeps what it was told about
 * the application: as a whole context through
 * {@link org.aeonbits.owner.extras.jndi.JndiLoader}, or one entry at a time through
 * {@link org.aeonbits.owner.extras.jndi.JndiHandler}.
 * <p>
 * The two answer different questions. A source - <code>&#64;Sources("jndi:comp/env/myconfig")</code> -
 * binds a whole subtree as properties, and merges with the files below it like any other source. A
 * marker in a value - <code>${$jndi::comp/env/db/password}</code> - takes one entry and leaves the rest
 * of the file alone, which is what a single credential wants.
 * </p>
 * <p>
 * <b>{@link org.aeonbits.owner.extras.jndi.JndiNames} refuses a name that would leave this machine</b>,
 * and it is the reason this package is three classes rather than two. A JNDI name can carry its own
 * scheme and <code>InitialContext</code> honours it, so <code>ldap://</code> and its relatives are names
 * it will resolve over the network - the shape of Log4Shell, reachable from a configuration file, since
 * a source spec is expanded before it is read and need not be a constant.
 * </p>
 */
package org.aeonbits.owner.extras.jndi;
