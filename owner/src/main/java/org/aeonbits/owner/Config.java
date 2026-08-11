/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;


import org.aeonbits.owner.crypto.Decryptor;
import org.aeonbits.owner.crypto.IdentityDecryptor;

import java.io.IOException;
import java.io.Serializable;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.aeonbits.owner.Config.HotReloadType.SYNC;
import static org.aeonbits.owner.Config.LoadType.FIRST;
import static org.aeonbits.owner.util.Util.ignore;
import static org.aeonbits.owner.util.Util.reverse;
/**
 * Marker interface that must be implemented by all Config sub-interfaces.
 * <p>
 * Sub-interfaces may also extend {@link Accessible} to allow some debugging facility, or {@link Reloadable} to allow the
 * user to programmatically reload properties.
 * </p>
 * <p>
 * <b>A method whose return type is another interface extending this one reads the section of the configuration
 * below its own key</b>, as a configuration object of its own: <code>ServerConfig server()</code> resolves
 * <code>server.host</code> and <code>server.port</code>. A {@link java.util.List} or an array of them reads
 * <code>servers[0].host</code>, a {@link java.util.Map} of them reads <code>servers.alpha.host</code>, and a
 * method taking arguments asks for one by name. There is no annotation for any of this: the return type is the
 * whole of the declaration. See {@link org.aeonbits.owner.Config.Prefix} for how the keys compose, and the
 * documentation for the rest.
 * </p>
 *
 * @author Luigi R. Viggiano
 * @see java.util.Properties
 */
public interface Config extends Serializable {
    /**
     * Specifies the policy for loading the properties files. By default the first available properties file specified
     * by {@link Sources} will be loaded, see {@link LoadType#FIRST}. User can also specify that the load policy is
     * {@link LoadType#MERGE} to have the properties files merged: properties are loaded in order from the first file to
     * the last, if there are conflicts in properties names the earlier files loaded prevail.
     * <p>
     * Being a single setting, this annotation is not accumulated across a hierarchy of interfaces: the first one
     * found wins, that is the one declared on the mapping interface if it has one, otherwise the one declared on
     * the first super-interface that has it, in declaration order. Note that the lookup only reaches the
     * <b>direct</b> super-interfaces: an annotation two levels up is silently ignored. That is a known
     * limitation, and it differs from {@link Prefix}, which counts at any depth.
     * </p>
     *
     * @since 1.0.2
     */
    @Retention(RUNTIME)
    @Target(TYPE)
    @Documented
    @interface LoadPolicy {
        /**
         * The policy used to load the properties files.
         *
         * @return the load policy; defaults to {@link LoadType#FIRST}.
         */
        LoadType value() default FIRST;
    }

    /**
     * Specifies the source from which to load the properties file. It has to be specified in a URI string format.
     * By default, allowed protocols are the ones allowed by {@link java.net.URL} plus
     * <code>classpath:path/to/resource.properties</code>, but user can specify his own additional protocols.
     * <p>
     * A source may carry options for the loader that reads it, written in the <b>fragment</b> - after the
     * <code>#</code>, several of them separated by <code>&amp;</code>, as in
     * <code>file:.env#dialect=dotenv&amp;quotes=strip</code>. The query is never touched, since on a remote
     * source it belongs to the server being addressed. An option a loader does not recognise is refused
     * rather than ignored. See {@link org.aeonbits.owner.loaders.SourceOptions}.
     * </p>
     * <p>
     * Unlike {@link LoadPolicy} and {@link HotReload}, this annotation describes a set rather than a single
     * setting, and across a hierarchy of interfaces it <b>accumulates</b>, by design: the URIs declared here come
     * first, followed by those declared on each super-interface, and the resulting list is the one the load
     * policy is applied to. Note that only the <b>direct</b> super-interfaces are read: the sources declared two
     * levels up are silently left out. That is a known limitation, and it differs from {@link Prefix}, which
     * counts at any depth.
     * </p>
     *
     * @since 1.0.2
     */
    @Retention(RUNTIME)
    @Target(TYPE)
    @Documented
    @interface Sources {
        /**
         * The source URIs from which to load the properties, in URI string format.
         *
         * @return the source URIs.
         */
        String[] value();
    }

    /**
     * Default value to be used if no property is found. No quoting (other than normal Java string quoting) is done.
     */
    @Retention(RUNTIME)
    @Target(METHOD)
    @Documented
    @interface DefaultValue {
        /**
         * The default value to be used when no property is found.
         *
         * @return the default value.
         */
        String value();

        /**
         * Whether a property that is found but <b>empty</b> is to be treated as if it were missing, and the
         * default used in its place.
         * <p>
         * A property that is set is normally kept as it is, and the default only covers the case of a property
         * that is not there at all: <code>port=</code> is a value like any other, and an empty value on a type
         * that cannot represent it fails the conversion. That is the same distinction other configuration
         * libraries draw, and it is what keeps a typo like <code>port=8O80</code>, written with the letter O,
         * from silently becoming the default.
         * </p>
         * <p>
         * There is a case where the distinction is not useful though, and this is what this flag is for: a value
         * left empty by a template, as in <code>port=${PORT}</code> with <code>PORT</code> unset, carries no
         * information, and falling back on the default is more useful than failing. Since it changes what a
         * property means, it is opt-in and applies to the annotated method only. A value made of whitespace
         * counts as empty, consistently with the rule that already makes it an empty collection.
         * </p>
         * <p>
         * The default value goes through preprocessing, variable expansion, decryption and parameter formatting
         * exactly as the value it replaces, so the result is the same as if the property had not been there.
         * Note that the substitution happens on the value returned by the method: {@link Accessible#getProperty}
         * and the other methods reading the properties directly keep returning the empty value.
         * </p>
         *
         * @return <code>true</code> if an empty value is to be replaced by the default value.
         * @since 2.0.0
         */
        boolean useOnEmpty() default false;
    }

    /**
     * Marks a property as mandatory.
     * <p>
     * Mandatory properties are validated when the Config object is created: if any of them cannot be resolved
     * (from the sources, the imports or a {@link DefaultValue}), a {@link MissingMandatoryPropertyException}
     * is thrown listing all the missing keys.
     * </p>
     * <p>
     * The check is also enforced on every access: if a mandatory property becomes unavailable later — for
     * instance after a {@link HotReload hot reload} or a {@link Mutable#removeProperty(String)} — reading it
     * throws a {@link MissingMandatoryPropertyException} instead of returning <code>null</code>.
     * </p>
     * <p>
     * When applied to an interface, all the properties declared in that interface are mandatory.
     * Methods taking parameters cannot be validated at creation time, since the property key may depend on the
     * invocation arguments; for those the check happens on access only.
     * </p>
     *
     * @since 2.0.0
     */
    @Retention(RUNTIME)
    @Target({METHOD, TYPE})
    @Documented
    @interface Mandatory {
    }

    /**
     * The key used for lookup for the property.  If not present, the key will be generated based on the unqualified
     * method name.
     */
    @Retention(RUNTIME)
    @Target(METHOD)
    @Documented
    @interface Key {
        /**
         * The key used to look up the property.
         *
         * @return the property key.
         */
        String value();
    }

    /**
     * Specifies a prefix that is prepended to the keys of all the properties declared in the annotated
     * interface.
     * <p>
     * The prefix applies to the key derived from the method name as well as to the one specified with
     * {@link Key}: given <code>@Prefix("server.")</code>, the method <code>String name();</code> is
     * looked up as <code>server.name</code>, and <code>@Key("host.name") String host();</code> is looked
     * up as <code>server.host.name</code>.
     * </p>
     * <p>
     * The prefix is not inherited by sub-interfaces: <b>every method takes the prefix of the interface
     * where it is declared</b>, so the prefix of a sub-interface does not leak onto the methods it
     * inherits, and the methods declared in a super-interface keep the prefix of that super-interface at
     * any depth of the hierarchy.
     * </p>
     * <p>
     * The prefix is subject to {@link DisableableFeature#VARIABLE_EXPANSION variable expansion} just like
     * the rest of the key, so <code>@Prefix("servers.${env}.")</code> is a valid prefix.
     * </p>
     * <p>
     * The prefix is concatenated to the key literally, and no separator is added in between: ending it
     * with the separator you want to use, typically a dot, is the recommended way to write it, but a
     * prefix that does not end with one is a valid naming scheme rather than an error, and is left
     * alone.
     * </p>
     * <p>
     * The prefix can be switched off for a single method or for a whole interface with
     * <code>@DisableFeature(DisableableFeature.PREFIX)</code>.
     * </p>
     * <p>
     * A prefix can also be configured on the {@link Factory} through the <code>owner.key.prefix</code> and
     * <code>owner.key.prefix.from.package</code> properties, for the interfaces that do not declare one of
     * their own. This annotation, being the explicit statement of the two, <b>wins</b> over it rather than
     * being appended to it, while <code>@DisableFeature(PREFIX)</code> switches off both.
     * </p>
     * <p>
     * On a <b>nested</b> configuration interface the two <b>compose</b> instead, the path the object hangs
     * from coming first: the path says where the object was hung and this annotation says how that object
     * names its own keys, so neither is a default the other overrides. An interface carrying a prefix
     * therefore carries it along when nested, and <code>@DisableFeature(PREFIX)</code> switches off the
     * path too.
     * </p>
     *
     * @since 2.0.0
     */
    @Retention(RUNTIME)
    @Target(TYPE)
    @Documented
    @interface Prefix {
        /**
         * The prefix prepended to the keys of the properties declared in the annotated interface.
         *
         * @return the property key prefix.
         */
        String value();
    }

    /**
     * When a value should be decrypted this annotation is needed.
     * If value is not supplied it is assumed that the {@link Decryptor} set in {@link DecryptorClass} will be used.
     * This overrides the {@link EncryptedValue} decryptor defined for the class.
     */
    @Retention(RUNTIME)
    @Target(METHOD)
    @Documented
    @interface EncryptedValue {
        /**
         * The decryptor to use for this value.
         *
         * @return the decryptor class; defaults to {@link IdentityDecryptor}, meaning the
         *         decryptor defined by {@link DecryptorClass} is used.
         */
        Class<? extends Decryptor> value() default IdentityDecryptor.class;
    }

    /**
     * When a value should be decrypted this annotation is needed.
     * This is the class {@link Decryptor}, the default <code>Decryptor</code> used to decrypt a key when none is
     * defined at {@link EncryptedValue}. This allows share the same decryptor for all encrypted keys.
     */
    @Retention(RUNTIME)
    @Target(TYPE)
    @Documented
    @interface DecryptorClass {
        /**
         * The default decryptor shared by all encrypted keys of the class.
         *
         * @return the decryptor class; defaults to {@link IdentityDecryptor}.
         */
        Class<? extends Decryptor> value() default IdentityDecryptor.class;
    }

    /**
     * Marks a property as sensitive, so that its value is replaced by {@link Sensitive#MASK} in the output
     * meant to be read by a human: {@link Accessible#list(java.io.PrintStream)},
     * {@link Accessible#list(java.io.PrintWriter)} and <code>toString()</code>.
     * <p>
     * A password written in clear in a properties file is a value like any other to this library, and
     * <code>cfg.list(System.out)</code> is a line people write while debugging and forget in the code. This
     * annotation is how a property says that it should not end up in a log.
     * </p>
     * <p>
     * <b>Only the human-readable output is masked.</b> {@link Accessible#getProperty(String)},
     * {@link Accessible#fill(java.util.Map)}, {@link Accessible#store(java.io.OutputStream, String)},
     * {@link Accessible#storeToXML(java.io.OutputStream, String)}, the JMX attributes and of course the
     * method itself keep returning the real value: those are how a configuration is read and written back,
     * and masking them would replace the value in the file the next time it is saved. Masking is not
     * encryption — see {@link EncryptedValue} for that — it only keeps a value from being printed by
     * accident.
     * </p>
     * <p>
     * When applied to an interface, all the properties declared in that interface are masked.
     * </p>
     * <p>
     * <b>On a method that reads a group</b> — one returning a {@link java.util.Map}, which resolves to a
     * prefix rather than to a single property — <b>everything under that prefix is masked</b>, since there is
     * no property of that name to mask on its own. All of it goes, not only the entries whose name looks like
     * a secret: the annotation says the group is sensitive, and guessing which parts of it really are would
     * be a different feature. Where one method declares a group sensitive and another reads a key inside it
     * and does not, the mask wins.
     * </p>
     * <p>
     * The keys to mask are worked out when the Config object is created, from the methods that take no
     * parameters: a key that depends on the invocation arguments, or on a variable expanded at access time,
     * is not known in advance and is left alone. A property that no method of the interface reads cannot be
     * masked either, there being nothing to write the annotation on.
     * </p>
     *
     * @since 2.0.0
     */
    @Retention(RUNTIME)
    @Target({METHOD, TYPE})
    @Documented
    @interface Sensitive {
        /** The text printed in place of the value of a sensitive property. */
        String MASK = "********";
    }

    /**
     * Specifies the policy type to use to load the {@link org.aeonbits.owner.Config.Sources} files for properties.
     *
     * @since 1.0.2
     */
    enum LoadType {

        /**
         * The first available of the specified sources will be loaded.
         */
        FIRST {
            @Override
            Properties load(List<URI> uris, LoadersManager loaders) {
                Properties result = new Properties();
                for (URI uri : uris)
                    try {
                        loaders.load(result, uri);
                        break;
                    } catch (IOException ex) {
                        // happens when a file specified in the sources is not found or cannot be read.
                        ignore();
                    }
                return result;
            }
        },

        /**
         * All the specified sources will be loaded and merged. If the same property key is
         * specified from more than one source, the one specified first will prevail.
         */
        MERGE {
            @Override
            Properties load(List<URI> uris, LoadersManager loaders) {
                Properties result = new Properties();
                for (URI uri :  reverse(uris))
                    try {
                        loaders.load(result, uri);
                    } catch (IOException ex) {
                        // happens when a file specified in the sources is not found or cannot be read.
                        ignore();
                    }
                return result;
            }
        };

        abstract Properties load(List<URI> uris, LoadersManager loaders);
    }

    /**
     * Specify that the class implements hot reloading of properties from filesystem baked {@link Sources} (hot
     * reloading can't be applied to all types of URIs).
     * <p>
     * It is possible to specify an interval to indicate how frequently the library shall check the files for
     * modifications and perform the reload.
     * </p>
     * Examples:
     * <pre>
     *      &#64;HotReload    // will check for file changes every 5 seconds.
     *      &#64;Sources("file:foo/bar/baz.properties")
     *      interface MyConfig extends Config { ... }
     *
     *      &#64;HotReload(2)    // will check for file changes every 2 seconds.
     *      &#64;Sources("file:foo/bar/baz.properties")
     *      interface MyConfig extends Config { ... }
     *
     *      &#64;HotReload(500, unit = TimeUnit.MILLISECONDS);  // will check for file changes every 500 milliseconds.
     *      &#64;Sources("file:foo/bar/baz.properties")
     *      interface MyConfig extends Config { ... }
     *
     *      &#64;HotReload(type=HotReloadType.ASYNC);  // will use ASYNC reload type: will span a separate thread
     *                                                 // that will check for the file change every 5 seconds (default).
     *      &#64;Sources("file:foo/bar/baz.properties")
     *      interface MyConfig extends Config { ... }
     *
     *      &#64;HotReload(2, type=HotReloadType.ASYNC);  // will use ASYNC reload type and will check every 2 seconds.
     *      &#64;Sources("file:foo/bar/baz.properties")
     *      interface MyConfig extends Config { ... }
     * </pre>
     *
     * <p>
     * To intercept the {@link org.aeonbits.owner.event.ReloadEvent} see {@link Reloadable#addReloadListener(org.aeonbits.owner.event.ReloadListener)}.
     * <p>
     * Being a single setting, this annotation follows the same rule as {@link LoadPolicy} across a hierarchy of
     * interfaces: the first one found wins, and the lookup only reaches the <b>direct</b> super-interfaces, so an
     * annotation two levels up is silently ignored and no reloading takes place.
     *
     * @since 1.0.4
     */
    @Retention(RUNTIME)
    @Target(TYPE)
    @Documented
    @interface HotReload {
        /**
         * The interval, expressed in seconds (by default), to perform checks on the filesystem to identify modified
         * files and eventually perform the reloading of the properties. By default is 5 seconds.
         *
         * @return the hot reload value; default is 5.
         */
        long value() default 5;

        /**
         * <p>
         * The time unit for the interval. By default it is {@link TimeUnit#SECONDS}.
         * </p>
         * <p>
         * Date resolution vary from filesystem to filesystem.<br>
         * For instance, for Ext3, ReiserFS and HSF+ the date resolution is of 1 second.<br>
         * For FAT32 the date resolution for the last modified time is 2 seconds. <br>
         * For Ext4 the date resolution is in nanoseconds.
         * </p>
         * <p>
         * So, it is a good idea to express the time unit in seconds or more, since higher time resolution
         * will probably not be supported by the underlying filesystem.
         * </p>
         * @return the time unit; default is SECONDS.
         */
        TimeUnit unit() default SECONDS;

        /**
         * The type of HotReload to use. It can be:
         *
         * <p>
         * {@link HotReloadType#SYNC Synchronous}: the configuration file is checked when a method is invoked on the
         * config object. So if the config object is not used for some time, the configuration doesn't get reloaded,
         * until its next usage. i.e. until next method invocation.
         * <p>
         * {@link HotReloadType#ASYNC}: the configuration file is checked by a background thread despite the fact that
         * the config object is used or not.
         *
         * @return the hot reload type; default is SYNC.
         */
        HotReloadType type() default SYNC;
    }

    /**
     * Allows to specify which type of HotReload should be applied.
     */
    enum HotReloadType {
        /**
         * The hot reload will happen when one of the methods is invoked on the <code>Config</code> class.
         */
        SYNC,

        /**
         * The hot reload will happen in background at the specified interval.
         */
        ASYNC
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
        /**
         * The features to disable.
         *
         * @return the features to disable.
         */
        DisableableFeature[] value();
    }

    /**
     * This enum contains the features that can be disabled using the annotation {@link DisableFeature}.
     *
     * @since 1.0.4
     */
    enum DisableableFeature {
        /** Disables variable expansion, i.e. <code>${...}</code> substitution in property values. */
        VARIABLE_EXPANSION,
        /** Disables parameter formatting, i.e. positional <code>{0}</code> argument substitution. */
        PARAMETER_FORMATTING,
        /**
         * Disables the {@link Prefix} declared on the interface, so that the annotated method — or every
         * method of the annotated interface — is looked up with its bare key.
         *
         * @since 2.0.0
         */
        PREFIX
    }

    /**
     * Specifies simple <code>{@link String}</code> as separator to tokenize properties values specified as a
     * single string value, into elements for vectors and collections.
     * The value specified is used as per {@link String#split(String, int)} with int=-1, every element is also
     * trimmed out from spaces using {@link String#trim()}.
     *
     * Notice that {@link TokenizerClass} and {@link Separator} do conflict with each-other when they are both specified
     * together on the same level:
     * <ul>
     *     <li>
     *     You cannot specify {@link TokenizerClass} and {@link Separator} both together on the same method
     *     </li>
     *     <li>
     *     You cannot specify {@link TokenizerClass} and {@link Separator} both together on the same class
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
     * Specifies a <code>{@link Tokenizer}</code> class to allow the user to define a custom logic to split
     * the property value into tokens to be used as single elements for vectors and collections.
     *
     * Notice that {@link TokenizerClass} and {@link Separator} do conflict with each-other when they are both specified
     * together on the same level:
     * <ul>
     *     <li>
     *     You cannot specify {@link TokenizerClass} and {@link Separator} both together on the same method
     *     </li>
     *     <li>
     *     You cannot specify {@link TokenizerClass} and {@link Separator} both together on the same class
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
        /**
         * The tokenizer used to split the property value into elements.
         *
         * @return the tokenizer class.
         */
        Class<? extends Tokenizer> value();
    }

    /**
     * Specifies a <code>{@link Converter}</code> class to allow the user to define a custom conversion logic for the
     * type returned by the method. If the method returns a collection, the Converter is used to convert a single
     * element.
     */
    @Retention(RUNTIME)
    @Target(METHOD)
    @Documented
    @interface ConverterClass {
        /**
         * The converter used to convert the property value to the method return type.
         *
         * @return the converter class.
         */
        Class<? extends Converter> value();
    }

    /**
     * Specifies a <code>{@link Converter}</code> class to convert the property value into the {@link Collection}
     * returned by the method, in a single step.
     * <p>
     * This differs from {@link ConverterClass}, which converts one element at a time after OWNER has split the
     * property value: here the raw value is handed over untouched and the converter is responsible for the whole
     * process, including honouring {@link Separator} and {@link TokenizerClass} if it wants to. It is therefore the
     * way to opt out of the built-in tokenization, for instance when the property holds a single indivisible
     * document such as JSON, or when the collection must be of a type OWNER cannot instantiate on its own.</p>
     * <p>
     * The annotated method must return a {@link Collection}; using it on any other return type raises an
     * {@link UnsupportedOperationException} when the property is read.</p>
     *
     * @since 2.0.0
     */
    @Retention(RUNTIME)
    @Target(METHOD)
    @Documented
    @interface CollectionConverterClass {
        /**
         * The converter used to convert the property value to the collection returned by the method.
         *
         * @return the converter class.
         */
        Class<? extends Converter<? extends Collection<?>>> value();
    }

    /**
     * Specifies a <code>{@link Preprocessor}</code> class to allow the user to define a custom logic to pre-process
     * the property value before being used by the library.
     *
     * @since 1.0.9
     */
    @Retention(RUNTIME)
    @Target({METHOD, TYPE})
    @Documented
    @interface PreprocessorClasses {
        /**
         * The preprocessors applied, in order, to the property value.
         *
         * @return the preprocessor classes.
         */
        Class<? extends Preprocessor>[] value();
    }

}
