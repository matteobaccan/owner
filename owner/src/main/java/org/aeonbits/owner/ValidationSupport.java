/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.validation.ConfigValidationException;
import org.aeonbits.owner.validation.ConfigValidator;
import org.aeonbits.owner.validation.ConstrainedProperty;
import org.aeonbits.owner.validation.Constraints;
import org.aeonbits.owner.validation.Violation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.aeonbits.owner.util.Util.unsupported;

/**
 * Finds the {@link ConfigValidator} implementations on the class path, hands them the properties that carry
 * constraints, and - the part that matters most - says out loud when a constraint is <b>not</b> being
 * checked.
 *
 * <p>
 * <b>The silence is the bug.</b> Bean Validation walks JavaBean properties, so it has always checked
 * <code>&#64;Min(12) int getPort()</code> and never <code>&#64;Min(12) int port()</code>, which is the
 * spelling this library teaches. Nothing failed and nothing was said: the annotation sat there looking like
 * a guarantee. Issue #201 is that, and half of the answer to it is not "validate more" but "never leave a
 * constraint unchecked without saying so" - which is why every method left out is reported here, and refused
 * outright under {@link PropertiesManager#STRICT}.
 * </p>
 *
 * @author Matteo Baccan
 * @since 2.0.0
 */
final class ValidationSupport {

    private static final Logger LOGGER = Logger.getLogger(ValidationSupport.class.getName());

    /**
     * The validators found on the class path, looked for once.
     * <p>
     * Once, and not once per configuration: {@link ServiceLoader} reads a resource and instantiates a class
     * every time it is asked, and a configuration is created wherever an application feels like it. The
     * field is written by {@link #validators()} under this class's own lock and by the two test seams below.
     * </p>
     */
    private static volatile List<ConfigValidator> validators;

    /** Don't let anyone instantiate this class */
    private ValidationSupport() {
    }

    /**
     * Checks what can be checked and reports what cannot.
     *
     * @param manager      the manager of the configuration being created, for the class name, the strictness
     *                     and nothing else.
     * @param properties   the properties carrying constraints that were safe to read, possibly empty.
     * @param notValidated one line per constraint that is not going to be checked, and why, possibly empty.
     * @throws ConfigValidationException if any constraint is violated.
     */
    static void check(PropertiesManager manager, List<ConstrainedProperty> properties,
                      List<String> notValidated) {
        reportWhatIsNotChecked(manager, notValidated);
        if (properties.isEmpty())
            return;

        List<ConfigValidator> found = validators();
        if (found.isEmpty()) {
            reportThatNobodyIsChecking(manager, properties);
            return;
        }

        List<Violation> violations = new LinkedList<>();
        for (ConfigValidator validator : found)
            violations.addAll(validator.validate(properties));

        // said even when nothing is wrong: "the constraints were checked" is the one thing a reader cannot
        // find out by looking at the configuration, and it is the question this whole mechanism raises
        LOGGER.log(Level.CONFIG, () -> String.format("%s: %d constrained %s checked by %s",
                manager.configuredClass().getName(), properties.size(),
                properties.size() == 1 ? "property" : "properties", names(found)));

        if (!violations.isEmpty())
            throw new ConfigValidationException(manager.configuredClass(), violations);
    }

    /**
     * A configuration carrying constraints that nothing on the class path can check.
     * <p>
     * The constraint annotations resolved, so a validation API is there; what is missing is anything that
     * knows what to do with them, which in practice means <code>owner-extras</code>. Reported rather than
     * thrown, because the application ran this way before the library learned to look, and a warning is
     * enough to stop it looking like a check. Under {@link PropertiesManager#STRICT} it is refused, that
     * being what strict means everywhere else here.
     * </p>
     */
    private static void reportThatNobodyIsChecking(PropertiesManager manager,
                                                   List<ConstrainedProperty> properties) {
        String name = manager.configuredClass().getName();
        if (manager.isStrict())
            throw unsupported("%s: %s %s validation constraints, and no %s was found on the class path to "
                            + "check them, and %s is on. Add owner-extras together with a validation "
                            + "provider, or write @DisableFeature(VALIDATION) on the interface to say that "
                            + "the annotations are there for something else.",
                    name, describe(properties), carry(properties), ConfigValidator.class.getSimpleName(),
                    PropertiesManager.STRICT);

        LOGGER.log(Level.WARNING, () -> String.format(
                "%s: %s %s validation constraints, and nothing on the class path can check them, so they "
                        + "are not being checked. Add owner-extras together with a validation provider "
                        + "(Hibernate Validator, Apache BVal) to have them enforced, or write "
                        + "@DisableFeature(VALIDATION) on the interface to say that the annotations are "
                        + "there for something else.",
                name, describe(properties), carry(properties)));
    }

    /**
     * Agrees the verb with what {@link #describe} listed, because these messages are read by somebody who
     * has one property wrong far more often than several, and a message that cannot count reads like one
     * nobody proofread.
     */
    private static String carry(List<ConstrainedProperty> properties) {
        return properties.size() == 1 ? "carries" : "carry";
    }

    /**
     * The constraints that are going to stay unchecked whatever is on the class path, because of the shape
     * of the method they are written on.
     * <p>
     * One line per method with the reason, since a reader who is told "something is not validated" without
     * being told which thing has been given a worry rather than an answer.
     * </p>
     */
    private static void reportWhatIsNotChecked(PropertiesManager manager, List<String> notValidated) {
        if (notValidated.isEmpty())
            return;
        String name = manager.configuredClass().getName();
        if (manager.isStrict())
            throw unsupported("%s: these validation constraints are not checked, and %s is on. %s",
                    name, PropertiesManager.STRICT, join(notValidated));

        LOGGER.log(Level.WARNING, () -> String.format(
                "%s: these validation constraints are declared and are not checked. %s", name,
                join(notValidated)));
    }

    /**
     * Tells whether the given interface, or any section reachable from it, declares a constraint anywhere.
     * <p>
     * Asked about the sections that are <b>not</b> built when the configuration is created - the elements of
     * a list, the values of a group, what an accessor taking arguments answers with - so that a constraint
     * written inside one of them can be reported as unchecked instead of being passed over. Nothing there is
     * ever validated, so the question is only ever "is there anything in there at all".
     * </p>
     */
    static boolean anyConstraintIn(Class<?> type) {
        return anyConstraintIn(type, new ArrayList<Class<?>>());
    }

    private static boolean anyConstraintIn(Class<?> type, List<Class<?>> visited) {
        // an interface can nest itself - a tree of sections is a legitimate shape - and the walk has to stop
        if (visited.contains(type))
            return false;
        visited.add(type);
        for (Method method : type.getMethods()) {
            if (Constraints.anyOn(method))
                return true;
            Class<?> section = NestedProperties.nests(method)
                    ? OptionalSupport.valueClass(method)
                    : NestedProperties.groupElementOf(method);
            if (section != null && anyConstraintIn(section, visited))
                return true;
        }
        return false;
    }

    /** Naming the keys rather than counting them: the reader has to go and find them. */
    private static String describe(List<ConstrainedProperty> properties) {
        StringBuilder text = new StringBuilder();
        for (ConstrainedProperty property : properties) {
            if (text.length() > 0) text.append(", ");
            text.append(property);
        }
        return text.toString();
    }

    private static String join(List<String> lines) {
        StringBuilder text = new StringBuilder();
        for (String line : lines) {
            if (text.length() > 0) text.append(' ');
            text.append(line);
        }
        return text.toString();
    }

    private static String names(List<ConfigValidator> found) {
        StringBuilder text = new StringBuilder();
        for (ConfigValidator validator : found) {
            if (text.length() > 0) text.append(", ");
            text.append(validator.getClass().getName());
        }
        return text.toString();
    }

    /**
     * The validators declared in
     * <code>META-INF/services/org.aeonbits.owner.validation.ConfigValidator</code>.
     * <p>
     * The context class loader is asked first and the one that loaded OWNER is the fallback, as
     * {@link LoadersManager} does and for the same reasons. A <code>ServiceConfigurationError</code> is left
     * to propagate: it means a broken artifact on the class path.
     * </p>
     */
    private static List<ConfigValidator> validators() {
        List<ConfigValidator> found = validators;
        if (found != null)
            return found;
        synchronized (ValidationSupport.class) {
            if (validators == null)
                validators = discover();
            return validators;
        }
    }

    private static List<ConfigValidator> discover() {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        List<ConfigValidator> found = new ArrayList<>();
        for (ConfigValidator validator : ServiceLoader.load(ConfigValidator.class,
                context != null ? context : ValidationSupport.class.getClassLoader()))
            found.add(validator);
        return found;
    }

    /**
     * Replaces what was discovered, for the tests: the class path a test suite runs on is the one thing a
     * test about class path discovery cannot arrange. Whoever calls this owes a {@link #forget()} in a
     * {@code finally} or an {@code @After}, this being JVM-wide.
     *
     * @param replacement the validators to use from now on.
     */
    static void install(List<ConfigValidator> replacement) {
        validators = new ArrayList<>(replacement);
    }

    /** Undoes {@link #install(List)}, so that the next configuration looks at the class path again. */
    static void forget() {
        validators = null;
    }
}
