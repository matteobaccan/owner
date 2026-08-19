/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.extras.validation;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Factory;
import org.aeonbits.owner.validation.ConfigValidationException;
import org.aeonbits.owner.validation.Violation;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Issue #201 end to end, against real providers: Hibernate Validator for <code>jakarta.validation</code> and
 * Apache BVal for <code>javax.validation</code>, both on this suite's class path at once, which is also the
 * pair the issue was reported against.
 *
 * <p>
 * <b>Not one assertion on a message.</b> The text of a violation is the provider's, interpolated and
 * translated into whatever locale the machine happens to run in - this suite reads them in Italian on the
 * author's laptop - so what is asserted is the key, the method and how many there were, which is what this
 * library contributes and what a user reads.
 * </p>
 *
 * @author Matteo Baccan
 */
public class BeanValidationTest {

    // ------------------------------------------------------ the interface from the issue

    /** The interface of issue #201, in the spelling the documentation teaches. */
    public interface ServerConfig extends Config {
        @jakarta.validation.constraints.Min(12)
        @DefaultValue("1")
        @Key("port")
        int port();

        @jakarta.validation.constraints.NotNull
        String hostname();

        @DefaultValue("42")
        int maxThreads();
    }

    @Test
    public void theOwnerStyleAccessorIsValidatedAtLast() {
        try {
            ConfigFactory.create(ServerConfig.class);
            fail("expected @Min(12) on port() to be enforced");
        } catch (ConfigValidationException violated) {
            assertEquals(2, violated.getViolations().size());
            assertTrue(violated.getKeys().toString(), violated.getKeys().contains("port"));
            assertTrue(violated.getKeys().toString(), violated.getKeys().contains("hostname"));
        }
    }

    @Test
    public void aConfigurationThatSatisfiesItsConstraintsIsCreated() {
        Properties values = new Properties();
        values.setProperty("port", "8080");
        values.setProperty("hostname", "localhost");

        ServerConfig config = ConfigFactory.create(ServerConfig.class, values);

        assertEquals(8080, config.port());
        assertEquals("localhost", config.hostname());
    }

    // ------------------------------------------------------ the spelling that always worked

    public interface GetterStyle extends Config {
        @jakarta.validation.constraints.Min(12)
        @DefaultValue("1")
        int getPort();
    }

    /**
     * The one that could go wrong twice. A getter-named method is a JavaBean property as well as a method,
     * so a mechanism that ran <code>validate(object)</code> alongside <code>validateReturnValue</code> would
     * find it under both names and report the same value as two failures - once against the property
     * <code>port</code> and once against <code>getPort().&lt;return value&gt;</code>. Only executable
     * validation is used, for every spelling alike.
     */
    @Test
    public void aGetterNamedMethodIsReportedOnceAndNotTwice() {
        try {
            ConfigFactory.create(GetterStyle.class);
            fail("expected @Min(12) on getPort() to be enforced");
        } catch (ConfigValidationException violated) {
            assertEquals(violated.getMessage(), 1, violated.getViolations().size());
            assertEquals("getPort", violated.getViolations().get(0).methodName());
            assertEquals("getPort", violated.getViolations().get(0).key());
        }
    }

    // ------------------------------------------------------ the other namespace

    public interface JavaxStyle extends Config {
        @javax.validation.constraints.Min(12)
        @DefaultValue("1")
        int port();
    }

    /**
     * <code>javax.validation</code> is the same specification before the rename, and the Java 8 world this
     * library still compiles for is full of it. Both namespaces are looked for, so this is checked by Apache
     * BVal while everything above is checked by Hibernate Validator, in the same run and with no
     * configuration.
     */
    @Test
    public void theOtherSpellingOfTheSameSpecificationIsCheckedToo() {
        try {
            ConfigFactory.create(JavaxStyle.class);
            fail("expected the javax.validation constraint to be enforced");
        } catch (ConfigValidationException violated) {
            assertEquals(1, violated.getViolations().size());
            assertEquals("port", violated.getViolations().get(0).key());
        }
    }

    // ------------------------------------------------------ containers

    public interface Containers extends Config {
        @DefaultValue("1")
        Optional<@jakarta.validation.constraints.Min(12) Integer> port();

        @DefaultValue("1, 20")
        List<@jakarta.validation.constraints.Min(12) Integer> ports();
    }

    /**
     * <code>Optional&lt;&#64;Min(12) Integer&gt;</code>: the constraint is on the value and the provider
     * unwraps the container to reach it, which is the spelling this library tells people to use and the
     * reason it can be told to.
     */
    @Test
    public void aConstraintInsideAnOptionalIsChecked() {
        try {
            ConfigFactory.create(Containers.class, oneGoodList());
            fail("expected the constraint inside the Optional to be enforced");
        } catch (ConfigValidationException violated) {
            assertEquals(1, violated.getViolations().size());
            assertEquals("port", violated.getViolations().get(0).key());
        }
    }

    private static Properties oneGoodList() {
        Properties values = new Properties();
        values.setProperty("ports", "20, 30");
        return values;
    }

    /**
     * An element of a list, where knowing <em>which</em> element is the difference between a message and an
     * answer: the key names the property, and the index comes off the provider's own path.
     */
    @Test
    public void aConstraintOnAListElementNamesTheElement() {
        Properties values = new Properties();
        values.setProperty("port", "20");
        values.setProperty("ports", "20, 1");

        try {
            ConfigFactory.create(Containers.class, values);
            fail("expected the constraint on the list elements to be enforced");
        } catch (ConfigValidationException violated) {
            assertEquals(1, violated.getViolations().size());
            Violation violation = violated.getViolations().get(0);
            assertEquals("ports", violation.key());
            assertTrue(violation.message(), violation.message().startsWith("element [1]:"));
        }
    }

    // ------------------------------------------------------ sections

    public interface WithASection extends Config {
        Server server();

        interface Server extends Config {
            @jakarta.validation.constraints.Min(12)
            @DefaultValue("1")
            int port();
        }
    }

    /** A section is walked into, and what comes back is the whole key rather than the method's name. */
    @Test
    public void aSectionIsCheckedAndItsKeyIsWhole() {
        try {
            ConfigFactory.create(WithASection.class);
            fail("expected the constraint inside the section to be enforced");
        } catch (ConfigValidationException violated) {
            assertEquals(1, violated.getViolations().size());
            assertEquals("server.port", violated.getViolations().get(0).key());
        }
    }

    // ------------------------------------------------------ a constraint on the container itself

    public interface ConstraintOnTheContainer extends Config {
        @jakarta.validation.constraints.Min(12)
        @DefaultValue("1")
        Optional<Integer> port();
    }

    /**
     * <code>&#64;Min(12) Optional&lt;Integer&gt;</code> asks for a number to be checked and hands over an
     * <code>Optional</code>. The provider has no validator for that and says so; what it says names its own
     * path, so it is caught and told again naming the method and the spelling that works.
     */
    @Test
    public void aConstraintTheProviderCannotApplyNamesTheMethod() {
        try {
            ConfigFactory.create(ConstraintOnTheContainer.class);
            fail("expected the misplaced constraint to be refused");
        } catch (UnsupportedOperationException refused) {
            assertTrue(refused.getMessage(), refused.getMessage().contains("'port()'"));
            assertTrue(refused.getMessage(), refused.getMessage().contains("Optional<@Min(12) Integer>"));
        }
    }

    // ------------------------------------------------------ the shapes that have no value to check

    /** A constraint on a value that will not convert, so it cannot be read to be checked. */
    public interface ConstraintOnAValueThatWillNotConvert extends Config {
        @jakarta.validation.constraints.Min(12)
        @DefaultValue("not a number")
        int port();
    }

    /**
     * A value that refuses to convert is <b>reported and passed over</b>, not thrown from the validation:
     * it fails on its own, with its own message, the moment anybody reads it, and turning that into a
     * failure of the validation would report the wrong thing about the wrong feature. What must not
     * happen is that it is passed over in silence — so under <code>owner.strict</code> it is a refusal,
     * which is what this asserts, and a warning otherwise.
     */
    @Test
    public void aValueThatCannotBeReadIsReportedRatherThanThrownFromTheValidation() {
        Factory strict = ConfigFactory.newInstance();
        strict.setProperty("owner.strict", "true");
        try {
            strict.create(ConstraintOnAValueThatWillNotConvert.class);
            fail("expected a value that cannot be read to be reported");
        } catch (UnsupportedOperationException refused) {
            assertTrue(refused.getMessage(), refused.getMessage().contains("'port()'"));
            assertTrue(refused.getMessage(), refused.getMessage().contains("could not be read"));
        }
    }

    /** Without strict the same interface is created, and the value fails on its own when it is read. */
    @Test
    public void andWithoutStrictItIsOnlyAWarning() {
        ConstraintOnAValueThatWillNotConvert cfg =
                ConfigFactory.newInstance().create(ConstraintOnAValueThatWillNotConvert.class);
        try {
            cfg.port();
            fail("the value still fails when it is read, which is where it belongs");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("not a number"));
        }
    }

    /** The same interface with validation switched off on the method: nothing is checked and nothing said. */
    public interface ValidationDisabledOnTheMethod extends Config {
        @jakarta.validation.constraints.Min(12)
        @DisableFeature(DisableableFeature.VALIDATION)
        @DefaultValue("1")
        int port();
    }

    /**
     * <code>@DisableFeature(VALIDATION)</code> on a method takes it out of the check <b>and</b> out of the
     * report — under <code>owner.strict</code> too, which is the assertion that says the two are the same
     * decision. Saying "this constraint is not being checked" about a method that asked not to be checked
     * would be noise, and noise is how a real warning stops being read.
     */
    @Test
    public void aMethodThatDisabledValidationIsNeitherCheckedNorReported() {
        Factory strict = ConfigFactory.newInstance();
        strict.setProperty("owner.strict", "true");
        assertEquals(1, strict.create(ValidationDisabledOnTheMethod.class).port());
    }

    /**
     * A constraint the provider has no validator for. <code>@Min</code> on a String would not do — the
     * provider reads it as a number and reports an ordinary violation — so this is <code>@Size</code> on
     * an <code>int</code>, which has no length to measure.
     */
    public interface ConstraintOnATypeItDoesNotFit extends Config {
        @jakarta.validation.constraints.Size(min = 2)
        @DefaultValue("8080")
        int port();
    }

    /**
     * A constraint written on a type its provider has no validator for.
     * <p>
     * The provider raises this while validating, not while starting, so without a word here it arrives as
     * a bare <code>UnexpectedTypeException</code> from inside somebody else's library. It is turned into a
     * refusal that names <b>the method</b>, which is the one piece of information the provider's own
     * message does not carry and the only one that says where to go and fix it.
     * </p>
     */
    @Test
    public void aConstraintOnATypeTheProviderCannotValidateNamesTheMethod() {
        try {
            ConfigFactory.newInstance().create(ConstraintOnATypeItDoesNotFit.class);
            fail("expected a constraint with no validator for its type to be refused");
        } catch (UnsupportedOperationException refused) {
            assertTrue(refused.getMessage(), refused.getMessage().contains("port"));
        }
    }
}
