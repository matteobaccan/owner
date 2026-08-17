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
import org.aeonbits.owner.event.*;
import org.aeonbits.owner.loaders.SourceOptions;
import org.aeonbits.owner.util.Reflection;
import org.aeonbits.owner.util.Util;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.Collections.synchronizedList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.aeonbits.owner.Config.LoadType.FIRST;
import static org.aeonbits.owner.PropertiesMapper.defaults;
import static org.aeonbits.owner.util.Util.*;

/**
 * Loads properties and manages access to properties handling concurrency.
 *
 * @author Luigi R. Viggiano
 */
class PropertiesManager implements Reloadable, Accessible, Mutable, Traceable {

    private static final Logger LOGGER = Logger.getLogger(PropertiesManager.class.getName());

    private final Class<? extends Config> clazz;
    private final Map<?, ?>[] imports;
    private final Properties properties;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ReadLock readLock = lock.readLock();
    private final WriteLock writeLock = lock.writeLock();

    private final LoadType loadType;
    private final List<URI> uris;
    private final HotReloadLogic hotReloadLogic;

    private volatile boolean loading = false;

    /**
     * The last hot reload failure already reported, so that a source which stays broken is named once rather
     * than at every check. Volatile because successive runs of a scheduled task need not be the same thread.
     */
    private volatile String lastReportedReloadFailure;

    final List<ReloadListener> reloadListeners = synchronizedList(new LinkedList<>());

    private Object proxy;
    private final LoadersManager loaders;

    /**
     * The handlers a <code>${$name::payload}</code> marker may name, kept for the same reason
     * {@link #keyPrefix} is: they belong to the factory that created this configuration, and a
     * configuration keeps answering the way it was born even if the factory is reconfigured afterwards.
     */
    private final HandlersManager handlers;

    /**
     * The prefix configured on the factory, captured when this object is created rather than looked up later:
     * that is what keeps the keys of a live Config object from moving when the factory is reconfigured, keeps
     * a reload resolving the same keys, and lets the mapping travel with the object when it is serialized.
     */
    private final KeyPrefix keyPrefix;


    /**
     * A cache of encryptedKeys with its decryptor.
     * <p>
     * This allows each key has its own decryptor.
     * Reflection is slow.
     */
    private Map<Method, Decryptor> encryptedKeys = new HashMap<>();

    /**
     * The keys whose value is replaced by {@link Sensitive#MASK} in the human-readable output. Worked out
     * once when this object is created: the annotation is on the method, and reflection is slow.
     */
    private final Set<String> sensitiveKeys = new HashSet<>();

    /**
     * The keys a method declares {@link EncryptedValue}, by name rather than by method.
     * <p>
     * {@link #encryptedKeys} answers "which decryptor does this method use", which is what decryption
     * needs. This answers "is the property behind this key an encrypted one", which is what
     * {@link #reportReferencesToEncryptedValues} needs, and no map keyed by method can: a variable names a
     * key and nothing else. Only the methods taking no arguments are here, for the same reason
     * {@link #sensitiveKeys} holds only those — a key that depends on the invocation is not known in
     * advance.
     * </p>
     */
    private final Set<String> encryptedKeyNames = new HashSet<>();

    /**
     * The prefixes below which every key is masked, one for each {@link Sensitive} method that reads a group
     * of properties rather than a single one. Such a method resolves to a prefix and not to a property, so
     * there is no single name to put in {@link #sensitiveKeys}: what has to be hidden is everything under it.
     */
    private final Set<String> sensitivePrefixes = new HashSet<>();

    /**
     * Where each property came from, kept at the one moment it can still be known: the merge is exactly
     * what makes a value indistinguishable from the one it overwrote, and from a default. See
     * {@link Traceable}.
     */
    private final Map<String, Origin> origins = new HashMap<>();

    /** Whether the interface asked for its sources by name, rather than letting the default probe look. */
    private final boolean sourcesWereDeclared;

    /**
     * The name of the {@link Factory} property that turns this library's warnings into refusals:
     * <code>owner.strict</code>, read when the Config object is created and kept for its life.
     */
    static final String STRICT = "owner.strict";

    /**
     * Whether this configuration refuses what it would otherwise only warn about.
     * <p>
     * The default is <b>off</b>, and off is the behaviour this library has always had: a source that
     * cannot be read is passed over, the configuration carries on with what it did read and with its
     * default values, and a warning says so. That is useful — a fallback is meant to work — but it means
     * the way this library fails is to keep working and answer with a default, which is invisible until
     * somebody notices the wrong value in production.
     * </p>
     * <p>
     * Turned on, every warning that has a caller to refuse becomes an
     * {@link UnsupportedOperationException} when the Config object is created. <b>What counts as a failure
     * is not a new list</b>: it is the warnings, which were already chosen to leave the legitimate cases
     * alone. A source that is merely absent stays silent under strict too, because
     * {@link Config.LoadType#FIRST} expects misses by design and refusing them would break the commonest
     * shape a configuration has.
     * </p>
     * <p>
     * A reload that fails is deliberately outside this: it happens later, on a scheduled thread with
     * nobody to refuse, and turning a transient failure into a crash is worse than the warning. The
     * event API is where a reload problem is answered.
     * </p>
     */
    private final boolean strict;

    /** Whether any source answered during the load now running; see {@link #reportIfNothingAnswered}. */
    private boolean somethingWasRead;

    /**
     * The failure already reported for each source, so that one which stays broken is named once rather
     * than at every reload. Same reasoning as {@link #lastReportedReloadFailure}, per source.
     */
    private final Map<URI, String> reportedFailures = new HashMap<>();

    /** Whether the load that read nothing at all has already been reported. */
    private boolean reportedEmptyLoad;

    /**
     * The keys that {@link Config.DisableableFeature#RELAXED_BINDING relaxed binding} applies to, which is
     * every key a method of this configuration reads a single property with — the nested interfaces
     * included, under the path that nests them.
     * <p>
     * Kept for {@link #reportAmbiguousKeys} and for nothing else: the lookup itself derives the forms from
     * the key it is handed, since a key carrying a variable or the arguments of a call is not knowable
     * here. Those are the ones missing from this set, for the reason {@link #sensitiveKeys} misses them
     * too, and a configuration writing one of them in two spellings goes unreported rather than reported
     * wrongly.
     * </p>
     */
    private final Set<String> relaxableKeys = new LinkedHashSet<>();

    /**
     * Whether the spellings of a key have been looked for yet. On the <b>first</b> load only, exactly as
     * {@link #lookedForCipherReferences} is: under {@link #strict} this refuses, and a refusal belongs to
     * the moment the object is created rather than to a reload on a scheduled thread.
     */
    private boolean lookedForAmbiguousKeys;

    /**
     * Whether the values referring to an encrypted property have been looked for yet. Looked for on the
     * <b>first</b> load only, and not because saying it twice would be noise: under
     * {@link #strict} this refuses, and a refusal belongs to the moment the object is created rather than
     * to a reload happening on a scheduled thread. See {@link #reportReferencesToEncryptedValues}.
     */
    private boolean lookedForCipherReferences;

    final List<PropertyChangeListener> propertyChangeListeners = synchronizedList(
            new LinkedList<PropertyChangeListener>() {
                @Override
                public boolean remove(Object o) {
                    Iterator iterator = iterator();
                    while (iterator.hasNext()) {
                        Object item = iterator.next();
                        if (item.equals(o)) {
                            iterator.remove();
                            return true;
                        }
                    }
                    return false;
                }
            });

    PropertiesManager(Class<? extends Config> clazz, Properties properties, ScheduledExecutorService scheduler,
                      VariablesExpander expander, LoadersManager loaders, KeyPrefix keyPrefix, boolean strict,
                      Map<?, ?>... imports) {
        this(clazz, properties, scheduler, expander, loaders, new HandlersManager(), keyPrefix, strict, imports);
    }

    PropertiesManager(Class<? extends Config> clazz, Properties properties, ScheduledExecutorService scheduler,
                      VariablesExpander expander, LoadersManager loaders, HandlersManager handlers,
                      KeyPrefix keyPrefix, boolean strict, Map<?, ?>... imports) {
        this.clazz = clazz;
        this.properties = properties;
        this.loaders = loaders;
        this.handlers = handlers;
        this.imports = imports;
        this.keyPrefix = keyPrefix;
        this.strict = strict;
        ConfigURIFactory urlFactory = new ConfigURIFactory(clazz.getClassLoader(), expander);
        Map<Class<?>, Sources> declared = Annotations.findAnnotations(clazz, Sources.class);
        sourcesWereDeclared = !declared.isEmpty();
        uris = toURIs(declared, urlFactory);

        LoadPolicy loadPolicy = Annotations.findAnnotation(clazz, LoadPolicy.class);
        loadType = (loadPolicy != null) ? loadPolicy.value() : FIRST;

        HotReload hotReload = Annotations.findAnnotation(clazz, HotReload.class);
        if (hotReload != null) {
            long interval = HotReloadLogic.intervalMillis(hotReload, clazz, expander);
            hotReloadLogic = new HotReloadLogic(hotReload, interval, uris, this);

            if (hotReloadLogic.isAsync())
                scheduler.scheduleAtFixedRate(this::checkAndReloadKeepingTheSchedule,
                        interval, interval, MILLISECONDS);
        } else {
            hotReloadLogic = null;
        }

        // We try to identify the DecryptorClass annotation, to assign the Decryptor to this configuration.
        // If it isn't present then we assign the IdentityDecryptor.
        Decryptor classDecryptor = declaredDecryptor(clazz, null);

        // the same is read off the nested interfaces, under the key that nests them: a configuration object
        // one level down is part of this configuration, and a rule that stopped at the root would mask and
        // decrypt only half of it
        scanAnnotations(clazz, keyPrefix, classDecryptor);
        NestedProperties.forEachNested(clazz, keyPrefix,
                (nested, prefix) -> scanAnnotations(nested, prefix, declaredDecryptor(nested, classDecryptor)));
        NestedProperties.forEachNestedGroup(clazz, keyPrefix,
                (nested, group) -> scanGroupAnnotations(nested, group,
                        declaredDecryptor(nested, classDecryptor)));

        reportTheKeys();
    }

    /**
     * The same, for an interface reached through a group whose keys nobody can name in advance: an element
     * of a list, a value of a map, the answer of an accessor taking arguments.
     * <p>
     * The decryptor is registered against the method and so does not care; what has to be masked has no key
     * to be named by, and the whole group is masked instead. See
     * {@link NestedProperties#forEachNestedGroup}.
     * </p>
     */
    private void scanGroupAnnotations(Class<?> clazz, String groupPrefix, Decryptor classDecryptor) {
        for (Method method : clazz.getMethods()) {
            if (isSensitive(method))
                sensitivePrefixes.add(groupPrefix);

            if (PropertiesMapper.isEncryptedValue(method)) {
                Class<? extends Decryptor> declared = method.getAnnotation(EncryptedValue.class).value();
                encryptedKeys.put(method, declared != IdentityDecryptor.class
                        ? Util.newInstance(declared)
                        : classDecryptor);
            }
        }
    }

    /**
     * The decryptor an interface declares with {@link DecryptorClass}, or that any interface above it does.
     * A nested interface that declares none uses the one of the configuration holding it, the tree being a
     * single configuration; the root falls back on the {@link IdentityDecryptor}.
     * <p>
     * Until 2.0.0 this was read off the interface itself and nowhere else, not even its direct
     * super-interfaces — so a {@code @DecryptorClass} written on a base interface did nothing, and did it
     * quietly: an {@link EncryptedValue} then came back as the ciphertext stored in the file, which is a
     * value like any other and fails, if it fails at all, wherever it is finally used.
     * </p>
     */
    private static Decryptor declaredDecryptor(Class<?> clazz, Decryptor fallback) {
        DecryptorClass declared = Annotations.findAnnotation(clazz, DecryptorClass.class);
        if (declared != null)
            return Util.newInstance(declared.value());
        return fallback != null ? fallback : Util.newInstance(IdentityDecryptor.class);
    }

    /** Reflection is slow, so what the methods of an interface declare is read once, when it is created. */
    private void scanAnnotations(Class<?> clazz, KeyPrefix prefix, Decryptor classDecryptor) {
        for (Method method : clazz.getMethods()) {
            collectRelaxableKey(method, prefix);

            // a key that depends on the invocation arguments is not known in advance: those methods
            // are skipped rather than masked under a key that would never match
            if (isSensitive(method) && method.getParameterTypes().length == 0) {
                String key = PropertiesMapper.key(method, prefix);
                // a method reading a group, or a whole nested object, resolves to a prefix and not to a
                // property: there is no key of that name in the file, and masking it alone would hide nothing
                if (NestedProperties.nests(method))
                    addWithItsSpellings(sensitivePrefixes, NestedProperties.pathOf(key), method);
                else if (PropertiesAggregator.aggregates(method))
                    // the prefix of a group is read as it is written, so no other spelling of it is ever
                    // the source of a value this method answers with
                    sensitivePrefixes.add(PropertiesAggregator.prefixOf(key));
                else
                    addWithItsSpellings(sensitiveKeys, key, method);
            }

            if (PropertiesMapper.isEncryptedValue(method)) {
                Class<? extends Decryptor> declared = method.getAnnotation(EncryptedValue.class).value();
                encryptedKeys.put(method, declared != IdentityDecryptor.class
                        ? Util.newInstance(declared)
                        : classDecryptor);
                if (method.getParameterTypes().length == 0)
                    addWithItsSpellings(encryptedKeyNames, PropertiesMapper.key(method, prefix), method);
            }
        }
    }

    /**
     * Registers a key under every spelling relaxed binding would read it by, and not only under the one
     * the method resolves to.
     * <p>
     * These two sets are the places where a key is matched <b>by name</b> against the properties as they
     * were loaded — what to mask, and what a variable must not refer to — and the name in the file is
     * exactly what relaxed binding stopped pinning down. Left as one name, a
     * <code>@Sensitive String password()</code> reading <code>PASSWORD</code> out of the environment
     * would answer the method correctly and print the secret in <code>list()</code>.
     * </p>
     * <p>
     * The over-approximation this brings is the one already chosen where a group and a key inside it
     * disagree: a secret printed because nobody named the key it arrived under is the mistake that costs
     * something, and a value masked that need not have been is read as over-caution and no more.
     * </p>
     */
    private static void addWithItsSpellings(Set<String> keys, String key, Method method) {
        keys.add(key);
        if (!DisableableFeature.RELAXED_BINDING.isDisabledFor(method))
            keys.addAll(RelaxedKeys.alternativesTo(key));
    }

    /**
     * Notes down the key of a method that reads a single property, which is the only shape the spellings
     * of a key can be checked for.
     * <p>
     * A method reading a whole group — a nested section, a {@link Map} — resolves to a prefix and not to a
     * property, and relaxed binding does not reach a prefix: see
     * {@link PropertiesInvocationHandler#lookupValue}. A method taking arguments, or one whose key holds a
     * variable, has no key until it is called.
     * </p>
     */
    private void collectRelaxableKey(Method method, KeyPrefix prefix) {
        if (isLibraryMethod(method) || Reflection.isDefault(method))
            return;
        if (method.getParameterTypes().length > 0)
            return;
        if (NestedProperties.nests(method) || PropertiesAggregator.aggregates(method))
            return;
        if (DisableableFeature.RELAXED_BINDING.isDisabledFor(method))
            return;
        String key = PropertiesMapper.key(method, prefix);
        if (!key.contains("${"))
            relaxableKeys.add(key);
    }

    /**
     * Returns the prefix this Config object resolves its keys with, so that whoever needs a key asks for it
     * instead of deriving it from the method alone.
     *
     * @return the prefix; {@link KeyPrefix#NONE} when the factory declares none.
     */
    KeyPrefix keyPrefix() {
        return keyPrefix;
    }

    private static boolean isSensitive(Method method) {
        return method.getAnnotation(Sensitive.class) != null
                || method.getDeclaringClass().getAnnotation(Sensitive.class) != null;
    }

    /**
     * Returns the properties as they are to be shown to a human: the value of every {@link Sensitive} key
     * replaced by {@link Sensitive#MASK}, everything else untouched.
     * <p>
     * The original object is handed back when there is nothing to mask, which is the common case, so a
     * configuration that declares no sensitive property pays nothing for this.
     * </p>
     */
    private Properties masked() {
        readLock.lock();
        try {
            if (sensitiveKeys.isEmpty() && sensitivePrefixes.isEmpty())
                return properties;
            Properties result = new Properties();
            for (Enumeration<?> names = properties.propertyNames(); names.hasMoreElements(); ) {
                String name = (String) names.nextElement();
                result.setProperty(name, isSensitiveKey(name) ? Sensitive.MASK : properties.getProperty(name));
            }
            return result;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Tells whether a property is to be masked, either because a method reads exactly it and is sensitive, or
     * because it sits below a group that is.
     * <p>
     * Where the two disagree — one method declaring a group sensitive while another reads a key inside it and
     * does not — the mask wins. Printing a secret that was declared as one is the mistake that costs
     * something; masking a value that need not have been is read as over-caution and no more.
     * </p>
     */
    private boolean isSensitiveKey(String name) {
        if (sensitiveKeys.contains(name))
            return true;
        for (String prefix : sensitivePrefixes)
            if (name.startsWith(prefix))
                return true;
        return false;
    }

    /**
     * If method contains the EncryptedValue annotation it Decrypts the value with the associated {@link Decryptor}.
     *
     * @param method with the key definition.
     * @param value the value to decrypt when the method contains the EncryptedValue annotation
     * @return
     *      the <code>value</code> if the method doesn't contains the EncryptedValue annotation
     *      or the <code>result of decrypt the value</code> if it does.
     */
    String decryptIfNecessary(Method method, String value) {
        // Value can't be null, it has been checked previously in PropertiesInvocationHandler.resolveProperty
        if (this.encryptedKeys.containsKey(method)) {
            Decryptor decryptor = this.encryptedKeys.get(method);
            return decryptor.decrypt(value);
        }
        return value;
    }

    /**
     * Turns the declared sources into the URIs that will be tried, in order: those of the mapping interface
     * first, then those of every interface above it, and the default specs only when <b>nobody</b> in the
     * hierarchy declared any.
     * <p>
     * The convention is a fallback for a configuration that names no source, not an extra source appended to
     * the ones that were named: an interface carrying {@code @Sources} reads what it asked for and nothing
     * else. Until 2.0.0 the two were the same call, so the defaults were looked for once per interface with
     * no annotation — twice for the plainest configuration there is, since {@code Config} itself has none —
     * and were quietly appended even to a configuration that had declared its sources, which the
     * {@code CONFIG} line then contradicted by saying <em>no @Sources</em> about an interface that had one.
     * </p>
     * <p>
     * A spec that resolves to nothing is dropped here, which is right — that is how {@code @LoadPolicy}
     * falls back on the next source — and is also the reason a configuration full of defaults can have no
     * explanation whatever. Both halves are therefore said at {@code CONFIG}: what was looked for, and what
     * of it was not there. Together with the line naming the loader that answered, that is most of "why is
     * my property missing" without anyone having to guess.
     * </p>
     */
    private List<URI> toURIs(Map<Class<?>, Sources> declared, ConfigURIFactory uriFactory) {
        if (declared.isEmpty()) {
            String[] specs = defaultSpecs(uriFactory);
            LOGGER.log(Level.CONFIG, () -> String.format("%s: no @Sources, looking for: %s",
                    clazz.getName(), Arrays.toString(specs)));
            return resolve(specs, uriFactory);
        }

        List<URI> result = new ArrayList<>();
        for (Map.Entry<Class<?>, Sources> entry : declared.entrySet()) {
            Class<?> declaring = entry.getKey();
            String[] specs = entry.getValue().value();
            LOGGER.log(Level.CONFIG, () -> String.format("%s: sources declared%s: %s", clazz.getName(),
                    declaring == clazz ? "" : " on " + declaring.getName(), Arrays.toString(specs)));
            result.addAll(resolve(specs, uriFactory));
        }
        return result;
    }

    private List<URI> resolve(String[] specs, ConfigURIFactory uriFactory) {
        List<URI> result = new ArrayList<>();
        for (String spec : specs) {
            try {
                URI uri = uriFactory.newURI(spec);
                if (uri == null)
                    LOGGER.log(Level.CONFIG, () -> String.format("%s: %s was not found, skipping it",
                            clazz.getName(), spec));
                else
                    result.add(uri);
            } catch (URISyntaxException e) {
                throw unsupported(e, "Can't convert '%s' to a valid URI", spec);
            }
        }
        return result;
    }

    private String[] defaultSpecs(ConfigURIFactory uriFactory) {
        String prefix = uriFactory.toClasspathURLSpec(clazz.getName());
        return loaders.defaultSpecs(prefix);
    }

    Properties load() {
        writeLock.lock();
        try {
            return load(properties);
        } finally {
            writeLock.unlock();
        }
    }

    private Properties load(Properties props) {
        try {
            loading = true;
            origins.clear();
            record(defaults(props, clazz, keyPrefix), Origin.ofDefaultValue());

            // the origins are recorded as each source is read, so the last one to write a key is the one
            // recorded against it - which is the source whose value survived the merge
            merge(props, doLoad());
            reportIfNothingAnswered();

            Map<?, ?>[] imported = reverse(imports);
            for (int i = 0; i < imported.length; i++) {
                merge(props, imported[i]);
                record(imported[i].keySet(), Origin.ofImport(imported.length - 1 - i));
            }
            refuseEncryptedValuesHoldingAMarker(props);
            reportReferencesToEncryptedValues(props);
            reportAmbiguousKeys(props);
            return props;
        } finally {
            loading = false;
        }
    }

    /**
     * Records where a set of keys came from. Called as each place is read, and later calls overwrite
     * earlier ones for the same key, which is what makes the origin recorded the one that won the merge.
     * <p>
     * Always called holding the write lock, as both callers of {@link #load(Properties)} do.
     * </p>
     */
    private void record(Collection<?> keys, Origin origin) {
        for (Object key : keys)
            if (key instanceof String)
                origins.put((String) key, origin);
    }

    @Delegate
    @Override
    public Origin originOf(String key) {
        readLock.lock();
        try {
            return properties.getProperty(key) == null ? null : origins.get(key);
        } finally {
            readLock.unlock();
        }
    }

    @Delegate
    @Override
    public Map<String, Origin> origins() {
        readLock.lock();
        try {
            Map<String, Origin> result = new LinkedHashMap<>();
            for (Enumeration<?> names = properties.propertyNames(); names.hasMoreElements(); ) {
                String name = (String) names.nextElement();
                Origin origin = origins.get(name);
                if (origin != null)
                    result.put(name, origin);
            }
            return result;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Tells whether anything below the given prefix was written rather than merely defaulted.
     * <p>
     * The question the merged properties cannot answer on their own — <i>was this section written, or is it
     * only the defaults of its own interface showing through?</i> — asked of the origins, which are what
     * remembers.
     * </p>
     *
     * @param prefix the path to look below, separator included.
     * @return <code>true</code> when at least one key below it came from a source, an import, or a later
     *         {@link Mutable#setProperty(String, String)}.
     */
    boolean anythingWrittenUnder(String prefix) {
        readLock.lock();
        try {
            for (Enumeration<?> names = properties.propertyNames(); names.hasMoreElements(); ) {
                String name = (String) names.nextElement();
                if (!name.startsWith(prefix) || name.length() <= prefix.length())
                    continue;
                Origin origin = origins.get(name);
                if (origin == null || origin.isWritten())
                    return true;
            }
            return false;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Runs one asynchronous hot reload check without ever letting it out.
     * <p>
     * {@link java.util.concurrent.ScheduledExecutorService#scheduleAtFixedRate scheduleAtFixedRate}
     * suppresses every later execution of a task that throws, so a single failed reload — a file caught
     * halfway through being rewritten, a source momentarily malformed, a loader refusing what it was given —
     * would silently stop the configuration reloading for the rest of the life of the process. It would go on
     * answering with the values it happened to hold, and nothing anywhere would say so.
     * </p>
     * <p>
     * So the failure is reported and the schedule is kept: the next tick tries again, and a fault that
     * clears itself costs one line in the log instead of everything after it. {@link Error} is deliberately
     * not caught — a process out of memory is not a thing to carry on through.
     * </p>
     */
    private void checkAndReloadKeepingTheSchedule() {
        try {
            hotReloadLogic.checkAndReload();
            // recovered: should the same fault return, it is worth hearing about again
            lastReportedReloadFailure = null;
        } catch (RuntimeException e) {
            reportReloadFailure(e);
        }
    }

    /**
     * Says it once. A check runs as often as the {@link Config.HotReload} interval says, so a source that
     * stays broken would otherwise fill the log at that rate; only a failure differing from the one before it
     * is worth a line.
     */
    private void reportReloadFailure(RuntimeException failure) {
        String signature = failure.getClass().getName() + ": " + failure.getMessage();
        if (signature.equals(lastReportedReloadFailure))
            return;
        lastReportedReloadFailure = signature;
        LOGGER.log(Level.WARNING, failure, () -> String.format(
                "Hot reload of %s failed. It keeps the values it already had and will try again at the next "
                        + "check; this is reported once, and again only if the failure changes or clears.",
                clazz.getName()));
    }

    @Delegate
    @Override
    public void reload() {
        writeLock.lock();
        try {
            Properties loaded = load(new Properties());
            List<PropertyChangeEvent> events =
                    fireBeforePropertyChangeEvents(keys(properties, loaded), properties, loaded);
            ReloadEvent reloadEvent = fireBeforeReloadEvent(events, properties, loaded);
            applyPropertyChangeEvents(events, null);
            firePropertyChangeEvents(events);
            fireReloadEvent(reloadEvent);
        } catch (RollbackBatchException e) {
            ignore();
        } finally {
            writeLock.unlock();
        }
    }

    private Set<?> keys(Map<?, ?>... maps) {
        Set<Object> keys = new HashSet<>();
        for (Map<?, ?> map : maps)
            keys.addAll(map.keySet());
        return keys;
    }

    /**
     * Applies the changes, and says where the new values came from.
     * <p>
     * The origin is the caller's to state, because the three callers differ on exactly that point and the
     * write itself cannot tell them apart. A reload has already recorded the sources that answered, so it
     * passes <code>null</code> and leaves them alone — without which every key a reload changed would be
     * attributed to whoever called <code>reload()</code>. A {@link Mutable#load(java.io.InputStream)} did
     * write those values at run time and says so. A {@link #clear()} removes, and a removal takes the origin
     * with it wherever it comes from.
     * </p>
     */
    private void applyPropertyChangeEvents(List<PropertyChangeEvent> events, Origin origin) {
        for (PropertyChangeEvent event : events) {
            performSetProperty(event.getPropertyName(), event.getNewValue());
            if (origin != null && event.getNewValue() != null)
                origins.put(event.getPropertyName(), origin);
        }
    }

    private void fireReloadEvent(ReloadEvent reloadEvent) {
        for (ReloadListener listener : reloadListeners)
            listener.reloadPerformed(reloadEvent);
    }

    private ReloadEvent fireBeforeReloadEvent(List<PropertyChangeEvent> events, Properties oldProperties,
                                              Properties newProperties) throws RollbackBatchException {
        ReloadEvent reloadEvent = new ReloadEvent(proxy, events, oldProperties, newProperties);
        for (ReloadListener listener : reloadListeners)
            if (listener instanceof TransactionalReloadListener)
                ((TransactionalReloadListener) listener).beforeReload(reloadEvent);
        return reloadEvent;
    }


    @Delegate
    @Override
    public void addReloadListener(ReloadListener listener) {
        if (listener != null)
            reloadListeners.add(listener);
    }

    @Delegate
    @Override
    public void removeReloadListener(ReloadListener listener) {
        if (listener != null)
            reloadListeners.remove(listener);
    }

    @Delegate
    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        if (listener != null)
            propertyChangeListeners.add(listener);
    }

    @Delegate
    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        if (listener != null)
            propertyChangeListeners.remove(listener);
    }

    @Delegate
    @Override
    public void addPropertyChangeListener(final String propertyName, final PropertyChangeListener listener) {
        if (propertyName == null || listener == null) return;

        final boolean transactional = listener instanceof TransactionalPropertyChangeListener;
        propertyChangeListeners.add(new PropertyChangeListenerWrapper(propertyName, listener, transactional));
    }

    private static class PropertyChangeListenerWrapper implements TransactionalPropertyChangeListener, Serializable {

        private final String propertyName;
        private final PropertyChangeListener listener;
        private final boolean transactional;

        PropertyChangeListenerWrapper(String propertyName, PropertyChangeListener listener,
                                      boolean transactional) {
            this.propertyName = propertyName;
            this.listener = listener;
            this.transactional = transactional;

        }

        @Override
        public void beforePropertyChange(PropertyChangeEvent event) throws RollbackOperationException,
                RollbackBatchException {
            if (transactional && propertyNameMatches(event))
                ((TransactionalPropertyChangeListener) listener).beforePropertyChange(event);
        }

        private boolean propertyNameMatches(PropertyChangeEvent event) {
            return propertyName.equals(event.getPropertyName());
        }

        @Override
        public void propertyChange(PropertyChangeEvent event) {
            if (propertyNameMatches(event))
                listener.propertyChange(event);
        }

        @Override
        public boolean equals(Object obj) {
            return listener.equals(obj);
        }

        @Override
        public int hashCode() {
            return listener.hashCode();
        }
    }

    private Properties doLoad() {
        somethingWasRead = false;
        return loadType.load(uris, loaders, this);
    }

    /** A source that was read, whatever it held: an empty file answered too. */
    void sourceAnswered(URI uri, Properties loaded) {
        somethingWasRead = true;
        reportedFailures.remove(uri);
        record(loaded.keySet(), Origin.of(uri));
    }

    /**
     * A source that was named and did not arrive.
     * <p>
     * <b>An absent one says nothing</b>, and that is not indulgence: {@link LoadType#FIRST} is a chain of
     * fallbacks in which every miss but the last is how the feature works, and a configuration with no
     * {@link Sources} probes four names for every interface. Warning there would be unbearable noise, and
     * noise is how a real warning stops being read.
     * </p>
     * <p>
     * <b>Anything else is a warning</b>, because nobody designs a fallback on a file that is there and
     * cannot be read: a wrong permission, a directory where a file was meant, a network source refusing.
     * Those produced a configuration full of defaults, and until now they produced it in silence.
     * </p>
     * <p>
     * Which of the two it was is <b>not</b> read off the exception, and that was the first thing a test
     * disproved here: {@link java.io.FileInputStream} throws {@link FileNotFoundException} for a file that
     * is missing, for a directory named where a file was meant, and for one it may not open — the three
     * things this rule exists to tell apart. For a file the filesystem is asked instead, and only a source
     * that is not a file at all falls back on the exception, where a
     * {@link FileNotFoundException} is a 404 or a missing entry in a jar.
     * </p>
     * <p>
     * Said once per source, and again only if the failure changes, since a reload runs the whole load
     * again and a hot reload runs it at its interval for as long as the process lives.
     * </p>
     */
    void sourceFailed(URI uri, IOException failure) {
        // a source that says it has to be there is the one case where absence is not a fallback but a
        // mistake, and the caller said so themselves: see SourceOptions#REQUIRED
        if (SourceOptions.isRequired(uri))
            throw unsupported(failure, "%s: the source %s says it is required and could not be read",
                    clazz.getName(), hideCredentials(uri));

        // ...and under owner.strict every source says it, except that a source which is merely absent is
        // still passed over: see #strict
        if (wasSimplyAbsent(uri, failure))
            return;

        if (strict)
            throw unsupported(failure, "%s: the source %s was named and could not be read, and %s is on. "
                            + "Read as it is written, the source is there and something else is wrong with "
                            + "it - a permission, a directory where a file was meant, a server refusing. "
                            + "Without %s this is a warning and the configuration goes on with its default "
                            + "values.",
                    clazz.getName(), hideCredentials(uri), STRICT, STRICT);

        String signature = failure.getClass().getName() + ": " + failure.getMessage();
        if (signature.equals(reportedFailures.get(uri)))
            return;
        reportedFailures.put(uri, signature);
        LOGGER.log(Level.WARNING, failure, () -> String.format(
                "%s: the source %s was named and could not be read. The configuration goes on with the "
                        + "other sources and its default values, so this is not an error - but a source "
                        + "that is merely absent is passed over in silence, and this one was not absent.",
                clazz.getName(), hideCredentials(uri)));
    }

    /**
     * Says what the keys of this configuration are, which is the question nothing else answers.
     * <p>
     * A wrong prefix is the most disorienting failure there is and the least visible: every property
     * vanishes at once, nothing errors, and the file is full of values that look right. The errors this
     * library raises already name the whole key — a missing mandatory property, a value that will not
     * convert — so what is left to cover is the case where nothing goes wrong and nothing is found.
     * </p>
     * <p>
     * Two lines of different weight. The prefix configured on the {@link Factory} goes at
     * <code>CONFIG</code> because it is written in no source file and moves the keys of every configuration
     * that factory creates. The key each method resolves to goes at <code>FINE</code>, being one line per
     * method, and is the complete answer: it walks the nested interfaces too, since a section's keys are
     * built from a path that appears nowhere on the method.
     * </p>
     */
    private void reportTheKeys() {
        String prefix = keyPrefix.describe();
        if (prefix != null)
            LOGGER.log(Level.CONFIG, () -> String.format("%s: every key is prefixed with %s",
                    clazz.getName(), prefix));

        if (!LOGGER.isLoggable(Level.FINE))
            return;
        reportTheKeysOf(clazz, keyPrefix);
        NestedProperties.forEachNested(clazz, keyPrefix, this::reportTheKeysOf);
    }

    private void reportTheKeysOf(Class<?> owner, KeyPrefix prefix) {
        for (Method method : owner.getMethods()) {
            if (isLibraryMethod(method) || Reflection.isDefault(method))
                continue;
            String key = PropertiesMapper.key(method, prefix);
            LOGGER.log(Level.FINE, () -> String.format("%s: %s.%s() %s", clazz.getName(),
                    owner.getSimpleName(), method.getName(), reads(method, key)));
        }
    }

    /** What the method does with the key it resolved to, which is not always "reads it". */
    private static String reads(Method method, String key) {
        if (NestedProperties.nests(method))
            return "is the section under '" + NestedProperties.pathOf(key) + "'";
        if (DisableableFeature.PREFIX.isDisabledFor(method))
            return "reads '" + key + "', with no prefix at all: it disables the feature";
        if (method.getParameterTypes().length > 0)
            return "reads '" + key + "', its arguments formatted in at each call";
        if (key.contains("${"))
            return "reads '" + key + "', before the variables in it are expanded";
        return "reads '" + key + "'";
    }

    /**
     * Whether the method is one of ours rather than one of the configuration's: those resolve to no key,
     * being answered by this very class.
     */
    private static boolean isLibraryMethod(Method method) {
        Class<?> owner = method.getDeclaringClass();
        return owner == Object.class || owner == Config.class || owner == Accessible.class
                || owner == Mutable.class || owner == Reloadable.class || owner == Traceable.class;
    }

    /** The interface this configuration was created from, for whoever has to name it in a message. */
    Class<? extends Config> configuredClass() {
        return clazz;
    }

    /** Whether the source was not there at all, as opposed to being there and refusing to be read. */
    private static boolean wasSimplyAbsent(URI uri, IOException failure) {
        File file = fileFromURI(uri);
        if (file != null)
            return !file.exists();
        return failure instanceof FileNotFoundException;
    }

    /**
     * The case a mistyped path produces: sources were asked for by name and not one of them could be read,
     * so what comes back is the default values and nothing else.
     * <p>
     * It is worth its own warning because the three above stay silent for exactly this: every one of those
     * misses is legitimate on its own, and only their sum is not. A configuration that declares no
     * {@link Sources} is left alone — probing four names and finding none is how a configuration made
     * entirely of defaults is written.
     * </p>
     */
    private void reportIfNothingAnswered() {
        if (!sourcesWereDeclared || somethingWasRead) {
            reportedEmptyLoad = false;
            return;
        }
        if (strict)
            throw unsupported("%s: not one of the sources it declares could be read %s, and %s is on. A "
                            + "path that is spelt wrong looks exactly like this. Without %s the "
                            + "configuration holds its default values and nothing else, and says so as a "
                            + "warning.",
                    clazz.getName(), specsOf(uris), STRICT, STRICT);

        if (reportedEmptyLoad)
            return;
        reportedEmptyLoad = true;
        LOGGER.log(Level.WARNING, () -> String.format(
                "%s: not one of the sources it declares could be read %s, so it holds its default values "
                        + "and nothing else. A path that is spelt wrong looks exactly like this.",
                clazz.getName(), specsOf(uris)));
    }

    /** Whether this configuration refuses what it would otherwise warn about. See {@link #strict}. */
    boolean isStrict() {
        return strict;
    }

    /** The handlers a <code>${$name::payload}</code> marker may name. See {@link #handlers}. */
    HandlersManager handlers() {
        return handlers;
    }

    /**
     * A value that refers to an encrypted property through a variable, which gets the <b>encrypted</b> text
     * and not the secret.
     * <pre>
     *     crypto.password = tzH7IKLCVc0AC72fh5DiZA==
     *     jdbc.url        = jdbc:h2:mem:test?password=${crypto.password}
     * </pre>
     * <p>
     * <code>password()</code> answers with the secret and <code>jdbcUrl()</code> answers with the cipher
     * text, so the same password reads two ways depending on how it is asked for, and nothing says so. The
     * connection then fails with a wrong password, or the cipher text travels somewhere a secret was meant
     * to go.
     * </p>
     * <p>
     * <b>It is where the annotation is written, not an oversight in the substitution.</b> The properties
     * hold the cipher text - they have to, or {@link #store} would write the file back decrypted -
     * and decryption happens per method, chosen by the {@link EncryptedValue} on it. A variable names a
     * <i>key</i>, so the substitution has nothing to read the decryptor from and inserts what it finds.
     * Issue #287 reports the same thing about a {@link Config.ConverterClass}, where it cannot be fixed at
     * all: a converter answers with a typed object, and there is no room for one inside a string.
     * </p>
     * <p>
     * The cure is a marker in the <i>value</i> rather than on the method, the shape SmallRye and Jasypt
     * both use, which makes decryption part of the expansion and therefore the same on every path. That is
     * what {@link org.aeonbits.owner.handlers.ValueHandler} is: written as
     * <code>crypto.password=${$aes-gcm::...}</code>, the reference above resolves to the secret and this
     * warning has nothing to report, because the property is no longer an <code>@EncryptedValue</code> one.
     * The warning stays for the configurations that keep the annotation, which is all of them until they
     * are rewritten.
     * </p>
     * <p>
     * The search is flat on purpose. With <code>a=${crypto.password}</code> and <code>jdbc.url=${a}</code>
     * only <code>a</code> matches, and that is enough: the point is that the reference exists, and naming
     * the value that holds it is more use than naming the one at the end of the chain. No value appears in
     * the message, encrypted or not - two key names do.
     * </p>
     */
    private void reportReferencesToEncryptedValues(Properties props) {
        if (encryptedKeyNames.isEmpty() || lookedForCipherReferences)
            return;
        lookedForCipherReferences = true;

        for (Enumeration<?> names = props.propertyNames(); names.hasMoreElements(); ) {
            String name = (String) names.nextElement();
            if (encryptedKeyNames.contains(name))
                continue;
            String referred = encryptedKeyReferredBy(props.getProperty(name));
            if (referred == null)
                continue;

            if (strict)
                throw unsupported("%s: the value of '%s' refers to '%s', which is declared "
                                + "@EncryptedValue, and %s is on. A variable is resolved against the "
                                + "properties, which hold the encrypted text, so '%s' would be built with "
                                + "the cipher text and not with the secret. Compose the value in Java from "
                                + "the method that decrypts it.",
                        clazz.getName(), name, referred, STRICT, name);

            LOGGER.log(Level.WARNING, () -> String.format(
                    "%s: the value of '%s' refers to '%s', which is declared @EncryptedValue. A variable is "
                            + "resolved against the properties, which hold the encrypted text, so '%s' is "
                            + "built with the cipher text and not with the secret - while the method reading "
                            + "'%s' answers with the secret. Compose the value in Java from the method that "
                            + "decrypts it.",
                    clazz.getName(), name, referred, name, referred));
        }
    }

    /**
     * Refuses a method that declares {@link EncryptedValue} and whose value is a
     * <code>${$name::payload}</code> marker, which are two ways of asking for the same thing that cannot
     * both happen.
     * <pre>
     *     &#064;EncryptedValue
     *     String password();          // db.password = ${$aes-gcm::...}
     * </pre>
     * <p>
     * The marker wins by construction, because expansion runs before {@link #decryptIfNecessary}: the value
     * reaching the decryptor is already the plain secret, and the decryptor is then asked to decrypt plain
     * text. What comes out is nonsense or an exception, and neither says which of the two declarations was
     * the one meant.
     * </p>
     * <p>
     * <b>Refused rather than warned about, and refused whether or not {@link #strict} is on</b>, which is
     * the treatment {@code @Mandatory} and {@link java.util.Optional} on one method already get: this is
     * not a configuration that works less well, it is one that says two contradictory things about a
     * password. Removing the annotation is what was meant - a marker carries the name of what decrypts it.
     * </p>
     * <p>
     * Only the keys of methods taking no arguments are checked, for the reason
     * {@link #encryptedKeyNames} exists at all: a key that depends on the invocation is not known here.
     * </p>
     */
    private void refuseEncryptedValuesHoldingAMarker(Properties props) {
        for (String encrypted : encryptedKeyNames) {
            String handler = markerOf(props.getProperty(encrypted));
            if (handler == null)
                continue;
            throw unsupported("%s: '%s' is declared @EncryptedValue and its value is the marker "
                            + "${$%s::...}, which are two ways of asking for the same thing. Expansion runs "
                            + "first, so the marker decrypts the value and the decryptor named by the "
                            + "annotation is then handed the plain secret to decrypt again. Keep one: a "
                            + "marker names what decrypts it, so with a marker the annotation has nothing "
                            + "left to say.",
                    clazz.getName(), encrypted, handler);
        }
    }

    /**
     * The name of the handler the given value hands itself to, when the whole value is a marker.
     * <p>
     * The whole value: a marker <i>inside</i> a larger value - <code>jdbc:h2:...?password=${$aes-gcm::x}
     * </code> - is not this case. There the property is a composed string that happens to contain a secret,
     * and it is not the one an <code>@EncryptedValue</code> decryptor could ever have decrypted either, so
     * there is no contradiction to refuse.
     * </p>
     *
     * @return the handler name, or <code>null</code> when the value is not a marker.
     */
    private static String markerOf(String value) {
        if (value == null)
            return null;
        String trimmed = value.trim();
        if (!trimmed.startsWith("${") || !trimmed.endsWith("}") || trimmed.indexOf("${", 2) != -1)
            return null;
        String expression = trimmed.substring(2, trimmed.length() - 1);
        return HandlersManager.isMarker(expression) ? HandlersManager.nameOf(expression) : null;
    }

    /**
     * The same property written twice, in two of the spellings {@link RelaxedKeys} accepts.
     * <pre>
     *     firstName  = a
     *     first-name = b
     * </pre>
     * <p>
     * One of the two is read and the other is never looked at again, which is precisely the failure this
     * library refuses to have: nothing is wrong, nothing errors, and half the file is inert. It is the
     * cost of the feature — relaxed binding is what makes two spellings collide in the first place — so it
     * is paid for here, where the pair can still be named.
     * </p>
     * <p>
     * <b>A {@link Config.DefaultValue} is not one of the two.</b> Every defaulted property is registered
     * under the key of its method and would otherwise pair with whatever the file wrote, which is not two
     * spellings of one thing but the ordinary business of a default being overridden. Only what was
     * written counts, which is the same distinction {@link #anythingWrittenUnder} draws.
     * </p>
     * <p>
     * A <code>WARNING</code> and not a line at <code>CONFIG</code>, because this is not a decision being
     * reported but a mistake being found: nobody spells one setting two ways on purpose in one
     * configuration. Under {@link #strict} it is a refusal, like every other warning that has a caller to
     * refuse.
     * </p>
     */
    private void reportAmbiguousKeys(Properties props) {
        if (relaxableKeys.isEmpty() || lookedForAmbiguousKeys)
            return;
        lookedForAmbiguousKeys = true;

        for (String key : relaxableKeys) {
            List<String> written = writtenSpellingsOf(props, key);
            if (written.size() < 2)
                continue;
            String winner = written.get(0);
            List<String> ignored = written.subList(1, written.size());

            if (strict)
                throw unsupported("%s: '%s' is written in %d spellings at once - %s - and %s is on. Relaxed "
                                + "binding reads '%s' and never looks at %s, so that part of the "
                                + "configuration is inert. Keep one spelling, or switch the feature off for "
                                + "the method with @DisableFeature(RELAXED_BINDING) if they are meant to be "
                                + "different properties.",
                        clazz.getName(), key, written.size(), written, STRICT, winner, ignored);

            LOGGER.log(Level.WARNING, () -> String.format(
                    "%s: '%s' is written in %d spellings at once - %s. Relaxed binding reads '%s' and never "
                            + "looks at %s. Keep one spelling, or switch the feature off for the method with "
                            + "@DisableFeature(RELAXED_BINDING) if they are meant to be different properties.",
                    clazz.getName(), key, written.size(), written, winner, ignored));
        }
    }

    /**
     * The spellings of the given key that somebody actually wrote, in the order relaxed binding tries
     * them, so that the first of them is the one that will be read.
     */
    private List<String> writtenSpellingsOf(Properties props, String key) {
        List<String> found = new ArrayList<>();
        if (props.getProperty(key) != null && wasWritten(key))
            found.add(key);
        for (String form : RelaxedKeys.alternativesTo(key))
            if (props.getProperty(form) != null && wasWritten(form))
                found.add(form);
        return found;
    }

    /**
     * The first encrypted key the given value refers to, or <code>null</code>. Both forms of a reference
     * count, the plain <code>${key}</code> and the one carrying a default, <code>${key:...}</code>.
     */
    private String encryptedKeyReferredBy(String value) {
        if (value == null)
            return null;
        for (String encrypted : encryptedKeyNames)
            if (value.contains("${" + encrypted + "}") || value.contains("${" + encrypted + ":"))
                return encrypted;
        return null;
    }

    private static String specsOf(List<URI> uris) {
        List<String> specs = new ArrayList<>();
        for (URI uri : uris)
            specs.add(hideCredentials(uri));
        return specs.toString();
    }

    private static void merge(Properties results, Map<?, ?>... inputs) {
        for (Map<?, ?> input : inputs)
            results.putAll(input);
    }

    /**
     * The value as it was written, variables unexpanded.
     * <p>
     * There is deliberately <b>no {@link Delegate}</b> on this method nor on the three below it, although
     * they implement {@link Accessible}: this is the method
     * {@link PropertiesInvocationHandler#lookupValue} reads every property with, so it has to stay raw,
     * and a Config object answers <code>getProperty</code> from the handler instead, which expands. See
     * {@link PropertiesInvocationHandler#readsValue}.
     * </p>
     */
    @Override
    public String getProperty(String key) {
        readLock.lock();
        try {
            return properties.getProperty(key);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * The value of a key, accepting the other spellings of it: <code>first-name</code>,
     * <code>first_name</code> and <code>FIRST_NAME</code> for a method that resolves to
     * <code>firstName</code>. See {@link RelaxedKeys}.
     * <p>
     * <b>A value that was written beats one that was only defaulted, whichever spelling holds it.</b> That
     * is the whole of the precedence rule and it is not the obvious one, "the key first and the
     * alternatives afterwards": a {@link Config.DefaultValue} is registered under the key of the method and
     * is a property like any other by the time this runs, so the plain rule would let the default of
     * <code>firstName</code> shadow the <code>first-name=…</code> somebody put in the file. Among values
     * that were all written, the key itself wins, and the alternatives are tried in the order
     * {@link RelaxedKeys#alternativesTo} states.
     * </p>
     * <p>
     * The cost of it is one lookup in {@link #origins} on the path where the key is found as written,
     * which is the path a configuration that agrees with its file always takes. The forms are built only
     * when that fails — for a property that is absent, or one that is holding its default.
     * </p>
     *
     * @param key the key the method resolved to.
     * @return the value, or <code>null</code> when no spelling of the key holds one.
     */
    String getRelaxedProperty(String key) {
        readLock.lock();
        try {
            String value = properties.getProperty(key);
            if (value != null && wasWritten(key))
                return value;
            for (String form : RelaxedKeys.alternativesTo(key)) {
                String written = properties.getProperty(form);
                if (written != null && wasWritten(form))
                    return written;
            }
            return value;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Whether the property under this key came from somewhere rather than from a {@link Config.DefaultValue}.
     * An origin that is missing counts as written, as it does in {@link #anythingWrittenUnder}: only a
     * default is ever recorded as not being one.
     */
    private boolean wasWritten(String key) {
        Origin origin = origins.get(key);
        return origin == null || origin.isWritten();
    }

    void syncReloadCheck() {
        if (hotReloadLogic != null && hotReloadLogic.isSync())
            hotReloadLogic.checkAndReload();
    }

    @Override
    public String getProperty(String key, String defaultValue) {
        readLock.lock();
        try {
            return properties.getProperty(key, defaultValue);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * On the manager the raw pair and the ordinary one are the same thing, the manager <i>being</i> the raw
     * layer. They part company one level up, in the handler, which expands the ordinary one.
     */
    @Override
    public String getRawProperty(String key) {
        return getProperty(key);
    }

    @Override
    public String getRawProperty(String key, String defaultValue) {
        return getProperty(key, defaultValue);
    }

    @Delegate
    @Override
    public void storeToXML(OutputStream os, String comment) throws IOException {
        readLock.lock();
        try {
            properties.storeToXML(os, comment);
        } finally {
            readLock.unlock();
        }
    }

    @Delegate
    @Override
    public Set<String> propertyNames() {
        readLock.lock();
        try {
            LinkedHashSet<String> result = new LinkedHashSet<>();
            for (Enumeration<?> propertyNames = properties.propertyNames(); propertyNames.hasMoreElements(); )
                result.add((String) propertyNames.nextElement());
            return result;
        } finally {
            readLock.unlock();
        }
    }

    @Delegate
    @Override
    public void list(PrintStream out) {
        readLock.lock();
        try {
            masked().list(out);
        } finally {
            readLock.unlock();
        }
    }

    @Delegate
    @Override
    public void list(PrintWriter out) {
        readLock.lock();
        try {
            masked().list(out);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Writes the configuration to a file, keeping the file that is already there. The rule and its
     * reasons are on {@link Accessible#save(File)}; this assembles the three things the writer needs —
     * the descriptions declared in the code, the keys the interface owns, and the values as they stand.
     */
    @Delegate
    @Override
    public void save(File file) throws IOException {
        Map<String, String> descriptions = new HashMap<>();
        Set<String> known = new HashSet<>();
        for (Method method : clazz.getMethods()) {
            String key = PropertiesMapper.key(method, keyPrefix);
            known.add(key);
            Config.Description description = method.getAnnotation(Config.Description.class);
            if (description != null)
                descriptions.put(key, description.value());
        }

        // the header describes the configuration and not one interface of it, so it is taken from wherever
        // in the hierarchy it is written, nearest first — a base interface that describes what the file is
        // for describes it for everything that extends it
        Config.Description onTheInterface = Annotations.findAnnotation(clazz, Config.Description.class);

        readLock.lock();
        try {
            // what the interface owns, and nothing else: a configuration that merges system:properties
            // holds hundreds of keys that have no business being written into somebody's file. Keys the
            // file already had are kept by the writer itself, whether we know them or not.
            Properties mine = new Properties();
            for (String key : properties.stringPropertyNames())
                if (known.contains(key))
                    mine.setProperty(key, properties.getProperty(key));

            new PropertiesFileWriter(descriptions, onTheInterface == null ? null : onTheInterface.value())
                    .write(file, mine, known);
        } finally {
            readLock.unlock();
        }
    }

    @Delegate
    @Override
    public void store(OutputStream out, String comments) throws IOException {
        readLock.lock();
        try {
            properties.store(out, comments);
        } finally {
            readLock.unlock();
        }
    }

    @Delegate
    @Override
    public void store(Writer out, String comments) throws IOException {
        readLock.lock();
        try {
            properties.store(out, comments);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void fill(Map map) {
        fill(map, UnaryOperator.<String>identity());
    }

    /**
     * Fills the given map with the properties, each value passed through the given transformation, which
     * is how a Config object fills a map with the values expanded. The transformation runs <b>under the
     * read lock</b>, so that a reload cannot change the properties halfway through the map being built.
     */
    @SuppressWarnings("unchecked")
    void fill(Map map, UnaryOperator<String> process) {
        readLock.lock();
        try {
            for (String propertyName : propertyNames())
                map.put(propertyName, process.apply(getProperty(propertyName)));
        } finally {
            readLock.unlock();
        }
    }

    @Delegate
    @Override
    public String setProperty(String key, String newValue) {
        writeLock.lock();
        try {
            String oldValue = properties.getProperty(key);
            try {
                if (Objects.equals(oldValue, newValue)) return oldValue;

                PropertyChangeEvent event = new PropertyChangeEvent(proxy, key, oldValue, newValue);
                fireBeforePropertyChange(event);
                String result = performSetProperty(key, newValue);
                if (newValue != null)
                    origins.put(key, Origin.ofRuntime());
                firePropertyChange(event);
                return result;
            } catch (RollbackException e) {
                return oldValue;
            }
        } finally {
            writeLock.unlock();
        }
    }

    private String performSetProperty(String key, Object value) {
        return (value == null) ?
                performRemoveProperty(key) :
                asString(properties.setProperty(key, asString(value)));
    }

    @Delegate
    @Override
    public String removeProperty(String key) {
        writeLock.lock();
        try {
            String oldValue = properties.getProperty(key);
            PropertyChangeEvent event = new PropertyChangeEvent(proxy, key, oldValue, null);
            fireBeforePropertyChange(event);
            String result = performRemoveProperty(key);
            firePropertyChange(event);
            return result;
        } catch (RollbackException e) {
            return properties.getProperty(key);
        } finally {
            writeLock.unlock();
        }
    }

    private String performRemoveProperty(String key) {
        origins.remove(key);
        return asString(properties.remove(key));
    }

    @Delegate
    @Override
    public void clear() {
        writeLock.lock();
        try {
            List<PropertyChangeEvent> events =
                    fireBeforePropertyChangeEvents(keys(properties), properties, new Properties());
            applyPropertyChangeEvents(events, null);
            firePropertyChangeEvents(events);
        } catch (RollbackBatchException e) {
            ignore();
        } finally {
            writeLock.unlock();
        }
    }

    @Delegate
    @Override
    public void load(InputStream inStream) throws IOException {
        load(loaded -> loaded.load(inStream));
    }

    private void performLoad(Set keys, Properties props) throws RollbackBatchException {
        List<PropertyChangeEvent> events = fireBeforePropertyChangeEvents(keys, properties, props);
        applyPropertyChangeEvents(events, Origin.ofRuntime());
        firePropertyChangeEvents(events);
    }

    @Delegate
    @Override
    public void load(Reader reader) throws IOException {
        load(loaded -> loaded.load(reader));
    }

    /**
     * What the two {@link Mutable} loads have in common, which is everything but the one line that reads the
     * bytes: the write lock is taken for the whole of it, the reading included, so that two loads cannot
     * interleave and leave the properties holding half of each.
     */
    private void load(PropertiesReader reader) throws IOException {
        writeLock.lock();
        try {
            Properties loaded = new Properties();
            reader.readInto(loaded);
            performLoad(keys(loaded), loaded);
        } catch (RollbackBatchException ex) {
            ignore();
        } finally {
            writeLock.unlock();
        }
    }

    /** Reads a stream or a reader into the given {@link Properties}. */
    private interface PropertiesReader {
        void readInto(Properties loaded) throws IOException;
    }

    void setProxy(Object proxy) {
        this.proxy = proxy;
    }

    @Delegate
    @Override
    public String toString() {
        readLock.lock();
        try {
            return masked().toString();
        } finally {
            readLock.unlock();
        }
    }

    boolean isLoading() {
        return loading;
    }

    private List<PropertyChangeEvent> fireBeforePropertyChangeEvents(
            Set keys, Properties oldValues, Properties newValues) throws RollbackBatchException {
        List<PropertyChangeEvent> events = new ArrayList<>();
        for (Object keyObject : keys) {
            String key = (String) keyObject;
            String oldValue = oldValues.getProperty(key);
            String newValue = newValues.getProperty(key);
            if (!Objects.equals(oldValue, newValue)) {
                PropertyChangeEvent event =
                        new PropertyChangeEvent(proxy, key, oldValue, newValue);
                try {
                    fireBeforePropertyChange(event);
                    events.add(event);
                } catch (RollbackOperationException e) {
                    ignore();
                }
            }
        }
        return events;
    }

    private void firePropertyChangeEvents(List<PropertyChangeEvent> events) {
        for (PropertyChangeEvent event : events)
            firePropertyChange(event);
    }

    private void fireBeforePropertyChange(PropertyChangeEvent event) throws RollbackBatchException,
            RollbackOperationException {
        for (PropertyChangeListener listener : propertyChangeListeners)
            if (listener instanceof TransactionalPropertyChangeListener)
                ((TransactionalPropertyChangeListener) listener).beforePropertyChange(event);
    }

    private void firePropertyChange(PropertyChangeEvent event) {
        for (PropertyChangeListener listener : propertyChangeListeners)
            listener.propertyChange(event);
    }

    @Delegate
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Proxy)) return false;
        InvocationHandler handler = Proxy.getInvocationHandler(obj);
        if (!(handler instanceof PropertiesInvocationHandler))
            return false;
        PropertiesInvocationHandler propsInvocationHandler = (PropertiesInvocationHandler) handler;
        // a nested object is a view of part of this same configuration, sharing this very manager: comparing
        // the properties would find them identical and report the whole as equal to one of its sections
        if (propsInvocationHandler.isNested())
            return false;
        PropertiesManager that = propsInvocationHandler.propertiesManager;
        return this.hasSamePropertiesAs(that);
    }

    /**
     * Not an {@code equals} overload: which of the two would be called depends on the static type of the
     * argument, and one of them is this class' real {@link #equals(Object)} contract.
     */
    private boolean hasSamePropertiesAs(PropertiesManager that) {
        if (!this.isAssignationCompatibleWith(that))
            return false;
        return sameValuesAs(that);
    }

    /** Whether the two hold the same properties, the interfaces they were built on not being considered. */
    boolean sameValuesAs(PropertiesManager that) {
        this.readLock.lock();
        try {
            that.readLock.lock();
            try {
                return this.properties.equals(that.properties);
            } finally {
                that.readLock.unlock();
            }
        } finally {
            this.readLock.unlock();
        }
    }

    private boolean isAssignationCompatibleWith(PropertiesManager that) {
        return this.clazz.isAssignableFrom(that.clazz) || that.clazz.isAssignableFrom(this.clazz);
    }

    @Delegate
    @Override
    public int hashCode() {
        readLock.lock();
        try {
            return properties.hashCode();
        } finally {
            readLock.unlock();
        }
    }

}
