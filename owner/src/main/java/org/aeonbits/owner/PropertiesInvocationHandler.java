/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.Config.Mandatory;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.reflect.Proxy.newProxyInstance;
import static org.aeonbits.owner.Config.DisableableFeature.PARAMETER_FORMATTING;
import static org.aeonbits.owner.Config.DisableableFeature.VARIABLE_EXPANSION;
import static org.aeonbits.owner.Converters.SpecialValue.NULL;
import static org.aeonbits.owner.Converters.convert;
import static org.aeonbits.owner.OptionalSupport.isOptional;
import static org.aeonbits.owner.OptionalSupport.valueClass;
import static org.aeonbits.owner.PreprocessorResolver.resolvePreprocessors;
import static org.aeonbits.owner.PropertiesMapper.defaultValueOnEmpty;
import static org.aeonbits.owner.PropertiesMapper.key;
import static org.aeonbits.owner.util.Util.unsupported;
import static org.aeonbits.owner.util.Reflection.invokeDefaultMethod;
import static org.aeonbits.owner.util.Reflection.isDefault;

/**
 * This {@link InvocationHandler} receives method calls from the delegate instantiated by {@link ConfigFactory} and maps
 * it to a property value from a property file, or a {@link Config.DefaultValue} specified in method annotation.
 * <p>
 * The {@link Config.Key} annotation can be used to override default mapping between method names and property names.
 * </p>
 * <p>
 * Automatic conversion is handled between the property value and the return type expected by the method of the
 * delegate.
 * </p>
 *
 * @author Luigi R. Viggiano
 */
class PropertiesInvocationHandler implements InvocationHandler, Serializable {

    private static final Logger LOGGER = Logger.getLogger(PropertiesInvocationHandler.class.getName());

    private static final long serialVersionUID = 5432212884255718342L;
    private transient List<DelegateMethodHandle> delegates;
    private final Object jmxSupport;
    private final StrSubstitutor substitutor;
    final PropertiesManager propertiesManager;

    /** The interface this object implements, which for a nested one is not the one the manager was built on. */
    private final Class<?> configClass;

    /** The prefix this object resolves its keys with: the factory's, or the path a nested object hangs from. */
    private final KeyPrefix keyPrefix;

    /** The interfaces from the root down to and including this one, kept to refuse a cycle. */
    private final List<Class<?>> ancestors;

    /**
     * The nested configuration objects, by accessor. Built once, so that the same accessor keeps answering
     * with the same object; transient because a {@link Method} cannot be serialized, and rebuilt on the way
     * back in exactly as the delegates are.
     */
    private transient Map<Method, Object> children;

    /**
     * The nested objects of the accessors that take arguments, by the path they resolved to. Those cannot be
     * built in advance — the key is not known until the call — so they are built on first use and kept, which
     * is what makes <code>server("alpha")</code> return the same object every time it is asked for.
     */
    private transient ConcurrentMap<String, Object> parameterized;

    /**
     * Whether the {@link Config.DefaultValue} of this object's methods are among the properties, which they
     * are for every path that exists before anything is read: the root, and the sections hanging from an
     * accessor that takes no arguments. A section that exists only because the properties put it there — an
     * element of a list, the answer of an accessor taking arguments — has a path nobody could register in
     * advance, and reads its defaults off the method instead. See {@link #lookupValue}.
     */
    private final boolean defaultsRegistered;

    PropertiesInvocationHandler(Class<?> configClass, PropertiesManager manager, Object jmxSupport) {
        this(configClass, manager, jmxSupport, manager.keyPrefix(), singletonChain(configClass), true);
    }

    private PropertiesInvocationHandler(Class<?> configClass, PropertiesManager manager, Object jmxSupport,
                                        KeyPrefix keyPrefix, List<Class<?>> ancestors,
                                        boolean defaultsRegistered) {
        this.configClass = configClass;
        this.propertiesManager = manager;
        this.jmxSupport = jmxSupport;
        this.keyPrefix = keyPrefix;
        this.ancestors = ancestors;
        this.defaultsRegistered = defaultsRegistered;
        delegates = findDelegates(manager, jmxSupport);
        this.substitutor = new StrSubstitutor(manager.load(), manager.isStrict());
        this.parameterized = new ConcurrentHashMap<>();
        this.children = NestedProperties.childrenOf(this, ancestors);
    }

    private static List<Class<?>> singletonChain(Class<?> configClass) {
        List<Class<?>> chain = new ArrayList<>();
        chain.add(configClass);
        return chain;
    }

    /**
     * Builds the object of a nested interface: the same properties, the same manager and therefore the same
     * reload and the same listeners, only a longer prefix.
     * <p>
     * It is deliberately <b>not</b> handed the JMX support, and its proxy does not implement
     * {@link javax.management.DynamicMBean}: the attributes an MBean exposes are the properties of the whole
     * configuration, which the parent already registers, and a second MBean over the same set would only
     * publish it twice. Nor is {@link PropertiesManager#setProxy} called for it — the source of a property
     * change event is the configuration object, and there is one of those.
     * </p>
     */
    Object nest(Class<?> type, String path, List<Class<?>> chain, boolean defaultsAreRegistered) {
        PropertiesInvocationHandler handler = new PropertiesInvocationHandler(type, propertiesManager, null,
                KeyPrefix.nestedIn(path), chain, defaultsRegistered && defaultsAreRegistered);
        return newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    /**
     * The nested object at a path that was not known when the configuration was created — an element of a
     * list, or the answer of an accessor taking arguments — built on first use and kept, so that the same
     * path is always the same object.
     */
    Object nestedAt(Class<?> type, String path, List<Class<?>> chain) {
        return parameterized.computeIfAbsent(type.getName() + ' ' + path,
                ignored -> nest(type, path, chain, false));
    }

    /** The interface this object implements. */
    Class<?> configClass() {
        return configClass;
    }

    /** The key the given method resolves to, arguments included; the nesting path is already part of it. */
    String keyOf(Method method, Object... args) {
        return expandKey(method, args);
    }

    /** Tells whether this is a nested configuration object rather than one a {@link Factory} created. */
    boolean isNested() {
        return keyPrefix.isNested();
    }

    @Override
    public Object invoke(Object proxy, Method invokedMethod, Object... args) throws Throwable {
        propertiesManager.syncReloadCheck();

        if (isDefault(invokedMethod))
            return invokeDefaultMethod(proxy, invokedMethod, args);

        // a nested object answers these itself: the delegates below belong to the manager, which is shared
        // with the whole tree and would report two different views of it as the same object
        if (isNested() && isIdentity(invokedMethod))
            return identity(proxy, invokedMethod, args);

        // before the delegates: these are Accessible methods the manager also implements, and the
        // manager's implementations are the raw ones - see readsValue
        if (readsValue(invokedMethod))
            return readValue(invokedMethod, args);

        DelegateMethodHandle delegate = getDelegateMethod(invokedMethod);
        if (delegate != null)
            return delegate.invoke(args);

        return resolveProperty(invokedMethod, args);
    }

    /**
     * The {@link Accessible} methods that answer with a <b>value</b> rather than writing the properties
     * out, and which therefore expand the variables as a mapping method does.
     * <p>
     * They are answered here and not through a {@link Delegate} on the {@link PropertiesManager}, and that
     * is not an arrangement but a necessity: <code>PropertiesManager.getProperty</code> is the very method
     * {@link #lookupValue} reads a property with, so an expansion added to it would run twice on every
     * mapping method and would stop the defaults being found, since those are registered under the
     * unexpanded key. The manager is the raw layer, and it has to stay one.
     * </p>
     */
    private static boolean readsValue(Method method) {
        if (!Accessible.class.isAssignableFrom(method.getDeclaringClass()))
            return false;
        Class<?>[] parameters = method.getParameterTypes();
        if ("fill".equals(method.getName()))
            return parameters.length == 1 && parameters[0] == Map.class;
        if ("getProperty".equals(method.getName()) || "getRawProperty".equals(method.getName()))
            return parameters.length >= 1 && parameters[0] == String.class;
        return false;
    }

    /**
     * Answers one of the methods {@link #readsValue} recognises.
     * <p>
     * The expansion is skipped for the <code>getRaw*</code> pair, which exists to skip it, and when the
     * configuration interface carries <code>@DisableFeature(VARIABLE_EXPANSION)</code>: that annotation is
     * read off {@link #configClass} rather than off the invoked method, because these methods are declared
     * on {@link Accessible} and never on the interface the user wrote.
     * </p>
     */
    @SuppressWarnings("unchecked")
    private Object readValue(Method method, Object... args) {
        boolean raw = method.getName().startsWith("getRaw")
                || VARIABLE_EXPANSION.isDisabledFor(configClass);

        if ("fill".equals(method.getName())) {
            Map<Object, Object> map = (Map<Object, Object>) args[0];
            if (raw)
                propertiesManager.fill(map);
            else
                propertiesManager.fill(map, this::expand);
            return null;
        }

        // always read raw and expand here: the manager is the raw layer, and which of the two the caller
        // asked for is this object's decision, not its
        String key = (String) args[0];
        String value = (args.length > 1)
                ? propertiesManager.getRawProperty(key, (String) args[1])
                : propertiesManager.getRawProperty(key);
        return raw ? value : expand(value);
    }

    /** Expands the variables of a value read by key, where there is no method whose annotations to read. */
    private String expand(String value) {
        return substitutor.replace(value);
    }

    private static boolean isIdentity(Method method) {
        if (method.getParameterTypes().length == 0)
            return "hashCode".equals(method.getName());
        return "equals".equals(method.getName())
                && method.getParameterTypes().length == 1
                && method.getParameterTypes()[0] == Object.class;
    }

    /**
     * {@code equals} and {@code hashCode} of a nested object.
     * <p>
     * Two of them are the same when they are the same view — the same interface hanging from the same path —
     * over the same properties. Without this they would both fall through to the manager, which is one object
     * for the whole tree: <code>config.server()</code> and <code>config.database()</code> would compare equal
     * to each other and to the configuration holding them, all three being backed by the same properties.
     * </p>
     */
    private Object identity(Object proxy, Method method, Object... args) {
        if ("hashCode".equals(method.getName()))
            return keyPrefix.path().hashCode() * 31 + propertiesManager.hashCode();
        Object other = args[0];
        if (proxy == other) return true;
        if (!(other instanceof Proxy)) return false;
        InvocationHandler handler = Proxy.getInvocationHandler(other);
        if (!(handler instanceof PropertiesInvocationHandler)) return false;
        PropertiesInvocationHandler that = (PropertiesInvocationHandler) handler;
        return that.isNested()
                && this.keyPrefix.path().equals(that.keyPrefix.path())
                && compatible(this.configClass, that.configClass)
                && this.propertiesManager.sameValuesAs(that.propertiesManager);
    }

    private static boolean compatible(Class<?> one, Class<?> other) {
        return one.isAssignableFrom(other) || other.isAssignableFrom(one);
    }

    private DelegateMethodHandle getDelegateMethod(Method invokedMethod) {
        for (DelegateMethodHandle delegate : delegates)
            if (delegate.matches(invokedMethod))
                return delegate;
        return null;
    }

    private Object resolveProperty(Method method, Object... args) {
        String key = expandKey(method, args);

        if (NestedProperties.nests(method))
            return nested(method, key);

        if (PropertiesAggregator.aggregates(method))
            return NestedProperties.nestsValues(method)
                    ? PropertiesAggregator.aggregateSections(method, key, this, ancestors)
                    : PropertiesAggregator.aggregate(method, key, propertiesManager,
                            entry -> propertiesManager.decryptIfNecessary(method,
                                    expandVariables(method, preProcess(method, entry))));

        boolean optional = isOptional(method);

        // a list written one element per key, servers[0] and servers[1] — or one section per key,
        // servers[0].host, when the elements are configuration objects of their own. Null when there are
        // none, and the ordinary lookup below then reads whatever single value the plain key holds
        if (IndexedProperties.readsAList(method)) {
            Object indexed = NestedProperties.nestsElements(method)
                    ? NestedProperties.list(method, key, this, ancestors)
                    : IndexedProperties.collect(method, key, propertiesManager,
                            element -> propertiesManager.decryptIfNecessary(method,
                                    expandVariables(method, preProcess(method, element))));
            if (indexed != null)
                return optional ? Optional.of(indexed) : indexed;
        }

        String value = lookupValue(method, key);
        if (value == null) {
            if (isMandatory(method))
                throw new MissingMandatoryPropertyException(key);
            return optional ? Optional.empty() : null;
        }
        String text = process(method, value, args);

        // an empty value is a value like any other, unless the method explicitly asked for the default to
        // cover it too: see Config.DefaultValue#useOnEmpty
        if (isEmpty(text)) {
            String onEmpty = defaultValueOnEmpty(method);
            if (onEmpty != null)
                text = process(method, onEmpty, args);
        }

        Object result = convert(method, valueClass(method), text, key);
        if (result == NULL) return optional ? Optional.empty() : null;
        return optional ? Optional.of(result) : result;
    }

    /**
     * The nested configuration object an accessor reads.
     * <p>
     * The one built when the configuration was created is the answer, unless the key has moved since — which
     * only a variable inside it can do — in which case the object for the key as it reads now is built and
     * kept, alongside the ones of the accessors that take arguments.
     * </p>
     * <p>
     * An {@link Optional} nested object is <b>present when there is anything at all below its path</b>. That
     * is the only reading of it worth having: the object itself can always be built, since a nested interface
     * is a view and not a value, so an Optional that was always present would say nothing. Empty therefore
     * means "this section of the configuration was not written", which is the question being asked.
     * </p>
     * <p>
     * <b>A {@link Config.DefaultValue} inside the nested interface counts as something below the path</b>,
     * and makes the section permanently present. That is not a compromise but the only answer available: the
     * defaults are merged into the same properties as the values read from the sources, and afterwards
     * nothing distinguishes them. An Optional section and a default written inside it say the opposite of
     * each other, and the default wins — SmallRye, whose defaults are a source like ours, decided the same
     * way in its version 3 and called the two "opposing concepts"; the behaviour it dropped in doing so is
     * the one this would otherwise have. Should a value ever remember which source it came from, which is
     * what issue #277 asks for, the question can be answered properly and this rule is where to change it.
     * </p>
     */
    private Object nested(Method method, String key) {
        String path = NestedProperties.pathOf(key);
        Object child = children.get(method);
        if (child == null || !path.equals(pathOf(child)))
            child = NestedProperties.create(method, key, this, ancestors);

        if (!isOptional(method))
            return child;
        return NestedProperties.anythingUnder(path, propertiesManager) ? Optional.of(child) : Optional.empty();
    }

    /** The path a nested object hangs from, read back off its own handler. */
    private static String pathOf(Object child) {
        return handlerOf(child).keyPrefix.path();
    }

    private static PropertiesInvocationHandler handlerOf(Object child) {
        return (PropertiesInvocationHandler) Proxy.getInvocationHandler(child);
    }

    /**
     * Applies to a raw value everything that happens between reading it from the properties and converting it
     * to the return type: the preprocessors, the variable expansion, the decryption and the formatting of the
     * arguments. Running the default value through the very same steps is what makes it indistinguishable from
     * a property that was not there, since a missing property is already resolved to its registered default.
     */
    private String process(Method method, String value, Object... args) {
        return format(method,
                propertiesManager.decryptIfNecessary(method,
                        expandVariables(method, preProcess(method, value))),
                args);
    }

    private static boolean isEmpty(String value) {
        return value != null && value.trim().isEmpty();
    }

    private String lookupValue(Method method, String key) {
        String value = propertiesManager.getProperty(key);

        // TODO: this if should go away! See #84 and #86
        if (value == null && !VARIABLE_EXPANSION.isDisabledFor(method)) {
            String unexpandedKey = key(method, keyPrefix);
            value = propertiesManager.getProperty(unexpandedKey);
        }
        // a section whose path nobody could know in advance has no default among the properties: the
        // annotation is read off the method instead, so that an element of a list defaults like everything
        // else. Only such a section does this - elsewhere the default is a property, and one that was
        // removed on purpose has to stay removed
        if (value == null && !defaultsRegistered)
            value = PropertiesMapper.defaultValue(method);
        return value;
    }

    /**
     * A method returning an {@link Optional} states that the property may be absent, so a {@link Mandatory}
     * inherited from the interface leaves it alone: annotating a whole interface is a way of saying "these
     * are all required", and the one method that says otherwise is the exception being made. The two written
     * on the <b>same</b> method contradict each other instead, and are rejected when the Config object is
     * created: see {@link #rejectMandatoryOptional(Method)}.
     */
    private static boolean isMandatory(Method method) {
        if (isOptional(method))
            return false;
        return method.getAnnotation(Mandatory.class) != null
                || method.getDeclaringClass().getAnnotation(Mandatory.class) != null;
    }

    /**
     * {@link Mandatory} written on an accessor that reads a nested object cannot mean anything, so it is
     * refused when the configuration is created rather than accepted and never enforced.
     * <p>
     * A section is there as soon as any key below it is, and a {@link Config.DefaultValue} on any method of
     * the nested interface puts one there: defaults are merged into the same properties as everything else,
     * and afterwards nothing tells them apart. The check would therefore pass for a section nobody ever
     * wrote, which is worse than no check at all, because it reads like a guarantee. SmallRye arrived at the
     * same place from the other side — it calls an <code>Optional</code> and a default "opposing concepts" —
     * and Spring Boot 3.2, which began building every absent nested object, broke the validation of everyone
     * who relied on the absence.
     * </p>
     * <p>
     * On the <b>interface</b> the annotation keeps its meaning, "these are all required", and leaves the
     * nested accessors alone exactly as it leaves an {@link Optional} one alone.
     * </p>
     */
    private static void rejectMandatoryNested(Method method) {
        if (NestedProperties.nests(method) && method.getAnnotation(Mandatory.class) != null)
            throw unsupported("Method '%s' is annotated with @Mandatory and reads a nested configuration "
                            + "object, which counts as present as soon as any property below it is - a "
                            + "@DefaultValue written inside '%s' is enough on its own. The check could never "
                            + "fail, so it is refused rather than left there looking like one: write "
                            + "@Mandatory on the properties inside that are really required.",
                    method.getName(), valueClass(method).getSimpleName());
    }

    private static void rejectMandatoryOptional(Method method) {
        if (isOptional(method) && method.getAnnotation(Mandatory.class) != null)
            throw unsupported("Method '%s' is annotated with @Mandatory and returns an Optional: the two say "
                            + "the opposite of each other. Drop the @Mandatory to let the property be absent, "
                            + "or drop the Optional to require it. A @Mandatory written on the interface does "
                            + "not need removing: it leaves an Optional method alone.",
                    method.getName());
    }

    /**
     * Verifies that every mandatory property of the configuration can be resolved, the nested objects
     * included, collecting all the missing ones in a single {@link MissingMandatoryPropertyException}.
     * Methods taking parameters are skipped, since their key may depend on the invocation arguments: they are
     * checked on access instead.
     */
    void validateMandatoryProperties() {
        NestedProperties.rejectKeyedSections(configClass);
        List<String> missingKeys = new LinkedList<>();
        collectMissingMandatory(missingKeys);
        if (!missingKeys.isEmpty())
            throw new MissingMandatoryPropertyException(missingKeys);
    }

    private void collectMissingMandatory(List<String> missingKeys) {
        for (Method method : configClass.getMethods()) {
            rejectMandatoryOptional(method);
            rejectMandatoryNested(method);
            if (isDefault(method)) continue;
            if (getDelegateMethod(method) != null) continue;
            if (NestedProperties.nests(method)) {
                collectMissingNested(method, missingKeys);
                continue;
            }
            if (!isMandatory(method)) continue;
            if (method.getParameterTypes().length > 0) continue;
            String key = expandKey(method);
            if (lookupValue(method, key) == null)
                missingKeys.add(key);
        }
    }

    /**
     * The mandatory properties of a nested object, checked when the configuration is created like everybody
     * else's — which is the whole point of building the objects then rather than at the first call.
     * <p>
     * An {@link Optional} section that is not there is the absence the Optional describes, so nothing inside
     * it is missing. Anything else is descended into, and a section that was not written at all is reported
     * through the mandatory properties it does not have — named one by one, instead of leaving the reader to
     * work out which section went missing.
     * </p>
     */
    private void collectMissingNested(Method method, List<String> missingKeys) {
        if (method.getParameterTypes().length > 0) return;
        Object child = children.get(method);
        if (child == null) return;

        String path = NestedProperties.pathOf(expandKey(method));
        if (isOptional(method) && !NestedProperties.anythingUnder(path, propertiesManager)) return;
        handlerOf(child).collectMissingMandatory(missingKeys);
    }

    private String preProcess(Method method, String value) {
        List<Preprocessor> preprocessors = resolvePreprocessors(method);
        String result = value;
        for (Preprocessor preprocessor : preprocessors)
            result = preprocessor.process(result);
        return result;
    }

    private String expandKey(Method method, Object... args) {
        String key = key(method, keyPrefix);
        if (VARIABLE_EXPANSION.isDisabledFor(method))
            return key;
        return substitutor.replace(key, args);
    }

    private String format(Method method, String format, Object... args) {
        if (PARAMETER_FORMATTING.isDisabledFor(method))
            return format;

        // If there are no arguments to format, we can just return.
        // This is also helpful when the {@code format} is a property value that contains a '%' character,
        // such as '@#$%^&*()" (e.g., a clear-text password). In such cases, the '%' character is not
        // a placeholder in a format string -- its just a random character in the property value.
        if ( args == null || args.length == 0 )
            return format;

        try {
            // Do this to achieve property expansion
            return String.format(format, args);
            }
        catch ( Exception notAFormat ) {
            // There's no guarantee that a property value from a config file
            // is a legal format string. When formatting doesn't work, let's
            // just return the original property value.
            reportNotAFormat(method, notAFormat);
            return format;
            }
    }

    /**
     * A value that was used as a format and is not one.
     * <p>
     * Returning it as it was written is the right answer and is documented: a method taking arguments makes
     * its value a template, and a value has no obligation to be one — a password holding a <code>%</code>
     * is the ordinary case, not a mistake. But the same silence covers a real mistake, a placeholder
     * mistyped in a value that <b>was</b> meant as a format, and then the method quietly answers with the
     * template instead of the text. Hence <code>FINE</code>: below the level anyone runs at, and there for
     * whoever is looking.
     * </p>
     * <p>
     * <b>Neither the value nor the exception message appears in the line.</b> The message of a formatting
     * failure quotes the part of the format it choked on, which is a piece of the value, and a value never
     * reaches a log — that is what {@link Config.Sensitive} exists for. The key and the name of the failure
     * are enough to find it.
     * </p>
     */
    private void reportNotAFormat(Method method, Exception notAFormat) {
        if (!LOGGER.isLoggable(Level.FINE))
            return;
        String key = key(method, keyPrefix);
        LOGGER.log(Level.FINE, () -> String.format(
                "%s() takes arguments, so the value of '%s' was used as a format, and it is not one (%s). "
                        + "It is returned as it was written. A value that was never meant as a format needs "
                        + "nothing done about this; one that was has a placeholder in it that is not.",
                method.getName(), key, notAFormat.getClass().getSimpleName()));
    }

    private String expandVariables(Method method, String value) {
        if (VARIABLE_EXPANSION.isDisabledFor(method))
            return value;
        return expand(value);
    }

    private List<DelegateMethodHandle> findDelegates(Object... targets) {
        List<DelegateMethodHandle> result = new LinkedList<>();
        for (Object target : targets) {
            if (target == null)
                continue;
            Method[] methods = target.getClass().getMethods();
            for (Method m : methods)
                if (m.getAnnotation(Delegate.class) != null)
                    result.add(new DelegateMethodHandle(target, m));
        }
        return result;
    }

    public <T extends Config> void setProxy(T proxy) {
        propertiesManager.setProxy(proxy);
    }

    private void readObject(java.io.ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        delegates = findDelegates(propertiesManager, jmxSupport);
        parameterized = new ConcurrentHashMap<>();
        children = NestedProperties.childrenOf(this, ancestors);
    }
}
