/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.xml;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.ext.DefaultHandler2;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Properties;
import java.util.Scanner;
import java.util.Stack;

import static org.junit.Assert.assertEquals;

/**
 * @author Luigi R. Viggiano
 */
public class XmlSpike {

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
//        "<!ATTLIST comment key CDATA #REQUIRED>"; // uncomment to test validation

        private boolean isJavaPropertiesFormat = false;
        private final PrintWriter writer;
        private final Stack<String> paths = new Stack<String>();
        private final Stack<StringBuilder> value = new Stack<StringBuilder>();

        @Override
        public InputSource resolveEntity(String name, String publicId, String baseURI,
                                         String systemId) throws SAXException, IOException {
            if (systemId.equals(PROPS_DTD_URI)) {
                isJavaPropertiesFormat = true;
                InputSource is;
                is = new InputSource(new StringReader(PROPS_DTD));
                is.setSystemId(PROPS_DTD_URI);
                return is;
            } else {
                return super.resolveEntity(name, publicId, baseURI, systemId);
            }
        }

        @Override
        public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
            return resolveEntity(null, publicId, null, systemId);
        }

        public XmlToPropsHandler(PrintWriter writer) {
            this.writer = writer;
        }

        @Override
        public void startElement(String uri, String localName, String qName,
                                 Attributes attributes) throws SAXException {
            this.value.push(new StringBuilder());

            if (isJavaPropertiesFormat) {
                if ("entry".equals(qName))
                    paths.push(attributes.getValue("key"));
                else
                    paths.push(qName);
            } else {
                String path = (paths.size() == 0) ? qName : paths.peek() + "." + qName;
                paths.push(path);
                for (int i = 0; i < attributes.getLength(); i++) {
                    String attrName = attributes.getQName(i);
                    String attrValue = attributes.getValue(i);
                    writer.println(path + "." + attrName + "=" + attrValue);
                }
            }
        }

        private String fixNewLines(String value) {
            return value.replace("\n", "\\\n");
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            String value = new String(ch, start, length);
            if (!value.isEmpty())
                this.value.peek().append(value);
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            String key = paths.peek();
            String value = this.value.peek().toString().trim();
            if (!value.isEmpty() &&
                    !(isJavaPropertiesFormat && "comment".equals(key)))
                writer.println(key + "=" + fixNewLines(value));
            this.value.pop();
            paths.pop();
        }


        @Override
        public void error(SAXParseException e) throws SAXException {
            if (isJavaPropertiesFormat)
                throw e;
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            error(e);
        }
    }

    public static Properties load(InputStream inputStream) throws ParserConfigurationException, SAXException,
            IOException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setValidating(true);
        factory.setNamespaceAware(true);
        SAXParser parser = factory.newSAXParser();

        StringWriter output = new StringWriter();
        PrintWriter pw = new PrintWriter(output);
        XmlToPropsHandler h = new XmlToPropsHandler(pw);
        parser.setProperty("http://xml.org/sax/properties/lexical-handler", h);

        parser.parse(inputStream, h);
        pw.flush();
        output.flush();

        System.out.println("Output:\n" + output);

        Properties props = new Properties();
        props.load(new StringReader(output.toString()));
        return props;
    }


    public static void main(String[] args) throws Exception {

        InputStream in = XmlSpike.class.getResourceAsStream("Config.xml");

        Properties props = load(in);

        File file = new File("target/test-resources/props.xml");
        file.getParentFile().mkdirs();

        props.storeToXML(new FileOutputStream(file), "test");

        System.out.println("java xml properties format:\n" + toString(new FileInputStream(file)));

        Properties props2 = new Properties();
        props2.loadFromXML(new FileInputStream(file));

        Properties props3 = load(new FileInputStream(file));

        System.out.println();
        props.store(System.out, "props");
        System.out.println();
        props2.store(System.out, "props2");
        System.out.println();
        props3.store(System.out, "props3");

        assertEquals(props2, props3);
    }

    private static String toString(InputStream is) {
        Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }
}
