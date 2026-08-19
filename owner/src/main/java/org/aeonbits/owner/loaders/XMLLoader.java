/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.loaders;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.ext.DefaultHandler2;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.aeonbits.owner.util.Util.unsupported;

/**
 * A {@link Loader loader} able to read properties from standard XML Java properties files, as well as user defined
 * XML properties files.
 *
 * @since 1.0.5
 * @author Luigi R. Viggiano
 */
public class XMLLoader implements Loader {
    /**
     * A loader is built with no arguments, both when it is registered by hand and when
     * {@link java.util.ServiceLoader} finds it on the class path. Declared rather than left implicit
     * so that the requirement is visible to whoever changes this class.
     */
    public XMLLoader() {
    }


    private static final long serialVersionUID = -894351666332018767L;

    /** The one place the name of the format is written: {@code accept} and the default spec both read it. */
    private static final String SUFFIX = ".xml";

    /**
     * The option that holds a document to the grammar it declares; see {@link #load(Properties, URI)}.
     */
    private static final String VALIDATE = "validate";

    private transient SAXParserFactory validating = null;
    private transient SAXParserFactory reading = null;

    /**
     * Whether the parser is asked to validate is a setting of the <b>factory</b> and not of a single parse,
     * so there are two of them rather than one. Both are built once and kept: a factory is expensive and a
     * configuration is read again at every reload.
     */
    private synchronized SAXParserFactory factory(boolean validate) {
        if (validate) {
            if (validating == null)
                validating = newFactory(true);
            return validating;
        }
        if (reading == null)
            reading = newFactory(false);
        return reading;
    }

    /** The two features that close XXE. Named here because they are set twice: directly, and by name. */
    private static final String EXTERNAL_ENTITIES = "http://xml.org/sax/features/external-general-entities";
    private static final String EXTERNAL_PARAMETERS = "http://xml.org/sax/features/external-parameter-entities";

    /**
     * Builds a parser factory with the hardening this class is documented to give.
     * <p>
     * The internal Java properties DTD still works, its DOCTYPE being intercepted by
     * {@link XmlToPropsHandler#resolveEntity}, but external DTDs and external entities are neutralized and
     * secure processing caps entity expansion, which is the billion laughs.
     * </p>
     * <p>
     * <b>The two features that close XXE are set here rather than through {@link #setFeature}</b>, and it
     * is worth a word since the four lines then read unevenly. An external entity is the one thing in this
     * class that can reach a file off the machine, and both a reader and a static analyser should be able
     * to see it being closed on the factory as the factory is built, without following a helper to find
     * out whether it happens at all. The helper still handles the two that harden without closing that
     * door.
     * </p>
     * <p>
     * They are two blocks rather than one because a parser that refuses the first must still be asked for
     * the second, and each has to be named on its own when it is refused - which is what the helper does
     * and what a single block would have quietly dropped.
     * </p>
     */
    private static SAXParserFactory newFactory(boolean validate) {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setValidating(validate);
        factory.setNamespaceAware(true);
        try {
            factory.setFeature(EXTERNAL_ENTITIES, false);
        } catch (ParserConfigurationException | SAXException refused) {
            reportUnavailable(EXTERNAL_ENTITIES, refused);
        }
        try {
            factory.setFeature(EXTERNAL_PARAMETERS, false);
        } catch (ParserConfigurationException | SAXException refused) {
            reportUnavailable(EXTERNAL_PARAMETERS, refused);
        }
        setFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        setFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return factory;
    }

    /**
     * Asks the parser for one of the hardening features, and says so when it will not have it.
     * <p>
     * A parser that does not know a feature cannot be made to honour it, and there is nothing to do but carry
     * on: refusing to read XML at all because one switch is missing would be worse than reading it. But the
     * protection this class is documented to give is then not there, and silence would leave a deployment
     * believing in a defence it does not have. Which feature was refused is named, because the three are not
     * equally serious.
     * </p>
     */
    static void setFeature(SAXParserFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (ParserConfigurationException | SAXException refused) {
            reportUnavailable(feature, refused);
        }
    }

    private static void reportUnavailable(String feature, Exception cause) {
        Logger.getLogger(XMLLoader.class.getName()).log(Level.WARNING, cause, () -> String.format(
                "The XML parser in use does not support '%s', so that hardening is not in force for XML "
                        + "configuration read by OWNER. An XML source from an untrusted place could then "
                        + "reach an external entity or expand entities without limit. Placing a parser that "
                        + "supports it on the classpath restores the protection.", feature));
    }

    static class XmlToPropsHandler extends DefaultHandler2 {

        private static final String PROPS_DTD_URI =
                "http://java.sun.com/dtd/properties.dtd";

        private static final String PROPS_DTD =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                        "<!-- DTD for properties -->" +
                        "<!ELEMENT properties ( comment?, entry* ) >" +
                        "<!ATTLIST properties version CDATA #FIXED \"1.0\">" +
                        "<!ELEMENT comment (#PCDATA) >" +
                        "<!ELEMENT entry (#PCDATA) >" +
                        "<!ATTLIST entry key CDATA #REQUIRED>";

        private boolean isJavaPropertiesFormat = false;

        /**
         * Whether the document declares a grammar of its own, which is what decides whether a validity
         * error is a real one. See {@link #error(SAXParseException)}.
         */
        private boolean declaresAGrammar = false;

        /**
         * Whether the grammar it declares is one this parser was allowed to read. An external DTD is
         * neutralized by the hardening above, so the document declares a grammar that never arrives.
         */
        private boolean grammarWasNeutralized = false;

        private final Properties props;
        private final Stack<String> paths = new Stack<>();
        private final Stack<StringBuilder> value = new Stack<>();

        /**
         * How many times each key has been arrived at, so that the second element of a name already seen
         * under the same parent can be told it is one of several.
         */
        private final Map<String, Integer> occurrences = new HashMap<>();

        /**
         * The document's own <code>DOCTYPE</code>, read here rather than inferred from anything else.
         * <p>
         * It says two things. That the document <b>declares a grammar</b>, which is what tells a real
         * validity error from the one a parser reports for every document that has none. And which format
         * this is: the Java properties one is recognised by its DTD, and recognising it here rather than in
         * {@link #resolveEntity} keeps it working when the parser is not validating and therefore never
         * asks for the DTD at all.
         * </p>
         */
        @Override
        public void startDTD(String name, String publicId, String systemId) throws SAXException {
            declaresAGrammar = true;
            if (PROPS_DTD_URI.equals(systemId))
                isJavaPropertiesFormat = true;
            super.startDTD(name, publicId, systemId);
        }

        @Override
        public InputSource resolveEntity(String name, String publicId, String baseURI,
                                         String systemId) throws SAXException, IOException {
            if (PROPS_DTD_URI.equals(systemId)) {
                isJavaPropertiesFormat = true;
                InputSource inputSource = new InputSource(new StringReader(PROPS_DTD));
                inputSource.setSystemId(PROPS_DTD_URI);
                return inputSource;
            }
            // Any other external entity/DTD is neutralized by returning an empty
            // source instead of null (which would let the parser fetch it).
            grammarWasNeutralized = true;
            return new InputSource(new StringReader(""));
        }

        /**
         * Reads into a map of its own, which the loader merges into the caller's once the document is
         * finished.
         * <p>
         * It matters because of the renumbering. Under a MERGE policy the same {@link Properties} is handed
         * to every source in turn, so a key this document is about to write may already hold another file's
         * value; writing straight into it would make that value visible to the renumbering, which would
         * carry it off to <code>[0]</code> along with its own. Building separately makes the contribution of
         * a source what it would have been had it been read alone, which is the only reading of it that can
         * be explained.
         * </p>
         */
        public XmlToPropsHandler() {
            this.props = new Properties();
        }

        /** What the document said, once it has all been read. */
        Properties properties() {
            return props;
        }

        @Override
        public void startElement(String uri, String localName, String qName,
                                 Attributes attributes) throws SAXException {
            value.push(new StringBuilder());

            if (isJavaPropertiesFormat) {
                if ("entry".equals(qName))
                    paths.push(attributes.getValue("key"));
                else
                    paths.push(qName);
            } else {
                String path = pathFor(PropertyKeys.child(paths.isEmpty() ? null : paths.peek(), qName));
                paths.push(path);
                for (int i = 0; i < attributes.getLength(); i++)
                    put(PropertyKeys.child(path, attributes.getQName(i)), attributes.getValue(i));
            }
        }

        /**
         * The key for an element, given the key it would have if it were the only one of its name under its
         * parent.
         * <p>
         * A stream cannot look ahead: when the first <code>&lt;tag&gt;</code> arrives there is no telling
         * whether a second will follow, and giving every element an index on the chance of it would rename
         * every key of every XML file ever written against this library. So the first keeps the plain name,
         * and if a second turns up the first is moved under <code>[0]</code> then — by which time its
         * subtree has been read in full, so there is a subtree to move.
         * </p>
         */
        private String pathFor(String base) {
            Integer seen = occurrences.get(base);
            if (seen == null) {
                occurrences.put(base, 1);
                return base;
            }
            if (seen == 1)
                moveUnderFirstIndex(base);
            occurrences.put(base, seen + 1);
            return PropertyKeys.element(base, seen);
        }

        /** Moves everything read under {@code base} to {@code base[0]}, itself included. */
        private void moveUnderFirstIndex(String base) {
            String indexed = PropertyKeys.element(base, 0);
            String below = base + PropertyKeys.NESTING;
            for (String name : new ArrayList<>(props.stringPropertyNames())) {
                if (name.equals(base))
                    props.setProperty(indexed, (String) props.remove(name));
                else if (name.startsWith(below))
                    props.setProperty(indexed + name.substring(base.length()), (String) props.remove(name));
            }
        }

        private void put(String key, String propertyValue) {
            props.setProperty(key, propertyValue);
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            value.peek().append(new String(ch, start, length));
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            String key = paths.peek();
            String text = this.value.peek().toString();
            if (isJavaPropertiesFormat)
                putAsTheJdkWould(qName, key, text);
            else if (!text.trim().isEmpty())
                put(key, text.trim());
            value.pop();
            paths.pop();
        }

        /**
         * The Java properties format, where <b>the reference implementation is in the JDK</b>:
         * {@link Properties#loadFromXML(java.io.InputStream)} reads the same documents, so where the two
         * disagree one of them is wrong, and it is not the one in the JDK.
         * <p>
         * Two things follow, and both were found by running the same document through both.
         * <b>The text is taken as written</b>, where the other half of this loader trims: an
         * <code>&lt;entry&gt;</code> holding <code>"  spaced  "</code> is that value, spaces and all,
         * because in this format the element holds the value and nothing else — while in an XML of your
         * own the whitespace is the indentation of a pretty-printed file and has to go.
         * <b>And an entry that is empty is a property</b>: <code>&lt;entry key="a"&gt;&lt;/entry&gt;</code>
         * is the empty value, which this library treats as a state of its own — a key present and empty is
         * not a key that is absent, and only the second lets a {@code @DefaultValue} win.
         * </p>
         * <p>
         * Only <code>entry</code> carries anything. <code>comment</code> is the format's own, and
         * <code>properties</code> is the root, whose text is the whitespace between its children.
         * </p>
         */
        private void putAsTheJdkWould(String qName, String key, String text) {
            if ("entry".equals(qName) && key != null)
                put(key, text);
        }

        /**
         * A validity error, which is recoverable and therefore ours to decide about.
         * <p>
         * <b>A document is held to the grammar it declares</b>, whosever grammar it is: the Java properties
         * DTD and one written in the document's own internal subset are both a statement the file makes
         * about itself, and reading past it would hand the caller exactly the part the file says is not
         * allowed. Parsing does not stop at a recoverable error, so what would come back is not a truncated
         * document but a complete one including what its own grammar forbids.
         * </p>
         * <p>
         * A document that declares <b>no</b> grammar is a different matter, and this is why the test is
         * needed at all: asking a validating parser to read one produces a validity error for the document
         * as a whole - <i>no grammar found</i> - which says nothing about the document and everything about
         * what was asked of the parser. That one is ignored.
         * </p>
         * <p>
         * So is the same error when the grammar was declared but <b>neutralized</b>, which the hardening
         * above does to every external DTD. There the document names a grammar and this parser was not
         * allowed to fetch it, so every element in the file is undeclared as far as the parser can see. A
         * document cannot be held to a rule we refused to read.
         * </p>
         *
         * @see #VALIDATE
         */
        @Override
        public void error(SAXParseException e) throws SAXException {
            if (declaresAGrammar && !grammarWasNeutralized)
                throw e;
        }
    }

    @Override
    public boolean accept(URI uri) {
        // see PropertiesLoader.accept: a URI with no scheme makes toURL throw the wrong kind of exception,
        // and an accept() that throws stops the search for a loader rather than declining
        if (uri == null || !uri.isAbsolute())
            return false;
        try {
            uri.toURL();
            // matched through SourceOptions rather than on URL.getFile(), which includes the query: an XML
            // served over HTTP as config.xml?v=2 used to fail this test, fall through to PropertiesLoader -
            // which accepts everything it can resolve - and be read as a properties file, in silence
            return SourceOptions.hasExtension(uri, SUFFIX);
        } catch (MalformedURLException ex) {
            return false;
        }
    }

    /**
     * Reads the document, holding it to the grammar it declares.
     * <p>
     * A document carrying a <code>DOCTYPE</code> is validated against it and a violation is refused, which
     * is what the JDK does for its own properties format and what Commons Configuration does when asked to
     * validate at all. A document declaring no grammar is read as it is; there is nothing to hold it to.
     * </p>
     * <p>
     * <code>#validate=false</code> on the source turns that off — <code>file:app.xml#validate=false</code> —
     * and the document is then read whatever its own grammar says of it. It is worth having for a file that
     * is out of step with a DTD nobody maintains any more, and it is written on the source rather than
     * configured globally because it is a property of that file and not of the application.
     * </p>
     */
    @Override
    public void load(Properties result, URI uri) throws IOException {
        SourceOptions options = SourceOptions.of(uri);
        options.refuseUnknown(VALIDATE);

        try (InputStream input = uri.toURL().openStream()) {
            read(result, input, validates(options, uri));
        }
    }

    /**
     * The same reading, from a stream that is already open, for a caller who has the bytes and no URI to
     * name them with - {@link org.aeonbits.owner.Mutable#loadFromXML(InputStream)}.
     * <p>
     * The document is <b>validated against the grammar it declares</b>, as it is when read from a source.
     * The option that switches that off lives in the fragment of a URI, and there is no URI here: a caller
     * holding a stream is holding it for a reason of their own, and the way to read such a document
     * unvalidated is to name it as a source with <code>#validate=false</code>.
     * </p>
     * <p>
     * <b>The stream is closed</b> when this returns - the parser closes what it was handed - which is what
     * {@link java.util.Properties#loadFromXML(InputStream)} does with its own and therefore the behaviour
     * a caller of that method expects.
     * </p>
     *
     * @param result where the properties read are put.
     * @param input  the document; closed when this returns.
     * @throws IOException if the document cannot be read, or is not well formed, or breaks its own grammar.
     * @since 2.0.0
     */
    public void load(Properties result, InputStream input) throws IOException {
        read(result, input, true);
    }

    private void read(Properties result, InputStream input, boolean validating) throws IOException {
        try {
            SAXParser parser = factory(validating).newSAXParser();
            XmlToPropsHandler h = new XmlToPropsHandler();
            parser.setProperty("http://xml.org/sax/properties/lexical-handler", h);
            parser.parse(input, h);
            // merged only once the whole document has been read: see the handler's constructor for why it
            // does not write straight into what it was given
            result.putAll(h.properties());
        } catch (ParserConfigurationException e) {
            throw new IllegalArgumentException(e);
        } catch (SAXException e) {
            throw new IOException(e);
        }
    }

    /** The {@code validate} option, on unless the source says otherwise, and only ever the two words. */
    private static boolean validates(SourceOptions options, URI uri) {
        for (SourceOptions.Option option : options.all())
            if (VALIDATE.equals(option.name())) {
                String setting = option.setting().toLowerCase();
                if ("true".equals(setting)) return true;
                if ("false".equals(setting)) return false;
                throw unsupported("'%s' is not a setting for the XML option '%s' in %s; use 'true' or "
                        + "'false'", option.setting(), VALIDATE, uri);
            }
        return true;
    }

    @Override
    public String defaultSpecFor(String urlPrefix) {
        return urlPrefix + SUFFIX;
    }

}
