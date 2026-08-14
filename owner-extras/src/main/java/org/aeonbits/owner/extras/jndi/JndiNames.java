/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.extras.jndi;

import static org.aeonbits.owner.util.Util.unsupported;

/**
 * Turns what somebody wrote into the JNDI name to look up, and <b>refuses a name that would leave this
 * machine</b>.
 * <p>
 * The second half is the reason this class exists rather than being three lines inside the loader. A JNDI
 * name can carry its own scheme, and <code>InitialContext</code> honours it: <code>ldap://…</code>,
 * <code>rmi://…</code> and their relatives are all names it will happily resolve over the network. That is
 * the shape of Log4Shell, and it is reachable from a configuration file, because a
 * <code>@Sources</code> spec is expanded before it is read and therefore need not be a constant.
 * </p>
 * <p>
 * A remote lookup is worth refusing even on a JDK that has closed the worst of it. Since 8u191 a remote
 * <i>codebase</i> is not trusted by default, but the deserialization path through a gadget already on the
 * class path is not covered by that, and neither is what a lookup does before any of it: it opens a
 * connection to a host somebody else chose and hands over whatever credentials the environment holds.
 * </p>
 * <p>
 * <b>There is deliberately no option to turn this off.</b> Where the provider lives is a decision that
 * belongs in Java, next to the credentials it needs - {@link JndiLoader#JndiLoader(java.util.Hashtable)}
 * takes an environment for exactly that - and not in the file whose contents are the thing being
 * protected. It is the same rule this library applies to a passphrase.
 * </p>
 *
 * @author Matteo Baccan
 */
final class JndiNames {

    /**
     * What a relative name is resolved against, which is where a Java EE container binds an
     * <code>env-entry</code> and what Spring's <code>JndiPropertySource</code> also prefixes by default.
     */
    private static final String CONTAINER = "java:comp/env/";

    /** The only scheme a name written in a source may carry. */
    private static final String LOCAL_SCHEME = "java";

    /**
     * What a name already qualified with the component namespace begins with.
     * <p>
     * The test is <code>comp/</code> and not <code>comp/env/</code>, and the difference is the case that
     * matters most: <code>&lt;env-entry&gt;</code> elements are bound <b>directly under</b>
     * <code>java:comp/env</code>, so reading the whole of it - <code>jndi:comp/env</code> - is the
     * commonest thing anybody will write, and the narrower test sent it to
     * <code>java:comp/env/comp/env</code>.
     * </p>
     */
    private static final String COMPONENT = "comp/";

    private JndiNames() {
    }

    /**
     * The name to look up.
     * <p>
     * Three shapes are accepted, and the first two are the same name written two ways:
     * </p>
     * <ul>
     *   <li><code>jndi:myconfig</code> and <code>jndi:comp/env/myconfig</code> -
     *   {@value #CONTAINER} is supplied, so both mean <code>java:comp/env/myconfig</code>;</li>
     *   <li><code>jndi:comp/env</code> - the whole component context, which is where
     *   <code>&lt;env-entry&gt;</code> elements are bound and therefore the commonest source of all;</li>
     *   <li><code>java:comp/env/myconfig</code>, or any other <code>java:</code> name such as
     *   <code>java:global/…</code>, which is used as written.</li>
     * </ul>
     *
     * @param schemeSpecificPart what followed <code>jndi:</code> or <code>java:</code> in the source.
     * @param spec               the whole source, for the message when it is refused.
     * @return the JNDI name, always beginning with <code>java:</code>.
     * @throws UnsupportedOperationException if the name carries a scheme other than <code>java</code>.
     */
    static String resolve(String schemeSpecificPart, String spec) {
        String written = schemeSpecificPart == null ? "" : schemeSpecificPart.trim();
        if (written.isEmpty())
            throw unsupported("%s names nothing to look up.", spec);

        refuseANameThatLeavesThisMachine(written, spec);

        if (written.startsWith(LOCAL_SCHEME + ':'))
            return written;
        if (written.startsWith(COMPONENT) || written.equals("comp"))
            return LOCAL_SCHEME + ':' + written;
        return CONTAINER + written;
    }

    /**
     * Refuses a name whose scheme is not <code>java</code>.
     * <p>
     * The test is a colon before the first slash, which is what tells <code>ldap://host/dc=x</code> from
     * <code>comp/env/some:key</code> - a colon is legal inside a name and only leading one means a scheme.
     * </p>
     */
    private static void refuseANameThatLeavesThisMachine(String written, String spec) {
        int colon = written.indexOf(':');
        if (colon < 0)
            return;
        int slash = written.indexOf('/');
        if (slash >= 0 && slash < colon)
            return;

        String scheme = written.substring(0, colon);
        if (LOCAL_SCHEME.equals(scheme))
            return;

        throw unsupported("%s names a '%s:' context, and this loader resolves local names only. A JNDI "
                        + "name can carry its own scheme and InitialContext will follow it over the "
                        + "network, which is how a configuration file turns into a request to somebody "
                        + "else's server - and a source spec is expanded before it is read, so it is not "
                        + "even required to be a constant. Where the provider lives belongs in Java, next "
                        + "to the credentials it needs: construct the loader with a JNDI environment and "
                        + "register it. Names accepted here are 'java:...' and anything relative, which is "
                        + "resolved against %s.",
                spec, scheme, CONTAINER);
    }
}
