/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.extras.jndi;

import org.aeonbits.owner.loaders.Loader;

import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import java.io.IOException;
import java.net.URI;
import java.util.Hashtable;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.aeonbits.owner.util.Util.unsupported;

/**
 * A {@link Loader loader} that reads the properties bound under a JNDI context:
 *
 * <pre>
 *     &#64;LoadPolicy(LoadType.MERGE)
 *     &#64;Sources({
 *         "jndi:comp/env/myconfig",
 *         "file:~/myconfig.properties",
 *         "classpath:myconfig.properties" })
 *     public interface MyConfig extends Config { }
 * </pre>
 *
 * <p>
 * Which is the shape a container gives you: the deployment binds an <code>env-entry</code> or a Tomcat
 * <code>&lt;Environment&gt;</code>, and it overrides the file that ships with the application.
 * </p>
 *
 * <h2>What it accepts</h2>
 * <p>
 * <code>jndi:myconfig</code> and <code>jndi:comp/env/myconfig</code> both mean
 * <code>java:comp/env/myconfig</code> - a relative name is resolved against <code>java:comp/env/</code>,
 * which is where a container binds and what Spring's <code>JndiPropertySource</code> prefixes by default.
 * A <code>java:</code> name is used as written, so <code>java:global/…</code> works and can be put in
 * <code>@Sources</code> directly.
 * </p>
 * <p>
 * <b>A name carrying any other scheme is refused</b>, and that is the centre of this class rather than a
 * detail of it - see {@link JndiNames}.
 * </p>
 *
 * <h2>What it reads</h2>
 * <p>
 * The name must be bound to a <b>context</b>, and every binding under it becomes a property. Subcontexts
 * are read too, and their names joined with a dot, which is the same flattening every other format in this
 * library uses: <code>comp/env/myconfig/db/host</code> is read as <code>db.host</code>.
 * </p>
 * <p>
 * A name bound to a single value is <b>not</b> a source - there would be no key for it - and is refused
 * with a pointer to {@link JndiHandler}, which is the per-value form of the same thing.
 * </p>
 * <p>
 * <b>A binding that is not a scalar is skipped, not refused.</b> A real <code>java:comp/env</code> holds
 * a <code>DataSource</code>, a <code>Queue</code> or a <code>UserTransaction</code> next to the settings,
 * and a loader that refused the whole context over one of them would be unusable in the container it
 * exists for. What is taken is a <code>String</code>, a <code>Number</code>, a <code>Boolean</code> or a
 * <code>Character</code> - which is the list of types an <code>env-entry</code> can declare. What is
 * skipped is named at <code>CONFIG</code>, so that a property that quietly did not arrive can be found.
 * </p>
 *
 * <h2>What it needs</h2>
 * <p>
 * Nothing. JNDI is in the JDK, so this loader brings no dependency; it is in this artifact rather than in
 * the core because a JNDI lookup is a capability, and one every application should not carry whether it
 * asked for it or not.
 * </p>
 *
 * @author Matteo Baccan
 * @see JndiHandler
 */
public class JndiLoader implements Loader {

    private static final long serialVersionUID = 7069884213295856781L;

    private static final Logger LOGGER = Logger.getLogger(JndiLoader.class.getName());

    private static final String JNDI_SCHEME = "jndi";
    private static final String JAVA_SCHEME = "java";

    /**
     * The JNDI environment, or <code>null</code> for the ambient one.
     * <p>
     * <b>Transient</b>, and for the reason a passphrase is: an environment carries
     * {@link Context#SECURITY_CREDENTIALS}, and a configuration object is serializable.
     * </p>
     */
    private final transient Hashtable<?, ?> environment;

    /**
     * A loader is built with no arguments, both when it is registered by hand and when
     * {@link java.util.ServiceLoader} finds it on the class path. Declared rather than left implicit
     * so that the requirement is visible to whoever changes this class.
     */
    public JndiLoader() {
        this(null);
    }

    /**
     * A loader against a JNDI environment of your own, which is how a provider somewhere else is reached.
     * <p>
     * This is the deliberate way to do what a <code>ldap:</code> name in a source is refused for: the
     * decision, and the credentials it needs, are in Java next to each other, rather than in the file
     * whose contents are the thing being protected.
     * </p>
     *
     * @param environment the environment handed to {@link InitialContext}, or <code>null</code> for the
     *                    ambient one. Copied, so the caller may keep using its own.
     */
    public JndiLoader(Hashtable<?, ?> environment) {
        this.environment = environment == null ? null : new Hashtable<>(environment);
    }

    @Override
    public boolean accept(URI uri) {
        // the constant on the left: a URI need not have a scheme, and every loader is asked about
        // every source
        return JNDI_SCHEME.equals(uri.getScheme()) || JAVA_SCHEME.equals(uri.getScheme());
    }

    @Override
    public void load(Properties result, URI uri) throws IOException {
        String name = JndiNames.resolve(writtenNameIn(uri), uri.toString());
        Context root = null;
        try {
            root = environment == null ? new InitialContext() : new InitialContext(environment);
            Object bound = root.lookup(name);
            if (!(bound instanceof Context))
                throw unsupported("%s is bound to a %s and not to a context, so there is nothing here to "
                                + "read a set of properties from - a source is a set of keys, and this is "
                                + "one value with no key of its own. To read a single entry, use the "
                                + "value handler instead: ${$jndi::%s}.",
                        name, bound == null ? "null" : bound.getClass().getName(), name);
            read((Context) bound, "", result);
        } catch (NamingException e) {
            // an IOException is what the loading machinery understands: it is what makes a source that
            // cannot be read pass over quietly, or refuse under owner.strict, like any other source
            throw new IOException("could not read " + name + ": " + e.getMessage(), e);
        } finally {
            closeQuietly(root);
        }
    }

    /**
     * The name as somebody wrote it, with the scheme put back when it is part of the name.
     * <p>
     * <code>URI</code> hands over the scheme and the rest separately, so a <code>java:comp/env/x</code>
     * source arrives here as <code>comp/env/x</code> — which is indistinguishable from a relative name and
     * would be resolved against <code>java:comp/env/</code> a second time. For <code>jndi:</code> the
     * scheme is ours and is dropped; for <code>java:</code> it belongs to the name and is kept.
     * </p>
     */
    private static String writtenNameIn(URI uri) {
        String rest = uri.getSchemeSpecificPart();
        return JAVA_SCHEME.equals(uri.getScheme()) ? JAVA_SCHEME + ':' + rest : rest;
    }

    /** Reads a context into the properties, joining the names of the subcontexts with a dot. */
    private void read(Context context, String prefix, Properties result) throws NamingException {
        NamingEnumeration<Binding> bindings = context.listBindings("");
        try {
            while (bindings.hasMore()) {
                Binding binding = bindings.next();
                String key = prefix.isEmpty() ? binding.getName() : prefix + '.' + binding.getName();
                Object value = binding.getObject();

                if (value instanceof Context) {
                    read((Context) value, key, result);
                } else if (isScalar(value)) {
                    result.setProperty(key, String.valueOf(value));
                } else {
                    // said rather than refused: a DataSource beside the settings is normal, and a
                    // property that quietly did not arrive is the thing that needs finding
                    final String skipped = key;
                    final String type = value == null ? "null" : value.getClass().getName();
                    LOGGER.log(Level.CONFIG, () -> String.format(
                            "JNDI: '%s' is bound to a %s and is not a property, so it was skipped",
                            skipped, type));
                }
            }
        } finally {
            closeQuietly(bindings);
        }
    }

    /**
     * The types an <code>env-entry</code> can declare, which is what makes a binding a property rather
     * than a resource.
     */
    private static boolean isScalar(Object value) {
        return value instanceof String || value instanceof Number
                || value instanceof Boolean || value instanceof Character;
    }

    private static void closeQuietly(Context context) {
        if (context == null)
            return;
        try {
            context.close();
        } catch (NamingException closingIsNotTheFailureWorthReporting) {
            LOGGER.log(Level.FINE, "closing a JNDI context failed", closingIsNotTheFailureWorthReporting);
        }
    }

    private static void closeQuietly(NamingEnumeration<?> enumeration) {
        try {
            enumeration.close();
        } catch (NamingException closingIsNotTheFailureWorthReporting) {
            LOGGER.log(Level.FINE, "closing a JNDI enumeration failed", closingIsNotTheFailureWorthReporting);
        }
    }
}
