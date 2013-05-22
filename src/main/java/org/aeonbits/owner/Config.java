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
import static org.aeonbits.owner.PropertiesLoader.close;
import static org.aeonbits.owner.PropertiesLoader.getInputStream;
import static org.aeonbits.owner.PropertiesLoader.properties;
import static org.aeonbits.owner.Util.ignore;
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
     *
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
     * Allowed protocols are the ones allowed by {@link java.net.URL} plus
     * <tt>classpath:path/to/resource.properties</tt>
     *
     * @since 1.0.2
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
     *
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
                            try {
                                return properties(stream);
                            } finally {
                                close(stream);
                            }
                    } catch (IOException ex) {
                        ignore(); // happens when a file specified in the sources is not found or cannot be read.
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
                            try {
                                result.load(stream);
                            } finally {
                                close(stream);
                            }
                    } catch (IOException ex) {
                        ignore(); // happens when a file specified in the sources is not found or cannot be read.
                    }
                }
                return result;
            }
        };

        abstract Properties load(Sources sources, ConfigURLStreamHandler handler) throws MalformedURLException;
    }

    /**
     * Specifies to disable some of the features supported by the API.
     * This may be useful in case the user prefers to implement by his own, or just has troubles with something that
     * is unwanted.
     * Features that can be disabled are specified in the enum {@link DisableableFeature}.
     *
     * @since 1.0.4
     */
    @Retention(RUNTIME)
    @Target({METHOD, TYPE})
    @Documented
    @interface DisableFeature {
        DisableableFeature[] value();
    }

    /**
     * This enum contains the features that can be disabled using the annotation {@link DisableFeature}.
     *
     * @since 1.0.4
     */
    enum DisableableFeature {
        VARIABLE_EXPANSION,
        PARAMETER_FORMATTING
    }

    /**
     * Specifies simple <tt>{@link java.lang.String}</tt> as separator to tokenize properties values into
     * single element for vectors and collections.
     * The value specified is used as per {@link java.lang.String#split(String, int)} with int=-1
     *
     * Notice that {@link TokenizerClass} and {@link Separator} do conflict with each-other when they are both specified
     * together on the same level:
     * <ul>
     *     <li>
     *     You cannot specify both {@link TokenizerClass} and {@link Separator} together on the same method
     *     </li>
     *     <li>
     *     You cannot specify both {@link TokenizerClass} and {@link Separator} together on the same class
     *     </li>
     * </ul>
     * in the two above cases an {@link UnsupportedOperationException} will be thrown when the corresponding conversion
     * is executed.
     *
     * @since 1.0.4
     */
    @Retention(RUNTIME)
    @Target({METHOD, TYPE})
    @Documented
    @interface Separator {
        /**
         * @return the value specified is used as per {@link java.lang.String#split(String, int)} with int=-1
         */
        String value();
    }

    /**
     * Specifies simple <tt>{@link Tokenizer}</tt> class to allow the user to specify a custom logic to split
     * the property value into tokens to be used as single elements for vectors and collections.
     *
     * Notice that {@link TokenizerClass} and {@link Separator} do conflict with each-other when they are both specified
     * together on the same level:
     * <ul>
     *     <li>
     *     You cannot specify both {@link TokenizerClass} and {@link Separator} together on the same method
     *     </li>
     *     <li>
     *     You cannot specify both {@link TokenizerClass} and {@link Separator} together on the same class
     *     </li>
     * </ul>
     * in the two above cases an {@link UnsupportedOperationException} will be thrown when the corresponding conversion
     * is executed.
     *
     * @since 1.0.4
     */
    @Retention(RUNTIME)
    @Target({METHOD, TYPE})
    @Documented
    @interface TokenizerClass {
        Class<? extends Tokenizer> value();
    }

    /**
     * Tokenizer interface that specifies how to split a single value into tokens to be used as elements
     * for arrays and collections.
     *
     * @since 1.0.4
     */
    interface Tokenizer {
        String[] tokens(String values);
    }
}
