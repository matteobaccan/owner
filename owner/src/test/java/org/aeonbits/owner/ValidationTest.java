/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.util.LogCapture;
import org.aeonbits.owner.validation.AtLeast;
import org.aeonbits.owner.validation.ConfigValidationException;
import org.aeonbits.owner.validation.ConfigValidator;
import org.aeonbits.owner.validation.ConstrainedProperty;
import org.aeonbits.owner.validation.Required;
import org.aeonbits.owner.validation.Violation;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Issue #201, from the side of the core: which methods a validator is handed, which it is not, and what is
 * said about the ones it is not.
 *
 * <p>
 * The validator here is a fake, and deliberately: the core has no validation API and is never going to have
 * one, so what it can be held to is that it finds the constraints, reads the values, resolves the keys, walks
 * into the sections and reports what it left out. That a real provider then agrees is
 * <code>owner-extras</code>'s business, and <code>BeanValidationTest</code> there is where it is proved.
 * </p>
 *
 * @author Matteo Baccan
 * @see org.aeonbits.owner.validation.ConfigValidator
 */
public class ValidationTest {

    private Recorder recorder;
    private LogCapture capture;

    @Before
    public void installAValidator() {
        recorder = new Recorder();
        ValidationSupport.install(singletonList((ConfigValidator) recorder));
        capture = LogCapture.ofLibrary(Level.CONFIG);
    }

    /**
     * The discovered validators are one field for the whole JVM, so a test that replaces them owes the next
     * one the class path back - the more so because this suite runs in a random order.
     */
    @After
    public void putEverythingBack() {
        ValidationSupport.forget();
        capture.close();
    }

    /** Checks the two constraints declared in this project's test sources, and records what it was given. */
    static class Recorder implements ConfigValidator {

        final List<ConstrainedProperty> seen = new ArrayList<>();

        @Override
        public List<Violation> validate(List<ConstrainedProperty> properties) {
            seen.addAll(properties);
            List<Violation> violations = new ArrayList<>();
            for (ConstrainedProperty property : properties) {
                AtLeast bound = property.method().getAnnotation(AtLeast.class);
                if (bound != null && property.value() instanceof Number
                        && ((Number) property.value()).intValue() < bound.value())
                    violations.add(new Violation(property.key(), property.method().getName(),
                            "must be at least " + bound.value()));
                if (property.method().getAnnotation(Required.class) != null && property.value() == null)
                    violations.add(new Violation(property.key(), property.method().getName(),
                            "must not be null"));
            }
            return violations;
        }
    }

    private List<String> keysSeen() {
        List<String> keys = new ArrayList<>();
        for (ConstrainedProperty property : recorder.seen)
            keys.add(property.key());
        return keys;
    }

    // ------------------------------------------------------ the issue itself

    /** The interface of issue #201, in the spelling that used not to be checked. */
    public interface ServerConfig extends Config {
        @AtLeast(12)
        @DefaultValue("1")
        @Key("port")
        int port();

        @Required
        String hostname();

        @DefaultValue("42")
        int maxThreads();
    }

    @Test
    public void theOwnerStyleAccessorIsCheckedAtLast() {
        try {
            ConfigFactory.create(ServerConfig.class);
            fail("expected the constraints to be enforced");
        } catch (ConfigValidationException violated) {
            assertEquals(2, violated.getViolations().size());
            assertTrue(violated.getMessage(), violated.getMessage().contains("'port'"));
            assertTrue(violated.getMessage(), violated.getMessage().contains("'hostname'"));
            assertTrue(violated.getMessage(), violated.getMessage().contains("must be at least 12"));
        }
    }

    /** Every one of them, in one exception: a configuration is read once, at startup. */
    @Test
    public void theKeysOfEveryViolationAreListed() {
        try {
            ConfigFactory.create(ServerConfig.class);
            fail("expected the constraints to be enforced");
        } catch (ConfigValidationException violated) {
            assertEquals(asList("port", "hostname"), sorted(violated.getKeys()));
        }
    }

    private static List<String> sorted(List<String> keys) {
        List<String> copy = new ArrayList<>(keys);
        Collections.sort(copy);
        Collections.reverse(copy);   // 'port' before 'hostname' reads the way the interface is written
        return copy;
    }

    @Test
    public void aValueThatSatisfiesTheConstraintsPassesInSilence() {
        Properties values = new Properties();
        values.setProperty("port", "8080");
        values.setProperty("hostname", "localhost");

        ServerConfig config = ConfigFactory.create(ServerConfig.class, values);

        assertEquals(8080, config.port());
        assertEquals(2, recorder.seen.size());
    }

    /** The methods with no constraint on them are never read for validation: only the constrained ones. */
    @Test
    public void onlyTheConstrainedMethodsAreHandedOver() {
        Properties values = new Properties();
        values.setProperty("port", "8080");
        values.setProperty("hostname", "localhost");

        ConfigFactory.create(ServerConfig.class, values);

        assertEquals(asList("hostname", "port"), sortedAscending(keysSeen()));
    }

    private static List<String> sortedAscending(List<String> keys) {
        List<String> copy = new ArrayList<>(keys);
        Collections.sort(copy);
        return copy;
    }

    // ------------------------------------------------------ the getter spelling

    /** The spelling that always worked, which must not now be checked twice. */
    public interface GetterStyle extends Config {
        @AtLeast(12)
        @DefaultValue("1")
        int getPort();
    }

    /**
     * A getter-named method is a JavaBean property <b>and</b> a method, so a mechanism that used both
     * <code>validate(object)</code> and <code>validateReturnValue</code> would report it once under each
     * name. Only the second is used, here and in owner-extras, and this is what says so.
     */
    @Test
    public void aGetterNamedMethodIsCheckedExactlyOnce() {
        try {
            ConfigFactory.create(GetterStyle.class);
            fail("expected the constraint to be enforced");
        } catch (ConfigValidationException violated) {
            assertEquals(1, violated.getViolations().size());
            assertEquals(1, recorder.seen.size());
            assertEquals("getPort", violated.getViolations().get(0).methodName());
        }
    }

    // ------------------------------------------------------ nested sections

    public interface WithASection extends Config {
        Server server();

        interface Server extends Config {
            @AtLeast(12)
            @DefaultValue("1")
            int port();
        }
    }

    /**
     * A section is descended into and its keys arrive whole, prefix and path included - which is the point
     * of validating when the configuration is created rather than leaving it to whoever calls the method.
     */
    @Test
    public void theSectionsAreWalkedIntoAndTheirKeysAreWhole() {
        try {
            ConfigFactory.create(WithASection.class);
            fail("expected the constraint inside the section to be enforced");
        } catch (ConfigValidationException violated) {
            assertEquals(singletonList("server.port"), violated.getKeys());
        }
    }

    // ------------------------------------------------------ what is not checked, and is said

    public interface EveryShapeThatCannotBeChecked extends Config {
        @AtLeast(12)
        @Key("servers.%s.port")
        int portOf(String name);

        @AtLeast(12)
        default int computed() {
            return 1;
        }

        @Required
        WithASection.Server section();

        @AtLeast(12)
        Optional<Integer> optionalPort();

        Map<String, WithASection.Server> group();
    }

    /**
     * The section this interface reads is descended into as any other, so its own constraint is satisfied
     * here: what is under test is the report about the accessor, not a violation inside it.
     */
    private static Properties aValidSection() {
        Properties values = new Properties();
        values.setProperty("section.port", "20");
        return values;
    }

    @Test
    public void everyShapeThatCannotBeCheckedIsNamedWithItsReason() {
        ConfigFactory.create(EveryShapeThatCannotBeChecked.class, aValidSection());

        String said = capture.messagesFrom(Level.WARNING);
        assertTrue(said, said.contains("'portOf()' takes arguments"));
        assertTrue(said, said.contains("'computed()' is a default method"));
        assertTrue(said, said.contains("'section()' reads a nested section"));
        assertTrue(said, said.contains("'optionalPort()' returns an Optional"));
        assertTrue(said, said.contains("inside 'Server', which 'group()' reads"));
        assertEquals("one line, not one per method", 1, capture.linesFrom(Level.WARNING).size());
    }

    @Test
    public void underStrictTheyAreRefused() {
        Factory strict = ConfigFactory.newInstance();
        strict.setProperty("owner.strict", "true");

        try {
            strict.create(EveryShapeThatCannotBeChecked.class, aValidSection());
            fail("expected the unchecked constraints to be refused");
        } catch (UnsupportedOperationException refused) {
            assertTrue(refused.getMessage(), refused.getMessage().contains("owner.strict"));
            assertTrue(refused.getMessage(), refused.getMessage().contains("'portOf()'"));
        }
    }

    /** A group of sections with nothing constrained inside it is not worth a word. */
    public interface PlainSection extends Config {
        @DefaultValue("1")
        int port();
    }

    public interface GroupOfPlainSections extends Config {
        Map<String, PlainSection> servers();
    }

    @Test
    public void aGroupWithNothingConstrainedInsideItSaysNothing() {
        ConfigFactory.create(GroupOfPlainSections.class);

        assertTrue(capture.messagesFrom(Level.WARNING), capture.linesFrom(Level.WARNING).isEmpty());
    }

    // ------------------------------------------------------ nothing on the class path to check with

    @Test
    public void withNoValidatorAtAllTheConfigurationSaysSo() {
        ValidationSupport.install(Collections.<ConfigValidator>emptyList());

        ConfigFactory.create(GetterStyle.class);

        String said = capture.messagesFrom(Level.WARNING);
        assertTrue(said, said.contains("getPort"));
        assertTrue(said, said.contains("owner-extras"));
    }

    @Test
    public void withNoValidatorAndUnderStrictItIsRefused() {
        ValidationSupport.install(Collections.<ConfigValidator>emptyList());
        Factory strict = ConfigFactory.newInstance();
        strict.setProperty("owner.strict", "true");

        try {
            strict.create(GetterStyle.class);
            fail("expected the unchecked constraints to be refused");
        } catch (UnsupportedOperationException refused) {
            assertTrue(refused.getMessage(), refused.getMessage().contains("owner.strict"));
            assertTrue(refused.getMessage(), refused.getMessage().contains("getPort"));
        }
    }

    // ------------------------------------------------------ saying no

    @Config.DisableFeature(Config.DisableableFeature.VALIDATION)
    public interface NotOurConstraints extends Config {
        @AtLeast(12)
        @DefaultValue("1")
        int port();

        @AtLeast(12)
        @Key("servers.%s.port")
        int portOf(String name);
    }

    /**
     * <code>@DisableFeature(VALIDATION)</code> turns off the report as well as the check, which is the whole
     * of what it is for: an interface whose annotations belong to somebody else has nothing to be told.
     */
    @Test
    public void disablingTheFeatureSilencesTheCheckAndTheReportAlike() {
        ConfigFactory.create(NotOurConstraints.class);

        assertTrue(recorder.seen.isEmpty());
        assertTrue(capture.messagesFrom(Level.WARNING), capture.linesFrom(Level.WARNING).isEmpty());
    }

    // ------------------------------------------------------ costing nothing to everybody else

    public interface NothingConstrained extends Config {
        @DefaultValue("1")
        int port();

        @DefaultValue("localhost")
        String hostname();
    }

    /**
     * The configuration nearly everybody has: no constraint anywhere, so no validator is asked anything and
     * no value is read to ask about.
     */
    @Test
    public void aConfigurationWithoutConstraintsNeverReachesAValidator() {
        assertEquals(1, ConfigFactory.create(NothingConstrained.class).port());

        assertTrue(recorder.seen.isEmpty());
        assertFalse(capture.messagesFrom(Level.CONFIG).contains("checked by"));
    }

    /** ...and the one that has them says, once, that they were checked. */
    @Test
    public void aConfigurationThatWasCheckedSaysSoAtConfigLevel() {
        Properties values = new Properties();
        values.setProperty("port", "8080");
        values.setProperty("hostname", "localhost");

        ConfigFactory.create(ServerConfig.class, values);

        String said = capture.messagesAt(Level.CONFIG);
        assertTrue(said, said.contains("2 constrained properties checked by"));
        assertTrue(said, said.contains(Recorder.class.getName()));
    }
}
