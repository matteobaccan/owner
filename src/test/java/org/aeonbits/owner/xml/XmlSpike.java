/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.xml;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.Stack;

/**
 * @author Luigi R. Viggiano
 */
public class XmlSpike {
    public void testParse() throws ParserConfigurationException, SAXException, IOException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser = factory.newSAXParser();

        InputStream in = getClass().getResourceAsStream("Config.xml");
        DefaultHandler h = new DefaultHandler() {
            Stack<String> paths = new Stack<String>();
            
            @Override
            public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                String path = (paths.size() == 0) ? qName : paths.peek() + "." + qName;
                paths.push(path);
                for (int i = 0; i < attributes.getLength(); i++) {
                    String attrName = attributes.getQName(i);
                    String attrValue = attributes.getValue(i);
                    System.out.println(path + "." + attrName + "=" + attrValue);
                }
            }

            @Override
            public void characters(char[] ch, int start, int length) throws SAXException {
                String value = new String(ch, start, length).trim();
                if (!value.isEmpty())
                    System.out.println(paths.peek() + "=" + new String(ch, start, length).trim());
            }

            @Override
            public void endElement(String uri, String localName, String qName) throws SAXException {
                paths.pop();
            }
        };
        parser.parse(in, h);
    }

    public static void main(String[] args) throws Exception {
        new XmlSpike().testParse();
    }
}
