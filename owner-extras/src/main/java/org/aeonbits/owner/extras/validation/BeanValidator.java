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
import org.aeonbits.owner.validation.Violation;

import java.util.ArrayList;
import java.util.List;

import static org.aeonbits.owner.util.Reflection.isClassAvailable;
import static org.aeonbits.owner.util.Util.unsupported;

/**
 * Checks a configuration's values with
 * <a href="https://beanvalidation.org/">Bean Validation</a> - the annotations everybody already writes,
 * <code>&#64;Min</code>, <code>&#64;NotNull</code>, <code>&#64;Pattern</code> and the rest - so that they
 * mean something on a method named the way this library teaches.
 *
 * <h2>What was wrong</h2>
 *
 * <p>
 * Nothing, as long as the methods were named as JavaBean getters. <code>Validator.validate(object)</code>
 * walks properties and fields, so <code>&#64;Min(12) int getPort()</code> was a property called
 * <code>port</code> and was checked, while <code>&#64;Min(12) int port()</code> was neither a property nor a
 * field and was <b>passed over without a word</b>. That is issue #201, and the silence was the dangerous
 * half of it: an annotation that reads like a guarantee, on the spelling the documentation teaches, checking
 * nothing.
 * </p>
 *
 * <p>
 * The cure is the part of the specification written for exactly this,
 * <code>Validator.forExecutables().validateReturnValue(object, method, value)</code>: it checks the value a
 * method actually returned, and a method is a method whatever it is called. Both spellings are checked here,
 * and each of them <b>once</b> - this never calls <code>validate(object)</code> as well, which would find
 * the getter-shaped ones a second time as properties and report the same thing twice under two different
 * names.
 * </p>
 *
 * <h2>What it costs, and to whom</h2>
 *
 * <p>
 * <b>Nothing, unless a configuration carries constraints.</b> Both validation APIs are optional dependencies
 * of <code>owner-extras</code>: they are not transitive, this project does not ship them, and nobody
 * receives one by depending on OWNER. Nothing in this class names either of them - everything that does
 * lives in {@link JakartaBeanValidation} and {@link JavaxBeanValidation}, neither of which is touched until
 * there is something to check - so this validator is discovered and instantiated on a class path without
 * them, like any other service. And the core only asks a validator anything about a configuration whose
 * methods carry constraint annotations, which on a class path without a validation API is no configuration
 * at all.
 * </p>
 *
 * <h2>javax or jakarta: both, and why</h2>
 *
 * <p>
 * <code>javax.validation</code> is Bean Validation 1.1 and 2.0; <code>jakarta.validation</code> is the same
 * specification after the rename, version 3.0 and later. <b>Choosing one would have been choosing an
 * audience.</b> This library still compiles to Java 8, and the Java 8 world is the <code>javax</code> one -
 * Hibernate Validator 6.2 is the last release that runs there under that name, and Spring Boot 2 and Jakarta
 * EE 8 are full of it. Everything current is <code>jakarta</code>: Spring Boot 3, Jakarta EE 10, Hibernate
 * Validator 8, which needs Java 11 and would not run on this library's own baseline. A user cannot pick
 * their namespace anyway - their framework picked it years ago - so both are looked for, and whichever
 * namespaces are present <b>and have a provider</b> are used. An application in the middle of the rename,
 * with constraints of both kinds, has both checked.
 * </p>
 *
 * <p>
 * The cost of that decision is one dependency line each in <code>owner-extras</code>, both optional, and
 * this class asking twice.
 * </p>
 *
 * @author Matteo Baccan
 * @see org.aeonbits.owner.validation.ConfigValidator
 * @since 2.0.0
 */
public class BeanValidator implements ConfigValidator {

    /**
     * A validator is built with no arguments, both when it is registered by hand and when
     * {@link java.util.ServiceLoader} finds it on the class path. Declared rather than left implicit so that
     * the requirement is visible to whoever changes this class.
     */
    public BeanValidator() {
    }

    /** The class whose presence means the API is there. Named as text: this class must not load it. */
    private static final String JAKARTA_API = "jakarta.validation.Validation";

    private static final String JAVAX_API = "javax.validation.Validation";

    /**
     * {@inheritDoc}
     * <p>
     * The <code>&amp;&amp;</code> below is doing real work: a class path holding one namespace and not the
     * other must never load the class that names the missing one, and a method body is only resolved when
     * it runs. See {@link JakartaBeanValidation} for why that separation must not be undone.
     * </p>
     */
    @Override
    public List<Violation> validate(List<ConstrainedProperty> properties) {
        List<Violation> violations = new ArrayList<>();
        boolean checked = false;

        if (isClassAvailable(JAKARTA_API) && JakartaBeanValidation.hasProvider()) {
            violations.addAll(JakartaBeanValidation.check(properties));
            checked = true;
        }
        if (isClassAvailable(JAVAX_API) && JavaxBeanValidation.hasProvider()) {
            violations.addAll(JavaxBeanValidation.check(properties));
            checked = true;
        }

        // returning no violations would mean "these constraints hold", which is not something to say about
        // constraints nobody looked at. See ConfigValidator
        if (!checked)
            throw unsupported("The configuration carries validation constraints and no Bean Validation "
                    + "provider could be started to check them. Add one to the class path - Hibernate "
                    + "Validator 8 or Apache BVal 3 for jakarta.validation, Hibernate Validator 6.2 or "
                    + "Apache BVal 2 for javax.validation - or write @DisableFeature(VALIDATION) on the "
                    + "interface to say that the annotations are there for something else.");

        return violations;
    }
}
