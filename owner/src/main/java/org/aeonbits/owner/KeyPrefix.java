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
 * <p>
 * A {@link NestedProperties nested} configuration object carries a third form, the <b>path</b> it hangs from,
 * which is not read from anywhere: it is the key its accessor resolved to. See {@link #nestedIn(String)}.
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
    static final KeyPrefix NONE = new KeyPrefix("", "", false);

    private final String path;
    private final String literalPrefix;
    private final boolean fromPackage;

    private KeyPrefix(String path, String literalPrefix, boolean fromPackage) {
        this.path = path;
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

        return new KeyPrefix("", literalPrefix, fromPackage);
    }

    /**
     * The prefix of a configuration object nested at the given path.
     * <p>
     * Neither of the two factory forms is carried over, and that is not an omission: whatever they had to say
     * was already said when the key of the accessor was resolved, and the path <b>is</b> that key. Applying
     * them again would prepend the literal a second time, or file a nested interface under the package it
     * happens to be declared in rather than under the object that holds it.
     * </p>
     *
     * @param path the key the accessor resolved to, separator included.
     * @return the prefix the nested object resolves its own keys with.
     */
    static KeyPrefix nestedIn(String path) {
        return new KeyPrefix(path, "", false);
    }

    /** Tells whether this prefix is the one of a nested configuration object rather than of a root one. */
    boolean isNested() {
        return !path.isEmpty();
    }

    /** The path a nested object hangs from, empty for a root one. */
    String path() {
        return path;
    }

    /**
     * Returns the prefix to prepend to the keys of the given interface.
     * <p>
     * On a root object a {@link Config.Prefix} declared by the interface <b>wins</b> over the factory: it is
     * the explicit statement of the two. On a nested one the two <b>compose</b> instead, path first, because
     * neither of them is a default the other overrides — the path says where the object was hung and the
     * annotation says how that object names its own keys. It also means an interface that already carries a
     * prefix cannot be nested without carrying it along, which is visible in the keys the first time it is
     * tried.
     * </p>
     *
     * @param declaringClass the interface declaring the method whose key is being resolved.
     * @param declared       the value of the {@link Config.Prefix} on that interface, <code>null</code> when
     *                       it declares none.
     * @return the prefix, possibly empty; never <code>null</code>.
     */
    String of(Class<?> declaringClass, String declared) {
        if (isNested())
            return declared == null ? path : path + declared;
        if (declared != null)
            return declared;
        if (!fromPackage)
            return literalPrefix;
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
