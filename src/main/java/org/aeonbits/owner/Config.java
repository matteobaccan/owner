/*
 * Copyright (c) 2012, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.aeonbits.owner.Config.LoadType.FIRST;
import static org.aeonbits.owner.PropertiesLoader.getInputStream;
import static org.aeonbits.owner.PropertiesLoader.properties;
import static org.aeonbits.owner.Util.reverse;

/**
 * Marker interface that must be implemented by all Config sub-interfaces.
 * <p/>
 * Sub-interfaces may also implement list() methods as following to aid debugging:
 * <p/>
 * <pre>
 *     void list(PrintStream out);
 *     void list(PrintWriter out);
 * </pre>
 * <p/>
 * These methods will print the list of properties, see {@link java.util.Properties#list(java.io.PrintStream)} and
 * {@link java.util.Properties#list(java.io.PrintWriter)}.
 *
 * @author Luigi R. Viggiano
 * @see java.util.Properties
 */
public interface Config {

    /**
     * Specifies the policy for loading the properties files. By default the first available properties file specified
     * by {@link Sources} will be loaded, see {@link LoadType#FIRST}. User can also specify that the load policy is
     * {@link LoadType#MERGE} to have the properties files merged: properties are loaded in order from the first file to
     * the last, if there are conflicts in properties names the earlier files loaded prevail.
     * @since 1.0.2
     */
    @Retention(RUNTIME)
    @Target(TYPE)
    @Documented
    @interface LoadPolicy {
        LoadType value() default FIRST;
    }

    /**
     * Specifies the source from which to load the properties file. It has to be specified in a URL string format.
     * Allowed protocols are the ones allowed by {@link java.net.URL} plus <tt>classpath:path/to/resource
     * .properties</tt>
     */
    @Retention(RUNTIME)
    @Target(TYPE)
    @Documented
    @interface Sources {
        String[] value();
    }

    /**
     * Default value to be used if no property is found. No quoting (other than normal Java string quoting) is done.
     */
    @Retention(RUNTIME)
    @Target(METHOD)
    @Documented
    @interface DefaultValue {
        String value();
    }

    /**
     * The key used for lookup for the property.  If not present, the key will be generated based on the unqualified
     * method name.
     */
    @Retention(RUNTIME)
    @Target(METHOD)
    @Documented
    @interface Key {
        String value();
    }

    /**
     * Specifies the policy type to use to load the {@link org.aeonbits.owner.Config.Sources} files for properties.
     * @since 1.0.2
     */
    enum LoadType {

        /**
         * The first available {@link org.aeonbits.owner.Config.Sources} will be loaded.
         */
        FIRST {
            @Override
            Properties load(Sources sources, ConfigURLStreamHandler handler) throws MalformedURLException {
                String[] values = sources.value();
                for (String source : values) {
                    URL url = new URL(null, source, handler);
                    try {
                        InputStream stream = getInputStream(url);
                        if (stream != null)
                            return properties(stream);
                    } catch (IOException ex) {
                        // ignore: happens when a file specified in the sources is not found or cannot be read.
                    }
                }
                return new Properties();
            }
        },

        /**
         * All the {@link org.aeonbits.owner.Config.Sources} will be loaded and merged. If the same property key is
         * specified from more than one source, the one specified first will prevail.
         */
        MERGE {
            @Override
            Properties load(Sources sources, ConfigURLStreamHandler handler) throws MalformedURLException {
                String[] values = reverse(sources.value());
                Properties result = new Properties();
                for (String source : values) {
                    URL url = new URL(null, source, handler);
                    try {
                        InputStream stream = getInputStream(url);
                        if (stream != null)
                            result.load(stream);
                    } catch (IOException ex) {
                        // ignore: happens when a file specified in the sources is not found or cannot be read.
                    }
                }
                return result;
            }
        };

        abstract Properties load(Sources sources, ConfigURLStreamHandler handler) throws MalformedURLException;
    }
}
