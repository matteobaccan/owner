/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.Config.Mandatory;
import org.junit.Test;

import java.util.HashSet;
import java.util.Properties;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for the {@link Config.Mandatory} annotation, at method and interface level, covering both the
 * validation performed when the Config object is created and the one performed on property access.
 *
 * @author Matteo Baccan
 */
public class MandatoryTest {

    interface WithMandatoryMethods extends Config {
        @Mandatory
        String required();

        @Mandatory
        @Key("also.required")
        String alsoRequired();

        String optional();
    }

    @Test
    public void creationShouldFailListingAllTheMissingProperties() {
        try {
            ConfigFactory.create(WithMandatoryMethods.class);
            fail("MissingMandatoryPropertyException is expected");
        } catch (MissingMandatoryPropertyException e) {
            assertEquals(new HashSet<>(asList("required", "also.required")),
                    new HashSet<>(e.getKeys()));
            assertTrue(e.getMessage().startsWith("Missing mandatory properties: "));
            assertTrue(e.getMessage().contains("'required'"));
            assertTrue(e.getMessage().contains("'also.required'"));
        }
    }

    @Test
    public void creationShouldSucceedWhenMandatoryPropertiesAreProvided() {
        WithMandatoryMethods cfg = ConfigFactory.create(WithMandatoryMethods.class, new Properties() {{
            setProperty("required", "foo");
            setProperty("also.required", "bar");
        }});
        assertEquals("foo", cfg.required());
        assertEquals("bar", cfg.alsoRequired());
        assertNull(cfg.optional());
    }

    interface WithMandatoryDefault extends Config {
        @Mandatory
        @DefaultValue("fallback")
        String withDefault();
    }

    @Test
    public void mandatoryIsSatisfiedByDefaultValue() {
        WithMandatoryDefault cfg = ConfigFactory.create(WithMandatoryDefault.class);
        assertEquals("fallback", cfg.withDefault());
    }

    @Mandatory
    interface AllMandatory extends Config {
        String first();

        String second();
    }

    @Test
    public void interfaceLevelAnnotationMakesAllDeclaredPropertiesMandatory() {
        try {
            ConfigFactory.create(AllMandatory.class);
            fail("MissingMandatoryPropertyException is expected");
        } catch (MissingMandatoryPropertyException e) {
            assertEquals(new HashSet<>(asList("first", "second")), new HashSet<>(e.getKeys()));
        }
    }

    @Test
    public void interfaceLevelAnnotationIsSatisfiedWhenPropertiesAreProvided() {
        AllMandatory cfg = ConfigFactory.create(AllMandatory.class, new Properties() {{
            setProperty("first", "1");
            setProperty("second", "2");
        }});
        assertEquals("1", cfg.first());
        assertEquals("2", cfg.second());
    }

    interface SubOfAllMandatory extends AllMandatory {
    }

    @Test
    public void interfaceLevelAnnotationIsInheritedBySubInterfaces() {
        try {
            ConfigFactory.create(SubOfAllMandatory.class);
            fail("MissingMandatoryPropertyException is expected");
        } catch (MissingMandatoryPropertyException e) {
            assertEquals(new HashSet<>(asList("first", "second")), new HashSet<>(e.getKeys()));
        }
    }

    interface MixedMandatory extends AllMandatory {
        String extra();
    }

    @Test
    public void propertiesDeclaredOutsideTheAnnotatedInterfaceAreNotMandatory() {
        MixedMandatory cfg = ConfigFactory.create(MixedMandatory.class, new Properties() {{
            setProperty("first", "1");
            setProperty("second", "2");
        }});
        assertNull(cfg.extra());
    }

    interface MutableMandatory extends Config, Mutable {
        @Mandatory
        @DefaultValue("initial")
        String key();
    }

    @Test
    public void accessShouldFailWhenAMandatoryPropertyIsRemovedAfterCreation() {
        MutableMandatory cfg = ConfigFactory.create(MutableMandatory.class);
        assertEquals("initial", cfg.key());

        cfg.removeProperty("key");
        try {
            cfg.key();
            fail("MissingMandatoryPropertyException is expected");
        } catch (MissingMandatoryPropertyException e) {
            assertEquals(asList("key"), e.getKeys());
            assertEquals("Missing mandatory property: 'key'", e.getMessage());
        }
    }

    interface ParameterizedMandatory extends Config {
        @Mandatory
        String greeting(String name);
    }

    @Test
    public void methodsWithParametersAreSkippedAtCreationAndCheckedOnAccess() {
        // creation succeeds: the key of a parameterized method may depend on the arguments
        ParameterizedMandatory cfg = ConfigFactory.create(ParameterizedMandatory.class);
        try {
            cfg.greeting("world");
            fail("MissingMandatoryPropertyException is expected");
        } catch (MissingMandatoryPropertyException e) {
            assertEquals(asList("greeting"), e.getKeys());
        }
    }

    @Mandatory
    interface MandatoryRedeclaringDelegate extends Config, Reloadable {
        @Override
        void reload(); // redeclared from Reloadable: delegated methods are not properties

        @DefaultValue("present")
        String value();
    }

    @Test
    public void delegatedMethodsAreNotValidatedAsProperties() {
        MandatoryRedeclaringDelegate cfg = ConfigFactory.create(MandatoryRedeclaringDelegate.class);
        assertEquals("present", cfg.value());
        cfg.reload();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void missingKeysListIsUnmodifiable() {
        try {
            ConfigFactory.create(AllMandatory.class);
            fail("MissingMandatoryPropertyException is expected");
        } catch (MissingMandatoryPropertyException e) {
            e.getKeys().add("other");
        }
    }
}
