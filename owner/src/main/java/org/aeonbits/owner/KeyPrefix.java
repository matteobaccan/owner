/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import java.io.Serializable;
import java.util.Properties;

/**
 * The prefix that applies to the interfaces which do not declare a {@link Config.Prefix} of their own, configured
 * on the {@link Factory} rather than in the source code.
 * <p>
 * It is read from the factory properties, which {@link Factory#getProperties()} already describes as the place
 * where the factory is configured, so no new method appears on the interface:
 * </p>
 * <pre>
 *     ConfigFactory.setProperty(KeyPrefix.FROM_PACKAGE, "true");
 * </pre>
 * <p>
 * Two forms are available, and they compose - the literal comes first:
 * </p>
 * <ul>
 *     <li><code>owner.key.prefix</code>, a literal prepended to every key. It is the blunt form: it moves the
 *     keys of <b>every</b> interface created by the factory, so it belongs to an application, never to a
 *     library.</li>
 *     <li><code>owner.key.prefix.from.package</code>, which derives the prefix from the package of the interface
 *     that <b>declares</b> the method, followed by a dot. Being derived, it moves with the class instead of
 *     being left behind when the class is moved, and it namespaces each interface under its own package rather
 *     than dragging everything under a common one.</li>
 * </ul>
 * <p>
 * The instance is built when the Config object is created, and kept for its whole life: changing the factory
 * afterwards cannot rename the keys of an object that already exists, a reload resolves the same keys it
 * resolved the first time, and the mapping survives serialization along with the object.
 * </p>
 *
 * @author Matteo Baccan
 * @since 2.0.0
 */
final class KeyPrefix implements Serializable {

    private static final long serialVersionUID = 6544270213498635971L;

    /** Name of the factory property holding a literal prefix; see the class description. */
    static final String LITERAL = "owner.key.prefix";

    /** Name of the factory property asking for the prefix to be derived from the package; see the class description. */
    static final String FROM_PACKAGE = "owner.key.prefix.from.package";

    private static final String SEPARATOR = ".";

    /** No prefix at all, which is what every factory does unless it is told otherwise. */
    static final KeyPrefix NONE = new KeyPrefix("", false);

    private final String literalPrefix;
    private final boolean fromPackage;

    private KeyPrefix(String literalPrefix, boolean fromPackage) {
        this.literalPrefix = literalPrefix;
        this.fromPackage = fromPackage;
    }

    /**
     * Reads the prefix out of the properties of a {@link Factory}.
     *
     * @param props the factory properties, may be <code>null</code>.
     * @return the prefix, {@link #NONE} when neither property is set.
     */
    static KeyPrefix from(Properties props) {
        if (props == null) return NONE;

        String literalPrefix = props.getProperty(LITERAL, "");
        boolean fromPackage = Boolean.parseBoolean(props.getProperty(FROM_PACKAGE));
        if (literalPrefix.isEmpty() && !fromPackage) return NONE;

        return new KeyPrefix(literalPrefix, fromPackage);
    }

    /**
     * Returns the prefix to prepend to the keys of the given interface.
     *
     * @param declaringClass the interface declaring the method whose key is being resolved.
     * @return the prefix, possibly empty; never <code>null</code>.
     */
    String of(Class<?> declaringClass) {
        if (!fromPackage) return literalPrefix;
        return literalPrefix + packageOf(declaringClass);
    }

    /**
     * The package of the given class followed by a dot, or nothing at all for a class in the default package,
     * which has no name to build a prefix out of.
     */
    private static String packageOf(Class<?> declaringClass) {
        Package pkg = declaringClass.getPackage();
        if (pkg == null || pkg.getName().isEmpty()) return "";
        return pkg.getName() + SEPARATOR;
    }
}
