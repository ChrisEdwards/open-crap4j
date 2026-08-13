package org.opencrap4j.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/** Parses one JaCoCo XML report without resolving external XML resources. */
public final class JacocoXmlParser {
    private static final String EXTERNAL_GENERAL_ENTITIES =
            "http://xml.org/sax/features/external-general-entities";
    private static final String EXTERNAL_PARAMETER_ENTITIES =
            "http://xml.org/sax/features/external-parameter-entities";
    private static final String LOAD_EXTERNAL_DTD =
            "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    public JacocoReport parse(Path reportFile) throws IOException, JacocoParseException {
        try (InputStream input = Files.newInputStream(reportFile)) {
            return parse(input);
        }
    }

    public JacocoReport parse(InputStream input) throws JacocoParseException {
        try {
            DocumentBuilder builder = documentBuilder();
            Document document = builder.parse(input);
            Element root = document.getDocumentElement();
            if (!"report".equals(root.getTagName())) {
                throw new JacocoParseException("Expected a JaCoCo <report> root element");
            }
            return new JacocoReport(root.getAttribute("name"), parsePackages(root));
        } catch (ParserConfigurationException | SAXException | IllegalArgumentException exception) {
            throw new JacocoParseException("Could not parse JaCoCo XML", exception);
        } catch (IOException exception) {
            throw new JacocoParseException("Could not read JaCoCo XML", exception);
        }
    }

    private static DocumentBuilder documentBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature(EXTERNAL_GENERAL_ENTITIES, false);
        factory.setFeature(EXTERNAL_PARAMETER_ENTITIES, false);
        factory.setFeature(LOAD_EXTERNAL_DTD, false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        builder.setErrorHandler(new DefaultHandler());
        return builder;
    }

    private static List<JacocoPackage> parsePackages(Element container) {
        List<JacocoPackage> packages = new ArrayList<>();
        for (Node child = container.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (!(child instanceof Element element)) {
                continue;
            }
            if ("package".equals(element.getTagName())) {
                packages.add(new JacocoPackage(
                        element.getAttribute("name"), parseClasses(element)));
            } else if ("group".equals(element.getTagName())) {
                packages.addAll(parsePackages(element));
            }
        }
        return packages;
    }

    private static List<JacocoClass> parseClasses(Element packageElement) {
        List<JacocoClass> classes = new ArrayList<>();
        for (Element classElement : children(packageElement, "class")) {
            classes.add(new JacocoClass(
                    classElement.getAttribute("name"),
                    optionalAttribute(classElement, "sourcefilename"),
                    parseMethods(classElement)));
        }
        return classes;
    }

    private static List<JacocoMethod> parseMethods(Element classElement) {
        List<JacocoMethod> methods = new ArrayList<>();
        for (Element methodElement : children(classElement, "method")) {
            String line = methodElement.getAttribute("line");
            methods.add(new JacocoMethod(
                    methodElement.getAttribute("name"),
                    methodElement.getAttribute("desc"),
                    line.isEmpty() ? OptionalInt.empty() : OptionalInt.of(Integer.parseInt(line)),
                    parseCounters(methodElement)));
        }
        return methods;
    }

    private static Map<CounterType, Counter> parseCounters(Element methodElement) {
        Map<CounterType, Counter> counters = new EnumMap<>(CounterType.class);
        for (Element counterElement : children(methodElement, "counter")) {
            CounterType type = CounterType.valueOf(counterElement.getAttribute("type"));
            Counter counter = new Counter(
                    Integer.parseInt(counterElement.getAttribute("missed")),
                    Integer.parseInt(counterElement.getAttribute("covered")));
            counters.put(type, counter);
        }
        return counters;
    }

    private static List<Element> children(Element parent, String tagName) {
        List<Element> matches = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && tagName.equals(element.getTagName())) {
                matches.add(element);
            }
        }
        return matches;
    }

    private static Optional<String> optionalAttribute(Element element, String name) {
        return element.hasAttribute(name)
                ? Optional.of(element.getAttribute(name))
                : Optional.empty();
    }
}
