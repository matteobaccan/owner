/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.extras.validation;

import org.aeonbits.owner.validation.ConstrainedProperty;
import org.aeonbits.owner.validation.Violation;

import javax.validation.constraints.Min;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.Callable;

import static java.util.Collections.singletonList;

/**
 * Runs inside the class loader built by {@link BeanValidatorDiscoveryTest}, where
 * <code>jakarta.validation</code> is unavailable and <code>javax.validation</code> is not, and reports what
 * happened as a string.
 *
 * <p>
 * This is the ordinary class path rather than an exotic one: an application has the namespace its framework
 * chose, one of the two, and never both. The suite it belongs to has both, which is what makes a probe
 * necessary to find out whether either half stands on its own.
 * </p>
 *
 * @author Matteo Baccan
 */
public class WithoutJakartaProbe implements Callable<String> {

    /** Constrained under the name Bean Validation had before the Jakarta rename. */
    public interface Server {
        @Min(12)
        int port();
    }

    @Override
    public String call() {
        StringBuilder report = new StringBuilder();
        report.append("jakarta absent: ").append(absent("jakarta.validation.Validation")).append('\n');
        report.append("javax absent: ").append(absent("javax.validation.Validation")).append('\n');

        try {
            List<Violation> violations = new BeanValidator().validate(singletonList(portIsOne()));
            report.append("violations: ").append(violations.size()).append('\n');
            for (Violation violation : violations)
                report.append("violation: ").append(violation.key())
                        .append(" / ").append(violation.methodName()).append('\n');
        } catch (Throwable failed) {
            report.append("checking: ").append(failed.getClass().getName())
                    .append(" / ").append(failed.getMessage());
        }
        return report.toString();
    }

    private static ConstrainedProperty portIsOne() {
        try {
            Object config = Proxy.newProxyInstance(WithoutJakartaProbe.class.getClassLoader(),
                    new Class<?>[]{Server.class}, answering(1));
            return new ConstrainedProperty(config, Server.class.getMethod("port"), "port", 1);
        } catch (NoSuchMethodException impossible) {
            throw new AssertionError(impossible);
        }
    }

    /**
     * A configuration object in miniature. The identity methods are answered here because a validation
     * provider puts the object it is checking into a collection of its own, and a proxy that answers
     * <code>null</code> to <code>hashCode</code> fails in a way that says nothing about validation.
     */
    private static InvocationHandler answering(final int port) {
        return new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                if ("hashCode".equals(method.getName()))
                    return System.identityHashCode(proxy);
                if ("equals".equals(method.getName()))
                    return proxy == args[0];
                if ("toString".equals(method.getName()))
                    return "Server";
                return port;
            }
        };
    }

    private static boolean absent(String className) {
        try {
            Class.forName(className, false, WithoutJakartaProbe.class.getClassLoader());
            return false;
        } catch (ClassNotFoundException | NoClassDefFoundError absent) {
            return true;
        }
    }
}
