/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Long-standing requests that 2.0.0 answers, each closed on the example below rather than on an
 * assertion — so that a change of behaviour breaks a test instead of quietly reopening an issue.
 * <p>
 * Every method here is titled with the issue it discharges. They live together because what they have in
 * common is their reason for existing, not their subject.
 * </p>
 *
 * @author Matteo Baccan
 */
public class AnsweredByTwoPointZeroTest {

    // ------------------------------------------------------------------ #85 runtime interpolation

    public interface Interpolating extends Config, Accessible {
        @Config.DefaultValue("/root")
        String rootPath();
    }

    /**
     * <a href="https://github.com/matteobaccan/owner/issues/85">#85</a> asked for an
     * <code>interpolate(String)</code> that expands <code>${...}</code> against the configuration at run
     * time. It needs no method of its own: since 2.0.0 <code>getProperty(key, default)</code> expands
     * <b>the default the caller supplies</b>, and a key that does not exist makes that the answer.
     */
    @Test
    public void issue85_aStringIsInterpolatedAgainstTheConfigurationAtRuntime() {
        Interpolating config = ConfigFactory.create(Interpolating.class);

        assertEquals("/root/some/other/text",
                config.getProperty("no.such.key", "${rootPath}/some/other/text"));
    }

    /**
     * And it reads the configuration as it stands rather than as it was declared, which is what makes it
     * interpolation and not string concatenation with extra steps.
     */
    @Test
    public void issue85_theInterpolationSeesAValueThatWasOverridden() {
        Properties overrides = new Properties();
        overrides.setProperty("rootPath", "/somewhere/else");
        Interpolating config = ConfigFactory.create(Interpolating.class, overrides);

        assertEquals("/somewhere/else/here", config.getProperty("absent", "${rootPath}/here"));
    }

    // ------------------------------------------------------------------ #87 nulls in arrays

    /** Refuses anything starting with "bad", so a list can be made to fail at a chosen position. */
    public static class Refuses {
        private final String value;

        public Refuses(String value) {
            if (value.startsWith("bad"))
                throw new IllegalArgumentException("refused: " + value);
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    public interface WithLists extends Config {
        @Config.DefaultValue("bad1, ok2, ok3")
        List<Refuses> firstIsBad();

        @Config.DefaultValue("ok1, bad2, ok3")
        List<Refuses> middleIsBad();

        @Config.DefaultValue("ok1, ok2, bad3")
        List<Refuses> lastIsBad();

        @Config.DefaultValue("ok1, ok2, ok3")
        Refuses[] anArrayThatIsFine();
    }

    /**
     * <a href="https://github.com/matteobaccan/owner/issues/87">#87</a>: a conversion that failed at any
     * position other than the first used to be turned into a <code>null</code> element, so a malformed
     * value in the middle of a list was silently dropped. Every position now refuses alike, and the
     * message names the element and the property.
     */
    @Test
    public void issue87_aBadElementIsRefusedWhereverItIsInTheList() {
        WithLists config = ConfigFactory.create(WithLists.class);

        refuses("bad1", new Runnable() {
            public void run() {
                config.firstIsBad();
            }
        });
        refuses("bad2", new Runnable() {
            public void run() {
                config.middleIsBad();
            }
        });
        refuses("bad3", new Runnable() {
            public void run() {
                config.lastIsBad();
            }
        });
    }

    /** And a list with nothing wrong in it is not disturbed by any of that. */
    @Test
    public void issue87_aListThatConvertsCleanlyHasNoNullsInIt() {
        Refuses[] values = ConfigFactory.create(WithLists.class).anArrayThatIsFine();

        assertEquals(3, values.length);
        for (Refuses each : values)
            assertNotNull("no element is null", each);
    }

    private void refuses(String element, Runnable call) {
        try {
            call.run();
            fail("the element '" + element + "' should have been refused rather than turned into a null");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(element));
        }
    }

    // ------------------------------------------------------------------ #72, #132, #153 composites

    public static class DataSource {
        final String url;
        final String user;

        DataSource(String url, String user) {
            this.url = url;
            this.user = user;
        }
    }

    /**
     * <a href="https://github.com/matteobaccan/owner/issues/72">#72</a> asked for an annotation that
     * builds one object out of several keys, and the objection recorded in that thread was that a
     * {@link Converter} cannot read the other properties. A <code>default</code> method can: it is on the
     * interface, so it calls the mapping methods directly, and it needs no annotation and no SPI.
     */
    public interface DbConfig extends Config {
        @Config.DefaultValue("jdbc:h2:mem:test")
        String jdbcUrl();

        @Config.DefaultValue("scott")
        String jdbcUser();

        default DataSource dataSource() {
            return new DataSource(jdbcUrl(), jdbcUser());
        }
    }

    @Test
    public void issue72_aDefaultMethodBuildsOneObjectOutOfSeveralKeys() {
        DataSource built = ConfigFactory.create(DbConfig.class).dataSource();

        assertEquals("jdbc:h2:mem:test", built.url);
        assertEquals("scott", built.user);
    }

    // ------------------------------------------------------------------ #132 an object out of nested XML

    public static class Person {
        final String name;
        final Integer age;

        Person(String name, Integer age) {
            this.name = name;
            this.age = age;
        }
    }

    /**
     * <a href="https://github.com/matteobaccan/owner/issues/132">#132</a> asked for a <code>Person</code>
     * built out of a nested XML block, and was answered in 2015 with "at the moment this cannot be done
     * with existing features". Nested interfaces arrived in 2.0.0 and it can.
     * <p>
     * <b>The part worth writing down</b> is the {@link Config.Prefix}: {@link org.aeonbits.owner.loaders.XMLLoader}
     * keys every element by its whole path, and the document element counts — the XML below produces
     * <code>properties.person.name</code>, not <code>person.name</code>. Without the prefix every method
     * answers <code>null</code>, which looks like nesting being broken and is not.
     * </p>
     */
    @Config.Prefix("properties.")
    @Config.Sources("file:${test.person.xml}")
    public interface WithAPersonInIt extends Config {

        interface PersonSection extends Config {
            String name();

            Integer age();
        }

        PersonSection person();

        default Person asPojo() {
            return new Person(person().name(), person().age());
        }
    }

    @Test
    public void issue132_anObjectIsBuiltOutOfANestedXmlBlock() throws Exception {
        File xml = File.createTempFile("person", ".xml");
        try {
            Files.write(xml.toPath(), ("<properties>\n"
                    + "  <person>\n"
                    + "    <name>Vas</name>\n"
                    + "    <age>22</age>\n"
                    + "  </person>\n"
                    + "</properties>\n").getBytes(StandardCharsets.UTF_8));
            ConfigFactory.setProperty("test.person.xml", xml.getAbsolutePath().replace('\\', '/'));

            WithAPersonInIt config = ConfigFactory.create(WithAPersonInIt.class);

            assertEquals("the section reads the nested block", "Vas", config.person().name());
            assertEquals(Integer.valueOf(22), config.person().age());

            Person built = config.asPojo();
            assertEquals("and a default method turns it into the object #132 asked for", "Vas", built.name);
            assertEquals(Integer.valueOf(22), built.age);
        } finally {
            ConfigFactory.clearProperty("test.person.xml");
            Files.deleteIfExists(xml.toPath());
        }
    }

    /** And it composes what the configuration actually holds, not what was written in the annotations. */
    @Test
    public void issue72_theCompositeSeesTheOverriddenValues() {
        Properties overrides = new Properties();
        overrides.setProperty("jdbcUrl", "jdbc:postgresql://db/prod");
        overrides.setProperty("jdbcUser", "prod");

        DataSource built = ConfigFactory.create(DbConfig.class, overrides).dataSource();

        assertEquals("jdbc:postgresql://db/prod", built.url);
        assertEquals("prod", built.user);
    }
}
