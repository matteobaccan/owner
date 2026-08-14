/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.extras.validation;

import org.aeonbits.owner.validation.ConfigValidator;
import org.aeonbits.owner.validation.ConstrainedProperty;

import java.lang.reflect.Method;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;

import static java.util.Collections.singletonList;

/**
 * Runs inside the class loader built by {@link BeanValidatorDiscoveryTest}, where neither
 * <code>javax.validation</code> nor <code>jakarta.validation</code> is available, and reports what happened
 * as a string.
 *
 * @author Matteo Baccan
 */
public class WithoutValidationApisProbe implements Callable<String> {

    @Override
    public String call() {
        StringBuilder report = new StringBuilder();

        report.append("jakarta absent: ").append(absent("jakarta.validation.Validation")).append('\n');
        report.append("javax absent: ").append(absent("javax.validation.Validation")).append('\n');

        ConfigValidator discovered = findBeanValidator();
        report.append("discovered: ").append(discovered != null).append('\n');
        if (discovered == null)
            return report.toString();

        try {
            discovered.validate(singletonList(somethingToCheck()));
            report.append("checking: no failure at all");
        } catch (Throwable failed) {
            report.append("checking: ").append(failed.getClass().getName())
                    .append(" / ").append(failed.getMessage());
        }
        return report.toString();
    }

    /**
     * Any property at all: what is under test is a class path with no provider on it, and nothing gets as far
     * as looking at an annotation.
     */
    private static ConstrainedProperty somethingToCheck() {
        try {
            Method method = String.class.getMethod("length");
            return new ConstrainedProperty("something", method, "some.key", 3);
        } catch (NoSuchMethodException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static boolean absent(String className) {
        try {
            Class.forName(className, false, WithoutValidationApisProbe.class.getClassLoader());
            return false;
        } catch (ClassNotFoundException | NoClassDefFoundError absent) {
            return true;
        }
    }

    private static ConfigValidator findBeanValidator() {
        for (ConfigValidator validator : ServiceLoader.load(ConfigValidator.class,
                WithoutValidationApisProbe.class.getClassLoader()))
            // by name, and isAssignableFrom would be exactly wrong here: this runs in a class loader of its
            // own, so the two classes share a name and nothing else
            if (validator.getClass().getName().equals(BeanValidator.class.getName())) // NOSONAR
                return validator;
        return null;
    }
}
