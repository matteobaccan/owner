/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.validation;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Recognising a constraint <b>by name</b>, which is the whole of what the dependency-free core can do about
 * validation and the whole of what it needs to do: everything else follows from being able to say "this
 * annotation is a check, and it is not being checked".
 *
 * <p>
 * The constraints used here are declared in this package, and so is the
 * {@link javax.validation.Constraint} they are annotated with - see that class for why the core tests do not
 * borrow a real one.
 * </p>
 *
 * @author Matteo Baccan
 */
public class ConstraintsTest {

    interface Shapes {
        @AtLeast(12)
        int onTheMethod();

        @Required
        String underTheOtherName();

        @AtLeast.List({@AtLeast(12), @AtLeast(20)})
        int repeated();

        Optional<@AtLeast(12) Integer> insideAnOptional();

        List<@AtLeast(12) Integer> insideAList();

        Map<String, List<@AtLeast(12) Integer>> twoLevelsDown();

        @AtLeast(12) Optional<Integer> onTheContainer();

        @Decorative
        int annotatedButNotConstrained();

        int plain();

        @AtLeast(12) int[] anArray();
    }

    private static Method method(String name) {
        for (Method candidate : Shapes.class.getMethods())
            if (candidate.getName().equals(name))
                return candidate;
        throw new AssertionError("no method called " + name);
    }

    @Test
    public void aConstraintOnTheMethodIsFound() {
        assertTrue(Constraints.anyOn(method("onTheMethod")));
        assertTrue(Constraints.anyDeclaredOn(method("onTheMethod")));
    }

    /** The rename is the compatibility question this feature had to answer, so both names are checked. */
    @Test
    public void bothNamesOfTheSameAnnotationAreRecognised() {
        assertTrue(Constraints.anyOn(method("underTheOtherName")));
    }

    /**
     * <code>&#64;AtLeast.List</code>, which is how a repeated constraint arrives. The container carries no
     * <code>&#64;Constraint</code> of its own, so a rule about the annotation alone would miss it and a
     * method carrying two bounds would look unannotated.
     */
    @Test
    public void theContainerOfARepeatedConstraintIsFound() {
        assertTrue(Constraints.anyOn(method("repeated")));
    }

    /**
     * The one that is easy to miss: a constraint inside the angle brackets is not among the method's
     * annotations at all, and <code>Optional&lt;&#64;AtLeast(12) Integer&gt;</code> is precisely the spelling
     * this library tells people to use.
     */
    @Test
    public void aConstraintOnATypeArgumentIsFound() {
        assertTrue(Constraints.anyOn(method("insideAnOptional")));
        assertTrue(Constraints.anyOn(method("insideAList")));
        assertTrue("two levels of generics", Constraints.anyOn(method("twoLevelsDown")));
        assertTrue("the component of an array", Constraints.anyOn(method("anArray")));
    }

    /**
     * Told apart from the one above, because they are reported differently: a constraint on a method
     * returning an <code>Optional</code> applies to the container, which is never absent.
     */
    @Test
    public void aConstraintOnTheContainerIsNotOneOnTheValue() {
        assertTrue(Constraints.anyOn(method("onTheContainer")));
        assertTrue(Constraints.anyDeclaredOn(method("onTheContainer")));
        assertFalse("this one is on the value, not on the container",
                Constraints.anyDeclaredOn(method("insideAnOptional")));
    }

    /** A configuration interface is covered in annotations, and almost none of them is a constraint. */
    @Test
    public void anAnnotationThatIsNotAConstraintIsNotOne() {
        assertFalse(Constraints.anyOn(method("annotatedButNotConstrained")));
        assertFalse(Constraints.anyOn(method("plain")));
    }
}
