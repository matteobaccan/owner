/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@link Preprocessor#processAbsent(String)} — what a preprocessor does when the property is not there,
 * which is <a href="https://github.com/matteobaccan/owner/issues/188">#188</a>.
 * <p>
 * The issue asked for two things that turned out to be one: run the preprocessor when there is no value,
 * <i>and</i> tell it which property it is. They are not separable — a preprocessor that learned only of
 * the absence could answer with a constant, and a constant is what {@link Config.DefaultValue} already
 * is. So the key comes with the call.
 * </p>
 * <p>
 * The half of #188 that is <b>not</b> here is the one it opened with: making a property required. That is
 * {@link Config.Mandatory}, which arrived in the meantime and needs no preprocessor at all —
 * see {@link #issue188_forMerelyRequiringAValueMandatoryIsTheAnswerAndNeedsNoPreprocessor()}.
 * </p>
 *
 * @author Matteo Baccan
 */
public class PreprocessorOnAbsenceTest {

    /** The preprocessor written in #188, which never ran because the property had no value. */
    public static class NullChecker implements Preprocessor {
        static final List<String> calls = new ArrayList<>();

        @Override
        public String process(String input) {
            calls.add("process(" + input + ")");
            return input;
        }

        @Override
        public String processAbsent(String key) {
            calls.add("processAbsent(" + key + ")");
            return Objects.requireNonNull(null, "Property is null: " + key);
        }
    }

    public interface Conf extends Config {
        @Config.PreprocessorClasses(NullChecker.class)
        String p1();
    }

    /**
     * The example from the issue, now doing what it was written to do — including the part that was asked
     * for separately: the message names the property, which <code>process(String)</code> could not have.
     */
    @Test
    public void issue188_thePreprocessorRunsWhenThereIsNoValueAndKnowsWhichPropertyItIs() {
        NullChecker.calls.clear();
        Conf conf = ConfigFactory.create(Conf.class);

        try {
            conf.p1();
            fail("the preprocessor should have refused the absence");
        } catch (NullPointerException expected) {
            assertEquals("Property is null: p1", expected.getMessage());
        }
        assertEquals("[processAbsent(p1)]", NullChecker.calls.toString());
    }

    // ------------------------------------------------------------------ supplying a value

    /** Stands in for a vault, a lookup service, or anything the library does not know about. */
    public static class SuppliesFromElsewhere implements Preprocessor {
        @Override
        public String process(String input) {
            return input;
        }

        @Override
        public String processAbsent(String key) {
            return "supplied-for-" + key;
        }
    }

    public static class Shouts implements Preprocessor {
        @Override
        public String process(String input) {
            return input.toUpperCase();
        }
    }

    public interface Supplied extends Config {
        @Config.PreprocessorClasses(SuppliesFromElsewhere.class)
        String absent();

        @Config.PreprocessorClasses(SuppliesFromElsewhere.class)
        @Config.DefaultValue("from-the-annotation")
        String hasADefault();

        @Config.PreprocessorClasses({SuppliesFromElsewhere.class, Shouts.class})
        String suppliedThenProcessed();

    }

    /** Supplies something that is not a String, to show the conversion still happens afterwards. */
    public static class SuppliesANumber implements Preprocessor {
        @Override
        public String process(String input) {
            return input;
        }

        @Override
        public String processAbsent(String key) {
            return "8080";
        }
    }

    public interface Typed extends Config {
        @Config.PreprocessorClasses(SuppliesANumber.class)
        int port();
    }

    /** A supplied value is converted to the return type like any other, rather than staying a String. */
    @Test
    public void whatIsSuppliedIsConvertedToTheReturnType() {
        assertEquals(8080, ConfigFactory.create(Typed.class).port());
    }

    @Test
    public void aPreprocessorCanSupplyTheValueOfAnAbsentProperty() {
        assertEquals("supplied-for-absent", ConfigFactory.create(Supplied.class).absent());
    }

    /** It is only asked when the property really is absent; a default is a value like any other. */
    @Test
    public void itIsNotAskedWhenThereIsADefaultValue() {
        assertEquals("from-the-annotation", ConfigFactory.create(Supplied.class).hasADefault());
    }

    /**
     * What one preprocessor supplies, the next one processes — the chain does not stop at whoever
     * answered. So a supplied value is treated exactly like one read from a file.
     */
    @Test
    public void whatIsSuppliedGoesOnThroughTheRestOfTheChain() {
        assertEquals("SUPPLIED-FOR-SUPPLIEDTHENPROCESSED",
                ConfigFactory.create(Supplied.class).suppliedThenProcessed());
    }

    /** And it is not sent back through the preprocessor that supplied it, which would double-process it. */
    public static class SuppliesThenWouldDouble implements Preprocessor {
        @Override
        public String process(String input) {
            return input + "+processed";
        }

        @Override
        public String processAbsent(String key) {
            return "supplied";
        }
    }

    public interface NotTwice extends Config {
        @Config.PreprocessorClasses(SuppliesThenWouldDouble.class)
        String value();
    }

    @Test
    public void whatIsSuppliedIsNotSentBackThroughTheOneThatSuppliedIt() {
        assertEquals("supplied", ConfigFactory.create(NotTwice.class).value());
    }

    // ------------------------------------------------------------------ nothing already written changes

    /** Counts every call, and would blow up on a null exactly as a preprocessor written in 1.x would. */
    public static class WrittenBeforeTwoPointZero implements Preprocessor {
        static final List<String> seen = new ArrayList<>();

        @Override
        public String process(String input) {
            seen.add(input);
            return input.trim();
        }
    }

    public interface Legacy extends Config {
        @Config.PreprocessorClasses(WrittenBeforeTwoPointZero.class)
        String absent();

        @Config.PreprocessorClasses(WrittenBeforeTwoPointZero.class)
        @Config.DefaultValue("  padded  ")
        String present();
    }

    /**
     * The compatibility that decided the design: a preprocessor that does not override
     * {@link Preprocessor#processAbsent} is <b>never handed a null</b>. Everything written against 1.x
     * calls methods on its input without checking, and would have started throwing on any absent
     * property the day this shipped.
     */
    @Test
    public void aPreprocessorThatDidNotOptInIsNeverCalledWithNull() {
        WrittenBeforeTwoPointZero.seen.clear();
        Legacy config = ConfigFactory.create(Legacy.class);

        assertNull("the property is still absent", config.absent());
        assertEquals("and nothing was handed to the preprocessor", 0, WrittenBeforeTwoPointZero.seen.size());

        assertEquals("padded", config.present());
        assertEquals("[  padded  ]", WrittenBeforeTwoPointZero.seen.toString());
    }

    /** {@link Preprocessor} stays a functional interface, so a lambda is still a preprocessor. */
    @Test
    public void aLambdaIsStillAPreprocessor() {
        Preprocessor lambda = input -> input + "!";

        assertEquals("hello!", lambda.process("hello"));
        assertNull("and it declines an absence, which is the default", lambda.processAbsent("any.key"));
    }

    // ------------------------------------------------------------------ how it meets @Mandatory

    // one interface each: @Mandatory is checked when the configuration is created, for the whole
    // interface at once, so a single missing property would stop the others being created at all
    public interface RequiredAndSupplied extends Config {
        @Config.Mandatory
        @Config.PreprocessorClasses(SuppliesFromElsewhere.class)
        String supplied();
    }

    public interface RequiredAndNobodySupplies extends Config {
        @Config.Mandatory
        @Config.PreprocessorClasses(WrittenBeforeTwoPointZero.class)
        String nobodySupplies();
    }

    public interface RequiredPlain extends Config {
        @Config.Mandatory
        String plain();
    }

    /**
     * A preprocessor that supplies a value <b>satisfies</b> {@link Config.Mandatory} rather than racing
     * it — and this is the assertion that costs something, because {@link Config.Mandatory} is checked
     * when the configuration is <b>created</b>, against the sources. Without asking the preprocessors
     * there, the two features would contradict each other: the value is available and startup fails
     * anyway. Creating the object at all is half of what is under test here.
     */
    @Test
    public void aSuppliedValueSatisfiesMandatoryIncludingTheCheckMadeAtCreation() {
        RequiredAndSupplied config = ConfigFactory.create(RequiredAndSupplied.class);

        assertEquals("supplied-for-supplied", config.supplied());
    }

    /** And when nothing supplies one, it throws as it always did, naming the key. */
    @Test
    public void mandatoryStillThrowsWhenNothingSuppliesAValue() {
        try {
            ConfigFactory.create(RequiredAndNobodySupplies.class).nobodySupplies();
            fail("a mandatory property that nothing supplied should throw");
        } catch (MissingMandatoryPropertyException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("nobodySupplies"));
        }
    }

    /**
     * The half of #188 that needs none of this. The issue opened by asking for null checks on required
     * properties; that is {@link Config.Mandatory}, it arrived in the meantime, and it names the key in
     * the exception — which is the other thing #188 asked for.
     */
    @Test
    public void issue188_forMerelyRequiringAValueMandatoryIsTheAnswerAndNeedsNoPreprocessor() {
        try {
            ConfigFactory.create(RequiredPlain.class).plain();
            fail("a mandatory property with no value should throw");
        } catch (MissingMandatoryPropertyException expected) {
            assertTrue("the message names the property", expected.getMessage().contains("plain"));
        }
    }
}
