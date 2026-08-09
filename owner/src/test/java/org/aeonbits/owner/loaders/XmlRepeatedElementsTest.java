/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.loaders;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Repeated sibling elements used to overwrite each other, so that
 * <code>&lt;tag&gt;a&lt;/tag&gt;&lt;tag&gt;b&lt;/tag&gt;</code> kept only <code>b</code> and lost the rest
 * without a word. They are now numbered, with the notation
 * {@link PropertyKeys#element(String, int) indexed keys} use.
 * <p>
 * The first element of a name keeps its plain key until a second one turns up, because a stream cannot look
 * ahead and giving an index to every element would rename the keys of every XML file written against this
 * library so far. These tests pin both halves of that: what a document without repetition produces, which
 * must not have changed, and what one with repetition produces, which must no longer lose anything.
 * </p>
 *
 * @author Matteo Baccan
 */
public class XmlRepeatedElementsTest {

    private static Properties read(String xml) throws IOException {
        File file = Files.createTempFile("owner-xml", ".xml").toFile();
        file.deleteOnExit();
        Writer writer = new OutputStreamWriter(Files.newOutputStream(file.toPath()), "UTF-8");
        try {
            writer.write(xml);
        } finally {
            writer.close();
        }
        Properties result = new Properties();
        new XMLLoader().load(result, file.toURI());
        return result;
    }

    // -------------------------------------------------------------------------------------------------
    // what did not change
    // -------------------------------------------------------------------------------------------------

    @Test
    public void anElementThatOccursOnceKeepsItsPlainKey() throws IOException {
        Properties props = read("<server><http><hostname>localhost</hostname></http></server>");
        assertEquals("localhost", props.getProperty("server.http.hostname"));
        assertNull(props.getProperty("server.http.hostname[0]"));
    }

    @Test
    public void attributesOfAnElementThatOccursOnceAreUnchanged() throws IOException {
        Properties props = read("<server><http port=\"80\"/></server>");
        assertEquals("80", props.getProperty("server.http.port"));
    }

    /** Different names under one parent are not repetitions of anything. */
    @Test
    public void siblingsWithDifferentNamesAreNotIndexed() throws IOException {
        Properties props = read("<server><host>a</host><port>80</port></server>");
        assertEquals("a", props.getProperty("server.host"));
        assertEquals("80", props.getProperty("server.port"));
    }

    /** The same name under different parents is not a repetition either. */
    @Test
    public void theSameNameUnderDifferentParentsIsNotIndexed() throws IOException {
        Properties props = read("<r><a><name>x</name></a><b><name>y</name></b></r>");
        assertEquals("x", props.getProperty("r.a.name"));
        assertEquals("y", props.getProperty("r.b.name"));
    }

    // -------------------------------------------------------------------------------------------------
    // what a repetition produces
    // -------------------------------------------------------------------------------------------------

    @Test
    public void twoElementsOfTheSameNameAreNumbered() throws IOException {
        Properties props = read("<r><tag>a</tag><tag>b</tag></r>");
        assertEquals("a", props.getProperty("r.tag[0]"));
        assertEquals("b", props.getProperty("r.tag[1]"));
        assertNull("the plain key is gone, its value having moved to [0]", props.getProperty("r.tag"));
    }

    @Test
    public void threeOrMoreAreNumberedOnwards() throws IOException {
        Properties props = read("<r><tag>a</tag><tag>b</tag><tag>c</tag></r>");
        assertEquals("a", props.getProperty("r.tag[0]"));
        assertEquals("b", props.getProperty("r.tag[1]"));
        assertEquals("c", props.getProperty("r.tag[2]"));
    }

    /** The first element's whole subtree moves, not only its own value. */
    @Test
    public void theAttributesOfTheFirstMoveWithIt() throws IOException {
        Properties props = read("<r><s port=\"80\">a</s><s port=\"443\">b</s></r>");
        assertEquals("80", props.getProperty("r.s[0].port"));
        assertEquals("a", props.getProperty("r.s[0]"));
        assertEquals("443", props.getProperty("r.s[1].port"));
        assertEquals("b", props.getProperty("r.s[1]"));
        assertNull(props.getProperty("r.s.port"));
    }

    @Test
    public void theChildrenOfTheFirstMoveWithItToo() throws IOException {
        Properties props = read(
                "<r><s><host>one</host></s><s><host>two</host></s></r>");
        assertEquals("one", props.getProperty("r.s[0].host"));
        assertEquals("two", props.getProperty("r.s[1].host"));
        assertNull(props.getProperty("r.s.host"));
    }

    /** A repetition inside what later turns out to be a repetition itself: both levels end up numbered. */
    @Test
    public void repetitionNestedInsideARepetitionIsHandled() throws IOException {
        Properties props = read(
                "<r><s><t>a</t><t>b</t></s><s><t>c</t></s></r>");
        assertEquals("a", props.getProperty("r.s[0].t[0]"));
        assertEquals("b", props.getProperty("r.s[0].t[1]"));
        assertEquals("c", props.getProperty("r.s[1].t"));
        assertNull(props.getProperty("r.s.t[0]"));
    }

    @Test
    public void anElementRepeatedAtTheTopOfTheDocumentIsNumbered() throws IOException {
        Properties props = read("<r><a>x</a><b><a>y</a><a>z</a></b></r>");
        assertEquals("x", props.getProperty("r.a"));
        assertEquals("y", props.getProperty("r.b.a[0]"));
        assertEquals("z", props.getProperty("r.b.a[1]"));
    }

    // -------------------------------------------------------------------------------------------------
    // the point of it: the list can now be read
    // -------------------------------------------------------------------------------------------------

    @Test
    public void whatComesOutIsWhatAListIsReadFrom() throws IOException {
        Properties props = read("<r><server>alpha</server><server>beta</server></r>");
        assertEquals("the keys are the ones IndexedProperties reads", "alpha", props.getProperty("r.server[0]"));
        assertEquals("beta", props.getProperty("r.server[1]"));
        assertEquals("and they are consecutive from zero, which it insists on", 2, props.size());
    }

    // -------------------------------------------------------------------------------------------------
    // renumbering must not reach outside the document
    // -------------------------------------------------------------------------------------------------

    /**
     * Under a MERGE policy the same {@link Properties} is handed to every source in turn, so a key of the
     * same name from another file is sitting there already. It must be left exactly where it is.
     */
    @Test
    public void aKeyFromAnotherSourceIsNotRenumbered() throws IOException {
        File file = Files.createTempFile("owner-xml", ".xml").toFile();
        file.deleteOnExit();
        Writer writer = new OutputStreamWriter(Files.newOutputStream(file.toPath()), "UTF-8");
        try {
            writer.write("<r><tag>a</tag><tag>b</tag></r>");
        } finally {
            writer.close();
        }

        Properties shared = new Properties();
        shared.setProperty("r.tag", "from another file");
        shared.setProperty("r.tag.detail", "also from another file");
        new XMLLoader().load(shared, file.toURI());

        assertEquals("from another file", shared.getProperty("r.tag"));
        assertEquals("also from another file", shared.getProperty("r.tag.detail"));
        assertEquals("a", shared.getProperty("r.tag[0]"));
        assertEquals("b", shared.getProperty("r.tag[1]"));
    }
}
