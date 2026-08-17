/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.loaders.DotEnvLoader;
import org.aeonbits.owner.loaders.IniLoader;
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

import static org.aeonbits.owner.util.Util.hideCredentials;
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
    /**
     * The subset of {@link #loaders} this library ships. Kept apart from the ones somebody registered
     * because {@link #defaultSpecs(String)} treats the two differently: a loader registered by hand is an
     * instruction and comes first, while these are a convention and are ordered by what a configuration
     * without {@link Config.Sources} should read first.
     */
    private final List<Loader> builtIn = new LinkedList<>();

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
        registerLoader(new IniLoader());
        registerLoader(new SystemLoader());
        builtIn.addAll(loaders);
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
        // which loader answered is a whole diagnosis in one line: a '.yaml' answered by PropertiesLoader is
        // a file being read as properties, which produces no error and nearly no properties
        LOGGER.log(Level.CONFIG, () -> String.format("Reading %s with %s",
                hideCredentials(uri), loader.getClass().getSimpleName()));
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
            builtIn.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * The second ordering, and it is <b>the first one reversed</b>: the built-in loaders in the order they
     * were registered - so <code>MyConfig.properties</code>, then <code>.xml</code>, then <code>.ini</code>
     * and <code>.cfg</code> - and a discovered loader last of all.
     * <p>
     * These are the sources looked for when an interface carries no {@link Config.Sources}, and under
     * {@link Config.LoadType#FIRST} the first one that resolves is the one that answers, while
     * {@link Config.LoadType#MERGE} lets the earlier ones overwrite the later. So this order decides which
     * file wins when two of them exist, which is a different question from the one {@link #findLoader(URI)}
     * answers, and it wants the opposite answer.
     * </p>
     * <p>
     * <b>Being able to read anything is a liability there and an asset here.</b> Asked <i>can you read this
     * URI</i>, {@link PropertiesLoader} must be last: it accepts every URL it can resolve and would answer
     * for the formats registered after it. Asked <i>which file is this configuration's own</i>, it must be
     * first: <code>MyConfig.properties</code> is the convention this library was built on and the one an
     * application has been reading for years, and it cannot be displaced by a <code>MyConfig.cfg</code> that
     * somebody else's tool left in the directory - <code>.cfg</code> being the most generic of the four
     * names and the easiest to collide with. Until 2.0.0 the two orders were the same list, which is to say
     * this one was never chosen: a <code>.ini</code> or a <code>.cfg</code> silently outranked a
     * <code>.properties</code>.
     * </p>
     * <p>
     * A discovered loader stays last under either reading. Ordered like {@link #findLoader(URI)}, a jar
     * appearing on the classpath would place its spec in front and a forgotten <code>MyConfig.yaml</code>
     * would silently start beating a working <code>MyConfig.properties</code>; last, it can only answer for
     * a name nothing else claimed. What a configuration wants read first, it can now also say - see
     * {@link Config.Sources} and <code>owner:default</code>.
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
            addRegisteredSpecs(defaultSpecs, prefix);
            addBuiltInSpecs(defaultSpecs, prefix);
            addDiscoveredSpecs(defaultSpecs, prefix);
            return defaultSpecs.toArray(new String[0]);
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Most recently registered first, as {@link #findLoader(URI)} asks them: this one is an instruction. */
    private void addRegisteredSpecs(Collection<String> specs, String prefix) {
        for (Loader loader : loaders)
            if (!isDiscovered(loader) && !isBuiltIn(loader))
                addSpecsOf(loader, specs, prefix);
    }

    /** Backwards, since registration pushes to the front: the list walked this way is registration order. */
    private void addBuiltInSpecs(Collection<String> specs, String prefix) {
        List<Loader> registered = new ArrayList<>(loaders);
        for (int i = registered.size() - 1; i >= 0; i--) {
            Loader loader = registered.get(i);
            if (isBuiltIn(loader))
                addSpecsOf(loader, specs, prefix);
        }
    }

    /** Forwards, which for these is the order {@link java.util.ServiceLoader} handed them over in. */
    private void addDiscoveredSpecs(Collection<String> specs, String prefix) {
        for (Loader loader : loaders)
            if (isDiscovered(loader))
                addSpecsOf(loader, specs, prefix);
    }

    private void addSpecsOf(Loader loader, Collection<String> specs, String prefix) {
        String[] offered = loader.defaultSpecsFor(prefix);
        if (offered == null) return;
        for (String spec : offered) {
            // a null among them is a mistake in the loader, not a way of saying "none" - that is an
            // empty array - and only the author of the loader can put it right, so it is named
            if (spec == null)
                throw unsupported("%s returned a null among its default specifications for '%s'",
                        loader.getClass().getName(), prefix);
            specs.add(spec);
        }
    }

    /** By identity, for the same reason {@link #isDiscovered(Loader)} is. */
    private boolean isBuiltIn(Loader loader) {
        for (Loader each : builtIn)
            if (each == loader)
                return true;
        return false;
    }

    /** By identity: a loader is free to define equality, and two instances of one class are two loaders. */
    private boolean isDiscovered(Loader loader) {
        for (Loader candidate : discovered)
            if (candidate == loader)
                return true;
        return false;
    }

}
