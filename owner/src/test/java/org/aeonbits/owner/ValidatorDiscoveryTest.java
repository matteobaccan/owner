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
import org.aeonbits.owner.validation.Required;
import org.aeonbits.owner.validation.Violation;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The discovery half of {@link ValidationSupport}: a {@link ConfigValidator} is found on the class path
 * through {@link java.util.ServiceLoader}, which is what the interface's own javadoc promises and what
 * every other test avoided by installing a validator through the test seam instead.
 * <p>
 * The class path a suite runs on is the one thing a test about class path discovery cannot arrange, so it
 * is arranged here: a directory holding <code>META-INF/services/…ConfigValidator</code> is put in front of
 * a class loader, and that loader is made the thread's context class loader — which is where
 * <code>discover()</code> looks first. What is asserted is not that the file was read but that <b>the
 * validator named in it decided the outcome</b>: the configuration is refused, by a violation only that
 * class could have produced.
 * </p>
 *
 * @author Matteo Baccan
 */
public class ValidatorDiscoveryTest {

    /** Announced through the services file below, and found by nothing else. */
    public static class DiscoveredValidator implements ConfigValidator {

        static final List<String> asked = new ArrayList<>();

        /** How many of these the service loader built, which is the only witness of how often it ran. */
        static final AtomicInteger built = new AtomicInteger();

        public DiscoveredValidator() {
            built.incrementAndGet();
        }

        @Override
        public List<Violation> validate(List<ConstrainedProperty> properties) {
            List<Violation> violations = new ArrayList<>();
            for (ConstrainedProperty property : properties) {
                asked.add(property.key());
                if (property.method().getAnnotation(Required.class) != null && property.value() == null)
                    violations.add(new Violation(property.key(), property.method().getName(),
                            "was found on the class path and says no"));
            }
            return violations;
        }
    }

    public interface NeedsSomething extends Config {
        @Required
        String mandatory();
    }

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private ClassLoader previous;

    @After
    public void putTheClassPathBack() {
        if (previous != null)
            Thread.currentThread().setContextClassLoader(previous);
        // JVM-wide, so it is undone whatever happened: the next configuration looks at the real class path
        ValidationSupport.forget();
        DiscoveredValidator.asked.clear();
        DiscoveredValidator.built.set(0);
    }

    /** A directory that announces the validator, in front of the class loader this test already has. */
    private void announce(Class<? extends ConfigValidator> validator) throws Exception {
        File services = folder.newFolder("META-INF", "services");
        Files.write(new File(services, ConfigValidator.class.getName()).toPath(),
                validator.getName().getBytes(StandardCharsets.ISO_8859_1));

        previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(new URLClassLoader(
                new URL[]{folder.getRoot().toURI().toURL()}, getClass().getClassLoader()));
        ValidationSupport.forget();
    }

    @Test
    public void aValidatorAnnouncedOnTheClassPathIsTheOneThatChecks() throws Exception {
        announce(DiscoveredValidator.class);

        try {
            ConfigFactory.create(NeedsSomething.class);
            fail("the discovered validator refuses this configuration");
        } catch (ConfigValidationException refused) {
            assertTrue(refused.getMessage(), refused.getMessage().contains("was found on the class path"));
        }

        assertEquals("and it was asked about the constrained property", 1, DiscoveredValidator.asked.size());
        assertEquals("mandatory", DiscoveredValidator.asked.get(0));
    }

    /**
     * And it is looked for <b>once</b>: the field is filled on the first configuration that needs it and
     * kept, because {@link java.util.ServiceLoader} reads a resource and builds a class every time it is
     * asked, while configurations are created wherever an application feels like it.
     */
    @Test
    public void theClassPathIsReadOnceAndNotOncePerConfiguration() throws Exception {
        announce(DiscoveredValidator.class);

        for (int i = 0; i < 3; i++)
            try {
                ConfigFactory.create(NeedsSomething.class);
                fail("refused every time");
            } catch (ConfigValidationException expected) {
                // the point is not the refusal here, it is what was asked below
            }

        assertEquals("three configurations, three questions", 3, DiscoveredValidator.asked.size());
        assertEquals("and one validator built, because the class path was read once",
                1, DiscoveredValidator.built.get());
    }
}
