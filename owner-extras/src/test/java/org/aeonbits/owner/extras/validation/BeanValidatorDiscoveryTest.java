/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.extras.validation;

import org.aeonbits.owner.extras.loaders.WithholdingClassLoader;
import org.junit.Test;

import java.util.concurrent.Callable;

import static org.junit.Assert.assertTrue;

/**
 * What happens on the class paths this suite can never run on: the one with no validation API at all, which
 * is the ordinary case for <code>owner-extras</code>, and the one with a single namespace, which is the
 * ordinary case for an application.
 *
 * <p>
 * {@link BeanValidator} is discovered by {@link java.util.ServiceLoader} like every other service, so it is
 * <b>instantiated in every application that has this artifact</b>, whether or not it will ever check
 * anything. Both validation APIs being optional, that instantiation has to survive their absence -
 * {@link BeanValidatorIsolationTest} proves the class does not mention them, and this proves the
 * consequence.
 * </p>
 *
 * @author Matteo Baccan
 */
public class BeanValidatorDiscoveryTest {

    @SuppressWarnings("unchecked")
    private static String runInside(WithholdingClassLoader loader, Class<?> probe) throws Exception {
        Class<?> inside = loader.loadClass(probe.getName());
        return ((Callable<String>) inside.getDeclaredConstructor().newInstance()).call();
    }

    /**
     * The ordinary class path for this artifact: <code>owner-extras</code> is there for a HOCON file or a
     * ZooKeeper source, and nobody has ever heard of Bean Validation.
     */
    @Test
    public void withNoValidationApiTheValidatorIsStillDiscoveredAndSaysWhatToAdd() throws Exception {
        String report;
        try (WithholdingClassLoader loader =
                     new WithholdingClassLoader("javax.validation", "jakarta.validation")) {
            report = runInside(loader, WithoutValidationApisProbe.class);
        }

        assertTrue("the class loader is not withholding the validation APIs, so this proves nothing:\n"
                + report, report.contains("jakarta absent: true"));
        assertTrue(report, report.contains("javax absent: true"));

        // a failure here would break every configuration in the application, not only a constrained one
        assertTrue("BeanValidator was not discovered without a validation API:\n" + report,
                report.contains("discovered: true"));

        assertTrue("checking did not report the missing dependency:\n" + report,
                report.contains("checking: java.lang.UnsupportedOperationException"));
        assertTrue("the failure does not say what to add:\n" + report,
                report.contains("Hibernate Validator"));
    }

    /**
     * One namespace and not the other, which is what every real application has. The half that is there does
     * the work on its own, and the half that is not is never loaded - a class path holding
     * <code>javax.validation</code> must not need <code>jakarta.validation</code> to check a constraint.
     */
    @Test
    public void withOnlyOneNamespaceThatHalfDoesTheWorkAlone() throws Exception {
        String report;
        try (WithholdingClassLoader loader = new WithholdingClassLoader("jakarta.validation")) {
            report = runInside(loader, WithoutJakartaProbe.class);
        }

        assertTrue("the class loader is not withholding jakarta.validation:\n" + report,
                report.contains("jakarta absent: true"));
        assertTrue("javax.validation should still be there:\n" + report,
                report.contains("javax absent: false"));

        assertTrue("the javax half found nothing without jakarta on the class path:\n" + report,
                report.contains("violations: 1"));
        assertTrue(report, report.contains("violation: port / port"));
    }
}
