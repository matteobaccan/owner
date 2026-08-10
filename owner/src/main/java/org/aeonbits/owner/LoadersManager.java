/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.loaders.DotEnvLoader;
import org.aeonbits.owner.loaders.Loader;
import org.aeonbits.owner.loaders.PropertiesLoader;
import org.aeonbits.owner.loaders.SystemLoader;
import org.aeonbits.owner.loaders.XMLLoader;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.aeonbits.owner.util.Util.unsupported;


/**
 * This class is responsible of locating an appropriate Loader for a given URL (based the extension in the resource
 * name)and load the properties from it.
 * <p>
 * It keeps <b>two orderings</b> over the same loaders, and they are not the same one. See
 * {@link #findLoader(URI)} and {@link #defaultSpecs(String)}.
 * </p>
 *
 * @author Luigi R. Viggiano
 * @since 1.0.5
 */
class LoadersManager implements Serializable {

    private static final long serialVersionUID = 7118416659372421551L;

    private static final Logger LOGGER = Logger.getLogger(LoadersManager.class.getName());

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final List<Loader> loaders = new LinkedList<>();
    /** The subset of {@link #loaders} that was found on the classpath rather than built in or registered. */
    private final List<Loader> discovered = new LinkedList<>();

    LoadersManager() {
        this(discover());
    }

    /**
     * Kept apart from the discovery itself so that the orderings can be tested without a classpath that has
     * to be arranged around them.
     *
     * @param found the loaders to treat as discovered, in the order they were found.
     */
    LoadersManager(Iterable<Loader> found) {
        // registration pushes to the front, so PropertiesLoader must come first: it accepts every URL it can
        // resolve and would otherwise answer for the formats registered after it
        registerLoader(new PropertiesLoader());
        registerLoader(new XMLLoader());
        registerLoader(new DotEnvLoader());
        registerLoader(new SystemLoader());
        registerDiscovered(found);
    }

    /**
     * Registers what was found on the classpath, ahead of the built-in loaders so that a loader shipped for a
     * format can answer for it before {@link PropertiesLoader} does.
     * <p>
     * Registration pushes to the front, so the list is walked backwards: what {@link java.util.ServiceLoader}
     * handed over first ends up being consulted first, rather than last.
     * </p>
     */
    private void registerDiscovered(Iterable<Loader> found) {
        List<Loader> inOrder = new ArrayList<>();
        for (Loader loader : found)
            inOrder.add(loader);
        for (int i = inOrder.size() - 1; i >= 0; i--)
            registerLoader(inOrder.get(i));
        discovered.addAll(inOrder);
        // said even when nothing was found: that is precisely the case somebody turns this on to look into,
        // and a loader that was not discovered leaves no other trace - its files are picked up by
        // PropertiesLoader, which accepts anything it can resolve, and read as properties without complaint
        LOGGER.log(Level.CONFIG, () -> String.format("Loaders discovered on the classpath: %s",
                inOrder.isEmpty() ? "none" : names(inOrder)));
    }

    private static String names(List<Loader> found) {
        StringBuilder text = new StringBuilder();
        for (Loader loader : found) {
            if (text.length() > 0) text.append(", ");
            text.append(loader.getClass().getName());
        }
        return text.toString();
    }

    /**
     * Finds the loaders declared in <code>META-INF/services/org.aeonbits.owner.loaders.Loader</code>.
     * <p>
     * The context class loader is asked first and the one that loaded OWNER is the fallback, which is what
     * Spring's <code>ClassUtils.getDefaultClassLoader</code> does and what SmallRye and MicroProfile Config
     * default to. It is right more often than the alternative and it is not right always - see
     * {@link Loader} for when it is not, and for the way out, which is to register the loader explicitly.
     * </p>
     * <p>
     * A <code>ServiceConfigurationError</code> - a service file naming a class that is absent or cannot be
     * instantiated - is left to propagate. It means a broken artifact on the classpath, and this library
     * spent 2026-08-09 removing the habit of carrying on quietly from things like it.
     * </p>
     */
    private static Iterable<Loader> discover() {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        return ServiceLoader.load(Loader.class, context != null ? context : LoadersManager.class.getClassLoader());
    }

    void load(Properties result, URI uri) throws IOException {
        Loader loader = findLoader(uri);
        loader.load(result, uri);
    }

    /**
     * The first ordering: <b>most recently registered first</b>, which puts an explicitly registered loader
     * ahead of a discovered one, a discovered one ahead of the built-in loaders, and {@link PropertiesLoader}
     * - which accepts every URL it can resolve - last of all.
     */
    Loader findLoader(URI uri) {
        lock.readLock().lock();
        try {
            for (Loader loader : loaders)
                if (loader.accept(uri))
                    return loader;
            throw unsupported("Can't resolve a Loader for the URL %s.", uri.toString());
        } finally {
            lock.readLock().unlock();
        }
    }

    final void registerLoader(Loader loader) {
        if (loader == null)
            throw new IllegalArgumentException("loader can't be null");
        lock.writeLock().lock();
        try {
            loaders.add(0, loader);
        } finally {
            lock.writeLock().unlock();
        }
    }

    void clear() {
        lock.writeLock().lock();
        try {
            loaders.clear();
            discovered.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * The second ordering, and deliberately not the first one: <b>a discovered loader comes last</b>.
     * <p>
     * These are the sources looked for when an interface carries no {@link Config.Sources}, and under
     * {@link Config.LoadType#FIRST} the first one that resolves is the one that answers, while
     * {@link Config.LoadType#MERGE} lets the earlier ones overwrite the later. Ordered like
     * {@link #findLoader(URI)}, a jar appearing on the classpath would place its spec in front and a
     * forgotten <code>MyConfig.yaml</code> would silently start beating a working
     * <code>MyConfig.properties</code>. Placed last, a discovered loader can only answer for a name nothing
     * else claimed.
     * </p>
     * <p>
     * That fixes precedence and not cost: {@link PropertiesManager} resolves every spec to a URI before any
     * of them is loaded, so each loader with a spec of its own is a classpath lookup on every configuration
     * created without {@code @Sources}. An interface that names its sources pays none of it.
     * </p>
     * <p>
     * Duplicates are dropped, keeping the first occurrence: the same loader registered by hand and also
     * found on the classpath is two instances contributing one name.
     * </p>
     */
    String[] defaultSpecs(String prefix) {
        lock.readLock().lock();
        try {
            Collection<String> defaultSpecs = new LinkedHashSet<>(loaders.size());
            addSpecs(defaultSpecs, prefix, false);
            addSpecs(defaultSpecs, prefix, true);
            return defaultSpecs.toArray(new String[0]);
        } finally {
            lock.readLock().unlock();
        }
    }

    private void addSpecs(Collection<String> specs, String prefix, boolean fromClasspath) {
        for (Loader loader : loaders) {
            if (isDiscovered(loader) != fromClasspath) continue;
            String spec = loader.defaultSpecFor(prefix);
            if (spec != null)
                specs.add(spec);
        }
    }

    /** By identity: a loader is free to define equality, and two instances of one class are two loaders. */
    private boolean isDiscovered(Loader loader) {
        for (Loader candidate : discovered)
            if (candidate == loader)
                return true;
        return false;
    }

}
