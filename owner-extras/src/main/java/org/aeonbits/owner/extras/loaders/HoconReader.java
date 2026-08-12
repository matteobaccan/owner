/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.extras.loaders;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigResolveOptions;
import com.typesafe.config.ConfigSyntax;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;
import org.aeonbits.owner.loaders.PropertyKeys;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;

import static org.aeonbits.owner.util.Util.hideCredentials;
import static org.aeonbits.owner.util.Util.unsupported;

/**
 * Everything in the HOCON loader that touches
 * <a href="https://github.com/lightbend/config">Typesafe Config</a>, and the only class here that names it.
 *
 * <p>
 * <b>The separation is load-bearing and it is not a matter of taste.</b> {@link HoconLoader} is found by
 * {@link java.util.ServiceLoader}, which instantiates every loader it discovers before anybody asks for one,
 * and Typesafe Config is an optional dependency that most users of <code>owner-extras</code> will not have.
 * A class is loaded without resolving the classes its method bodies mention, so <code>HoconLoader</code>
 * loads and instantiates with it absent - and this class, which cannot, is not touched until a
 * <code>.conf</code> source is actually read.
 * </p>
 *
 * <p>
 * The line that matters is execution, not mention: a local left null changes nothing, while one class
 * literal in a static initialiser turns discovery into a <code>ServiceConfigurationError</code>, which is
 * not one configuration failing but all of them. <code>HoconLoaderIsolationTest</code> refuses <em>any</em>
 * mention in <code>HoconLoader</code>, deliberately stricter than the JVM, and
 * <code>HoconDiscoveryWithoutTypesafeTest</code> catches the real failure. The suite runs with the
 * dependency present, so without those two nothing here notices either problem.
 * </p>
 *
 * @author Matteo Baccan
 * @since 2.0.0
 */
final class HoconReader {

    private HoconReader() {
    }

    /**
     * Reads the document at the given URI into the given properties, flattened to the convention every
     * loader in this library uses.
     *
     * @param result the properties to fill.
     * @param uri    the <code>.conf</code> source.
     * @throws IOException if the source cannot be read.
     */
    static void read(Properties result, URI uri) throws IOException {
        try (InputStream input = uri.toURL().openStream();
             Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            Config parsed = ConfigFactory.parseReader(reader, ConfigParseOptions.defaults()
                    .setSyntax(ConfigSyntax.CONF)
                    .setOriginDescription(hideCredentials(uri)));
            flatten("", resolve(parsed).root(), result);
        } catch (ConfigException badDocument) {
            // unchecked on purpose: LoadType.FIRST and MERGE both catch IOException and move on, which is
            // right for a source that is not there and would turn a broken document into a configuration
            // full of defaults with nothing said
            throw unsupported(badDocument, "%s could not be read as HOCON: %s",
                    hideCredentials(uri), badDocument.getMessage());
        }
    }

    /**
     * Resolves the substitutions - <code>${foo}</code> and <code>${?foo}</code> - which is the step that
     * makes this HOCON rather than JSON with comments.
     *
     * <p>
     * <b>Two passes, and the order is the whole of it.</b> The first resolves within the document and is
     * allowed to leave substitutions standing; the second looks the remainder up in the system properties
     * and then the environment, which is where an <code>application.conf</code> written for Typesafe Config
     * expects to find them. Doing it in one pass with {@code resolveWith} alone does not work, and the
     * failure is worth recording: a self-referential substitution - <code>path = ${path}":/usr/bin"</code>,
     * an everyday line in these files - is refused outright, because the document is then not resolved
     * against itself. The document has to see itself first.
     * </p>
     *
     * <p>
     * <b>Only the lookup falls back.</b> {@code resolveWith} resolves against another config without taking
     * any of its content, so not one system property or environment variable becomes a property of the
     * configuration. What OWNER reads is what the document says, and no more - and the sources it merges
     * remain its own business, <code>system:properties</code> and <code>system:env</code> included.
     * </p>
     *
     * <p>
     * A required substitution that resolves to nothing is an error, as it is in HOCON, and arrives as a
     * refusal naming the path. An optional one, <code>${?foo}</code>, leaves its key out - which is the
     * idiom these files are full of, and it agrees with what a missing key does everywhere here.
     * </p>
     */
    private static Config resolve(Config parsed) {
        Config withinTheDocument = parsed.resolve(ConfigResolveOptions.defaults().setAllowUnresolved(true));
        return withinTheDocument.resolveWith(
                ConfigFactory.systemProperties().withFallback(ConfigFactory.systemEnvironment()),
                ConfigResolveOptions.defaults());
    }

    /**
     * Walks the document, naming each leaf with {@link PropertyKeys} - the same convention every loader
     * here flattens with, so nested interfaces, indexed lists and grouped maps read a HOCON document the
     * way they read any other.
     */
    private static void flatten(String prefix, ConfigValue value, Properties result) {
        if (value.valueType() == ConfigValueType.OBJECT) {
            for (Map.Entry<String, ConfigValue> entry : ((ConfigObject) value).entrySet())
                flatten(PropertyKeys.child(prefix, entry.getKey()), entry.getValue(), result);
            return;
        }
        if (value.valueType() == ConfigValueType.LIST) {
            ConfigList list = (ConfigList) value;
            if (list.isEmpty()) {
                // as for JSON: an empty list is an empty value, which the library already reads as an empty
                // collection, and which overrides a default as the document says
                result.put(prefix, "");
                return;
            }
            for (int index = 0; index < list.size(); index++)
                flatten(PropertyKeys.element(prefix, index), list.get(index), result);
            return;
        }
        if (value.valueType() == ConfigValueType.NULL)
            return;   // a Properties cannot hold a null, so the key is simply not there - see HoconLoader

        result.put(prefix, String.valueOf(value.unwrapped()));
    }
}
