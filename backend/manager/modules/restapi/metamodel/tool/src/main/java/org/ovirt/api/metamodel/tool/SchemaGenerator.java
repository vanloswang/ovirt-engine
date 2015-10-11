/*
Copyright (c) 2015 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package org.ovirt.api.metamodel.tool;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import com.sun.xml.txw2.output.IndentingXMLStreamWriter;
import org.ovirt.api.metamodel.concepts.Attribute;
import org.ovirt.api.metamodel.concepts.Concept;
import org.ovirt.api.metamodel.concepts.EnumType;
import org.ovirt.api.metamodel.concepts.EnumValue;
import org.ovirt.api.metamodel.concepts.Link;
import org.ovirt.api.metamodel.concepts.ListType;
import org.ovirt.api.metamodel.concepts.Model;
import org.ovirt.api.metamodel.concepts.Name;
import org.ovirt.api.metamodel.concepts.NameParser;
import org.ovirt.api.metamodel.concepts.StructType;
import org.ovirt.api.metamodel.concepts.Type;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This class takes a model and an input XML schema file and modifies it adding (at the end) the XML schema elements
 * corresponding to the model.
 */
@ApplicationScoped
public class SchemaGenerator {
    // URI and prefix for the XML schema namespace:
    private static final String XS_URI = "http://www.w3.org/2001/XMLSchema";
    private static final String XS_PREFIX = "xs";

    // URI and prefix for the JAXB namespace:
    private static final String JAXB_URI = "http://java.sun.com/xml/ns/jaxb";
    private static final String JAXB_PREFIX = "jaxb";

    // Root types, those that can appear as the root element of valid XML documents. We currently can't figure this
    // automatically because there isn't an operational model yet, and thus we don't now what types are returned or
    // received by operations.
    private static final Set<Name> ROOTS = new HashSet<>();

    private static void addRoot(String name) {
        ROOTS.add(NameParser.parseUsingSeparator(name, '-'));
    }

    static {
        addRoot("affinity-group");
        addRoot("agent");
        addRoot("application");
        addRoot("authorized-key");
        addRoot("balance");
        addRoot("bookmark");
        addRoot("cdrom");
        addRoot("certificate");
        addRoot("cluster");
        addRoot("cpu-profile");
        addRoot("data-center");
        addRoot("device");
        addRoot("disk");
        addRoot("disk-profile");
        addRoot("disk-snapshot");
        addRoot("domain");
        addRoot("event");
        addRoot("external-compute-resource");
        addRoot("external-discovered-host");
        addRoot("external-host");
        addRoot("external-host-group");
        addRoot("external-provider");
        addRoot("file");
        addRoot("filter");
        addRoot("floppy");
        addRoot("gluster-brick");
        addRoot("gluster-brick-advanced-details");
        addRoot("gluster-hook");
        addRoot("gluster-memory-pool");
        addRoot("gluster-server-hook");
        addRoot("gluster-volume");
        addRoot("gluster-volume-profile-details");
        addRoot("graphics-console");
        addRoot("group");
        addRoot("hook");
        addRoot("host");
        addRoot("host-device");
        addRoot("host-nic");
        addRoot("host-storage");
        addRoot("icon");
        addRoot("image");
        addRoot("instance-type");
        addRoot("iscsi-bond");
        addRoot("job");
        addRoot("katello-erratum");
        addRoot("label");
        addRoot("mac-pool");
        addRoot("network");
        addRoot("network-attachment");
        addRoot("nic");
        addRoot("numa-node");
        addRoot("open-stack-image");
        addRoot("open-stack-image-provider");
        addRoot("open-stack-network");
        addRoot("open-stack-network-provider");
        addRoot("open-stack-provider");
        addRoot("open-stack-subnet");
        addRoot("openstack-volume-authentication-key");
        addRoot("open-stack-volume-provider");
        addRoot("open-stack-volume-type");
        addRoot("operating-system-info");
        addRoot("permission");
        addRoot("permit");
        addRoot("product");
        addRoot("qos");
        addRoot("quota");
        addRoot("quota-cluster-limit");
        addRoot("quota-storage-limit");
        addRoot("reported-device");
        addRoot("role");
        addRoot("scheduling-policy");
        addRoot("scheduling-policy-unit");
        addRoot("session");
        addRoot("snapshot");
        addRoot("ssh");
        addRoot("ssh-public-key");
        addRoot("statistic");
        addRoot("step");
        addRoot("storage-connection");
        addRoot("storage-connection-extension");
        addRoot("storage-domain");
        addRoot("tag");
        addRoot("template");
        addRoot("unmanaged-network");
        addRoot("user");
        addRoot("vendor");
        addRoot("version");
        addRoot("virtual-numa-node");
        addRoot("vm");
        addRoot("vm-base");
        addRoot("vm-pool");
        addRoot("vnic-profile");
        addRoot("watchdog");
        addRoot("weight");
    }

    // Exceptions to the rules to calculate complex type names:
    private static final Map<String, String> TYPE_NAME_EXCEPTIONS = new HashMap<>();

    static {
        TYPE_NAME_EXCEPTIONS.put("Device", "BaseDevice");
        TYPE_NAME_EXCEPTIONS.put("SeLinux", "SELinux");
    }

    // Exceptions to the rules to calculate tag names:
    private static final Map<String, String> TAG_NAME_EXCEPTIONS = new HashMap<>();

    static {
        TAG_NAME_EXCEPTIONS.put("gluster_brick", "brick");
        TAG_NAME_EXCEPTIONS.put("gluster_brick_memory_info", "brick_memoryinfo");
        TAG_NAME_EXCEPTIONS.put("gluster_bricks", "bricks");
        TAG_NAME_EXCEPTIONS.put("gluster_memory_pool", "memory_pool");
        TAG_NAME_EXCEPTIONS.put("gluster_memory_pools", "memory_pools");
        TAG_NAME_EXCEPTIONS.put("gluster_server_hook", "server_hook");
        TAG_NAME_EXCEPTIONS.put("gluster_server_hooks", "server_hooks");
        TAG_NAME_EXCEPTIONS.put("migration_options", "migration");
        TAG_NAME_EXCEPTIONS.put("numa_node", "host_numa_node");
        TAG_NAME_EXCEPTIONS.put("numa_nodes", "host_numa_nodes");
        TAG_NAME_EXCEPTIONS.put("open_stack_image", "openstack_image");
        TAG_NAME_EXCEPTIONS.put("open_stack_image_provider", "openstack_image_provider");
        TAG_NAME_EXCEPTIONS.put("open_stack_image_providers", "openstack_image_providers");
        TAG_NAME_EXCEPTIONS.put("open_stack_images", "openstack_images");
        TAG_NAME_EXCEPTIONS.put("open_stack_network", "openstack_network");
        TAG_NAME_EXCEPTIONS.put("open_stack_network_provider", "openstack_network_provider");
        TAG_NAME_EXCEPTIONS.put("open_stack_network_providers", "openstack_network_providers");
        TAG_NAME_EXCEPTIONS.put("open_stack_networks", "openstack_networks");
        TAG_NAME_EXCEPTIONS.put("open_stack_subnet", "openstack_subnet");
        TAG_NAME_EXCEPTIONS.put("open_stack_subnets", "openstack_subnets");
        TAG_NAME_EXCEPTIONS.put("open_stack_volume", "openstack_volume");
        TAG_NAME_EXCEPTIONS.put("open_stack_volume_provider", "openstack_volume_provider");
        TAG_NAME_EXCEPTIONS.put("open_stack_volume_providers", "openstack_volume_providers");
        TAG_NAME_EXCEPTIONS.put("open_stack_volumes", "openstack_volumes");
        TAG_NAME_EXCEPTIONS.put("operating_system", "os");
        TAG_NAME_EXCEPTIONS.put("operating_system_info", "operating_system");
        TAG_NAME_EXCEPTIONS.put("operating_system_infos", "operation_systems");
        TAG_NAME_EXCEPTIONS.put("operating_systems", "oss");
        TAG_NAME_EXCEPTIONS.put("transparent_huge_pages", "transparent_hugepages");
        TAG_NAME_EXCEPTIONS.put("virtual_numa_node", "vm_numa_node");
        TAG_NAME_EXCEPTIONS.put("virtual_numa_nodes", "vm_numa_nodes");
    }

    // Exceptions to the rules to calculate struct member type names:
    private static final Map<Name, Map<Name, String>> MEMBER_SCHEMA_TYPE_NAME_EXCEPTIONS = new HashMap<>();

    private static void addMemberSchemaTypeNameException(String type, String member, String exception) {
        Name typeName = NameParser.parseUsingSeparator(type, '-');
        Name memberName = NameParser.parseUsingSeparator(member, '-');
        Map<Name, String> typeExceptions = MEMBER_SCHEMA_TYPE_NAME_EXCEPTIONS.get(typeName);
        if (typeExceptions == null) {
            typeExceptions = new HashMap<>();
            MEMBER_SCHEMA_TYPE_NAME_EXCEPTIONS.put(typeName, typeExceptions);
        }
        typeExceptions.put(memberName, exception);
    }

    private static String getMemberSchemaTypeNameException(Name typeName, Name memberName) {
        Map<Name, String> typeExceptions = MEMBER_SCHEMA_TYPE_NAME_EXCEPTIONS.get(typeName);
        if (typeExceptions == null) {
            return null;
        }
        return typeExceptions.get(memberName);
    }

    static {
        addMemberSchemaTypeNameException("disk", "actual-size", "xs:long");
        addMemberSchemaTypeNameException("disk", "provisioned-size", "xs:long");
        addMemberSchemaTypeNameException("gluster-client", "bytes-read", "xs:long");
        addMemberSchemaTypeNameException("gluster-client", "bytes-written", "xs:long");
        addMemberSchemaTypeNameException("host", "max-scheduling-memory", "xs:long");
        addMemberSchemaTypeNameException("host", "memory", "xs:long");
        addMemberSchemaTypeNameException("host-nic", "speed", "xs:long");
        addMemberSchemaTypeNameException("logical-unit", "size", "xs:long");
        addMemberSchemaTypeNameException("memory-policy", "guaranteed", "xs:long");
        addMemberSchemaTypeNameException("numa-node", "memory", "xs:long");
        addMemberSchemaTypeNameException("quota-cluster-limit", "memory-limit", "xs:double");
        addMemberSchemaTypeNameException("quota-cluster-limit", "memory-usage", "xs:double");
        addMemberSchemaTypeNameException("quota-storage-limit", "limit", "xs:long");
        addMemberSchemaTypeNameException("quota-storage-limit", "usage", "xs:double");
        addMemberSchemaTypeNameException("statistic", "kind", "StatisticKind");
        addMemberSchemaTypeNameException("statistic", "type", "ValueType");
        addMemberSchemaTypeNameException("statistic", "unit", "StatisticUnit");
        addMemberSchemaTypeNameException("storage-domain", "available", "xs:long");
        addMemberSchemaTypeNameException("storage-domain", "used", "xs:long");
        addMemberSchemaTypeNameException("storage-domain", "committed", "xs:long");
        addMemberSchemaTypeNameException("ticket", "expiry", "xs:unsignedInt");
        addMemberSchemaTypeNameException("vm-base", "memory", "xs:long");
    }

    // Exceptions to the rules to calculate plurals:
    private static final Map<String, String> PLURALS = new HashMap<>();

    static {
        PLURALS.put("display", "displays");
        PLURALS.put("erratum", "errata");
        PLURALS.put("key", "keys");
    }

    // Exceptions to the rules to calculate singulars:
    private static final Map<String, String> SINGULARS = new HashMap<>();

    static {
        // No exceptions yet.
    }

    // We will need an XML parser:
    private DocumentBuilder parser;

    // The reference to the model:
    private Model model;

    // The input and output schema files:
    private File inFile;
    private File outFile;

    @PostConstruct
    private void init() {
        // Create the XML parser:
        try {
            parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        }
        catch (ParserConfigurationException exception) {
            throw new RuntimeException("Can't create XML parser.", exception);
        }
    }

    /**
     * Set the input XML schema file.
     */
    public void setInFile(File newInFile) {
        inFile = newInFile;
    }

    /**
     * Set the output XML schema file.
     */
    public void setOutFile(File newOutFile) {
        outFile = newOutFile;
    }

    public void generate(Model model) {
        // Save the reference to the model:
        this.model = model;

        // Parse the input XML schema:
        Document inSchema;
        try {
            inSchema = parser.parse(inFile);
        }
        catch (Exception exception) {
            throw new RuntimeException(
                "Can't parse input XML schema from file \"" + inFile.getAbsolutePath() + "\".",
                exception
            );
        }

        // Generate the new XML schema:
        Document newSchema = generateSchema();

        // Add all the elements of the new schema at the end of the input schema:
        Element inRoot = inSchema.getDocumentElement();
        Element newRoot = newSchema.getDocumentElement();
        NodeList newChildren = newRoot.getChildNodes();
        for (int i = 0; i < newChildren.getLength(); i++) {
            Node newChild = newChildren.item(i);
            Node importedChild = inSchema.importNode(newChild, true);
            inRoot.appendChild(importedChild);
        }

        // Write the output XML schema:
        File outDir = outFile.getParentFile();
        if (!outDir.exists()) {
            if (!outDir.mkdirs()) {
                throw new RuntimeException(
                    "Can't create output XML directory \"" + outDir.getAbsolutePath() + "\"."
                );
            }
        }
        Transformer transformer;
        try {
            transformer = TransformerFactory.newInstance().newTransformer();
        }
        catch (TransformerConfigurationException exception) {
           throw new RuntimeException(
               "Can't create XSLT transformer.",
               exception
           );
        }
        Source transformerIn = new DOMSource(inSchema);
        Result transformerOut = new StreamResult(outFile);
        try {
            transformer.transform(transformerIn, transformerOut);
        }
        catch (TransformerException exception) {
            throw new RuntimeException(
                "Can't write output XML schema to file \"" + outFile.getAbsolutePath() + "\".",
                exception
            );
        }
    }

    private Document generateSchema() {
        Document document = parser.newDocument();
        DOMResult result = new DOMResult(document);
        IndentingXMLStreamWriter indenter = null;
        try {
            XMLStreamWriter writer = XMLOutputFactory.newFactory().createXMLStreamWriter(result);
            writer.setPrefix(XS_PREFIX, XS_URI);
            writer.setPrefix(JAXB_PREFIX, JAXB_URI);
            indenter = new IndentingXMLStreamWriter(writer);
            indenter.setIndentStep("  ");
            writeSchema(indenter);
            return document;
        }
        catch (XMLStreamException exception) {
            throw new RuntimeException(
                "Can't generate XML schema document.",
                exception
            );
        }
        finally {
            if (indenter != null) {
                try {
                    indenter.close();
                }
                catch (XMLStreamException exception) {
                    throw new RuntimeException(
                        "Can't close XML writer.",
                        exception
                    );
                }
            }
        }
    }

    private void writeSchema(XMLStreamWriter writer) throws XMLStreamException {
        // Header:
        writer.writeStartElement(XS_URI, "schema");
        writer.writeAttribute("version", "1.0");

        // Find the struct and enum types and sort them by name, so that the order in the generated XML schema will be
        // predictable:
        List<StructType> structTypes = new ArrayList<>();
        List<EnumType> enumTypes = new ArrayList<>();
        for (Type type : model.getTypes()) {
            if (type instanceof StructType) {
                structTypes.add((StructType) type);
            }
            if (type instanceof EnumType) {
                enumTypes.add((EnumType) type);
            }
        }
        structTypes.sort(comparing(Concept::getName));
        enumTypes.sort(comparing(Concept::getName));

        // Write the XML schema group of elements that are used by the capabilities resource to list the possible
        // values of the enum types. Eventually the complete capabilities elements will be generated, but for now
        // it is written manually and int includes this group.
        writeEnumValues(writer, enumTypes);

        // Write the XML schema for the enum types:
        for (EnumType enumType : enumTypes) {
            writeEnumType(writer, enumType);
        }

        // Write the XML schema for the struct types:
        for (StructType structType : structTypes) {
            writeStructType(writer, structType);
        }

        // Footer:
        writer.writeEndElement();
    }

    private void writeEnumValues(XMLStreamWriter writer, List<EnumType> types) throws XMLStreamException {
        // Write the group used by the capabilities resource to report that contains the possible values for all the
        // enum types:
        writer.writeStartElement(XS_URI, "group");
        writer.writeAttribute("name", "EnumValues");
        writer.writeStartElement(XS_URI, "sequence");
        for (EnumType type : types) {
            Name name = type.getName();
            Name plural = getPlural(name);
            writer.writeStartElement(XS_URI, "element");
            writer.writeAttribute("name", getSchemaTagName(plural));
            writer.writeAttribute("type", getSchemaEnumTypeValuesName(name));
            writer.writeAttribute("minOccurs", "1");
            writer.writeAttribute("maxOccurs", "1");
            writer.writeEndElement();
        }
        writer.writeEndElement();
        writer.writeEndElement();
        writeLine(writer);

        // For each enum type write the complex type used by the capabilities resource to report the possible values
        // for that enum type:
        for (EnumType type : types) {
            Name name = type.getName();
            writer.writeStartElement(XS_URI, "complexType");
            writer.writeAttribute("name", getSchemaEnumTypeValuesName(name));
            writer.writeStartElement(XS_URI, "sequence");
            writer.writeStartElement(XS_URI, "element");
            writer.writeAttribute("name", getSchemaTagName(name));
            writer.writeAttribute("type", getSchemaTypeName(name));
            writer.writeAttribute("minOccurs", "0");
            writer.writeAttribute("maxOccurs", "unbounded");
            writer.writeEndElement();
            writer.writeEndElement();
            writer.writeEndElement();
            writeLine(writer);
        }
    }

    private void writeStructType(XMLStreamWriter writer, StructType type) throws XMLStreamException {
        // Get the name of the type, and its plural:
        Name typeName = type.getName();
        Name typePlural = getPlural(typeName);

        // Check if this type is a root entity, one that can appear as the root of a valid XML document, as in that
        // case the complex types must extend "BaseResource" and "BaseResources":
        boolean isRoot = ROOTS.contains(type.getName());

        // Tag for the entity:
        writer.writeStartElement(XS_URI, "element");
        writer.writeAttribute("name", getSchemaTagName(typeName));
        writer.writeAttribute("type", getSchemaTypeName(type));
        writer.writeEndElement();
        writeLine(writer);

        // Determine if the complex type is an extension of other complex type:
        String baseComplexTypeName = null;
        Type baseType = type.getBase();
        if (baseType != null) {
            baseComplexTypeName = getSchemaTypeName(baseType);
        }
        else {
           if (isRoot) {
               baseComplexTypeName = "BaseResource";
           }
        }

        // Complex type for the entity:
        writer.writeStartElement(XS_URI, "complexType");
        writer.writeAttribute("name", getSchemaTypeName(type));
        if (baseComplexTypeName != null) {
            writer.writeStartElement(XS_URI, "complexContent");
            writer.writeStartElement(XS_URI, "extension");
            writer.writeAttribute("base", baseComplexTypeName);
        }
        writeStructMembers(writer, type);
        if (baseComplexTypeName != null) {
            writer.writeEndElement();
            writer.writeEndElement();
        }
        writer.writeEndElement();
        writeLine(writer);

        // Tag for the collection:
        writer.writeStartElement(XS_URI, "element");
        writer.writeAttribute("name", getSchemaTagName(typePlural));
        writer.writeAttribute("type", getSchemaTypeName(typePlural));
        writer.writeEndElement();
        writeLine(writer);

        // Complex type for the collection:
        writer.writeStartElement(XS_URI, "complexType");
        writer.writeAttribute("name", getSchemaTypeName(typePlural));
        if (isRoot) {
            writer.writeStartElement(XS_URI, "complexContent");
            writer.writeStartElement(XS_URI, "extension");
            writer.writeAttribute("base", "BaseResources");
        }
        writer.writeStartElement(XS_URI, "sequence");
        writer.writeStartElement(XS_URI, "element");
        writer.writeAttribute("ref", getSchemaTagName(typeName));
        writer.writeAttribute("minOccurs", "0");
        writer.writeAttribute("maxOccurs", "unbounded");
        writeJaxbProperty(writer, getSchemaTypeName(typePlural));
        writer.writeEndElement();
        writer.writeEndElement();
        if (isRoot) {
            writer.writeEndElement();
            writer.writeEndElement();
        }
        writer.writeEndElement();
        writeLine(writer);
    }

    private void writeStructMembers(XMLStreamWriter writer, StructType type) throws XMLStreamException {
        writer.writeStartElement(XS_URI, "sequence");
        for (Attribute attribute : type.getDeclaredAttributes()) {
            writeStructMember(writer, type, attribute.getType(), attribute.getName());
        }
        for (Link link : type.getDeclaredLinks()) {
            writeStructMember(writer, type, link.getType(), link.getName());
        }
        writer.writeEndElement();
    }

    private void writeStructMember(XMLStreamWriter writer, StructType declaringType, Type memberType, Name memberName)
            throws XMLStreamException {
        // Calculate the singular of the name:
        Name singular = getSingular(memberName);

        // Write the element definition:
        writer.writeStartElement(XS_URI, "element");
        writer.writeAttribute("name", getSchemaTagName(memberName));
        writer.writeAttribute("minOccurs", "0");
        writer.writeAttribute("maxOccurs", "1");
        if (memberType instanceof ListType) {
            ListType listType = (ListType) memberType;
            Type elementType = listType.getElementType();
            String elementTypeName = getMemberSchemaTypeName(declaringType, elementType, memberName);
            if (elementTypeName.startsWith("xs:")) {
                // Attributes that are lists of XML schema scalar types (xs:string, xs:int, etc) are represented with a
                // wrapper element named like the attribute, and then a sequence of elements named like the attribute
                // but in singular. For example, if the name of the attribute is "dns_servers" and the values are
                // strings it will be represented as follows:
                //
                // <dns_servers>
                //   <dns_server>a.b.c.d</dns_server>
                //   <dns_server>e.f.g.h</dns_server>
                // </dns_servers>
                //
                // The generated XML schema will look like this:
                //
                // <xs:element name="dns_servers" minOccurs="0" maxOccurs="1">
                //   <xs:complexType>
                //     <xs:annotation>
                //       <xs:appinfo>
                //         <jaxb:class name="DnsServersList"/>
                //       </xs:appinfo>
                //     </xs:annotation>
                //     <xs:sequence>
                //       <xs:element name="dns_servers" type="xs:string" minOccurs="0" maxOccurs="unbounded">
                //         <xs:annotation>
                //           <xs:appinfo>
                //             <jaxb:class property="DnsServers"/>
                //           </xs:appinfo>
                //         </xs:annotation>
                //       </xs:element>
                //     </xs:sequence>
                //   </xs:complexType>
                // </xs:element>
                writer.writeStartElement(XS_URI, "complexType");
                writeJaxbClass(writer, getSchemaTypeName(memberName) + "List");
                writer.writeStartElement(XS_URI, "sequence");
                writer.writeStartElement(XS_URI, "element");
                writer.writeAttribute("name", getSchemaTagName(singular));
                writer.writeAttribute("type", elementTypeName);
                writer.writeAttribute("minOccurs", "0");
                writer.writeAttribute("maxOccurs", "unbounded");
                writeJaxbProperty(writer, getSchemaTypeName(memberName));
                writer.writeEndElement();
                writer.writeEndElement();
                writer.writeEndElement();
            }
            else {
                // Attributes of lists of XML schema complex types (Disk, Vm, etc) are represented with with a wrapper
                // element named like the attribute, and then a sequence of elements named like the type of the element.
                // For example, if the attribute is "primary_disks" and the values are objects of type "Disk" then it
                // will be represented as follows:
                //
                // <primary_disks>
                //   <disk>
                //     ...
                //   </disk>
                // </primary_disks>
                //
                // The generated XML schema will look like this:
                //
                // <xs:element name="primary_disks" type="Disks" minOccurs="0" maxOccurs="1"/>
                //
                // Note that this assumes that a "Disks" complex type has been generated, which is always true.
                writer.writeAttribute("name", getSchemaTagName(memberName));
                writer.writeAttribute("type", getSchemaTypeName(getPlural(elementType.getName())));
                writer.writeAttribute("minOccurs", "0");
                writer.writeAttribute("maxOccurs", "1");
            }
        }
        else {
            writer.writeAttribute("name", getSchemaTagName(memberName));
            writer.writeAttribute("type", getMemberSchemaTypeName(declaringType, memberType, memberName));
            writer.writeAttribute("minOccurs", "0");
            writer.writeAttribute("maxOccurs", "1");
        }
        writer.writeEndElement();
    }

    private void writeEnumType(XMLStreamWriter writer, EnumType type) throws XMLStreamException {
        // Get the enum values and sort them by name:
        List<EnumValue> values = new ArrayList<>(type.getValues());
        values.sort(comparing(Concept::getName));

        // Generate the XML schema enumerated type that will for attributes whose value is of this enum type:
        writer.writeStartElement(XS_URI, "simpleType");
        writer.writeAttribute("name", getSchemaTypeName(type));
        writer.writeStartElement(XS_URI, "restriction");
        writer.writeAttribute("base", "xs:string");
        for (EnumValue value : values) {
            writer.writeStartElement(XS_URI, "enumeration");
            writer.writeAttribute("value", getSchemaEnumValueName(value));
            writeJaxbProperty(writer, getJavaEnumValueName(value));
            writer.writeEndElement();
        }
        writer.writeEndElement();
        writer.writeEndElement();
        writeLine(writer);
    }

    private void writeJaxbClass(XMLStreamWriter writer, String value) throws XMLStreamException {
        writeJaxbCustomization(writer, "class", "name", value);
    }

    private void writeJaxbProperty(XMLStreamWriter writer, String value) throws XMLStreamException {
        writeJaxbCustomization(writer, "property", "name", value);
    }

    private void writeLine(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeCharacters("\n");
    }

    private void writeJaxbCustomization(XMLStreamWriter writer, String tag, String name, String value) throws XMLStreamException {
        writer.writeStartElement(XS_URI, "annotation");
        writer.writeStartElement(XS_URI, "appinfo");
        writer.writeStartElement(JAXB_URI, tag);
        writer.writeAttribute(name, value);
        writer.writeEndElement();
        writer.writeEndElement();
        writer.writeEndElement();
    }

    private String getMemberSchemaTypeName(Type declaringType, Type memberType, Name memberName) {
        String exception = getMemberSchemaTypeNameException(declaringType.getName(), memberName);
        if (exception != null) {
            return exception;
        }
        return getSchemaTypeName(memberType);
    }

    private String getSchemaTypeName(Type type) {
        if (type == model.getBooleanType()) {
            return "xs:boolean";
        }
        if (type == model.getStringType()) {
            return "xs:string";
        }
        if (type == model.getIntegerType()) {
            return "xs:int";
        }
        if (type == model.getDateType()) {
            return "xs:dateTime";
        }
        if (type == model.getDecimalType()) {
            return "xs:decimal";
        }
        return getSchemaTypeName(type.getName());
    }

    private String getSchemaTypeName(Name name) {
        StringBuilder buffer = new StringBuilder();
        if (name != null) {
            for (String word : name.getWords()) {
                String capitalizedWord = Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase();
                buffer.append(capitalizedWord);
            }
        }
        String result = buffer.toString();
        String exception = TYPE_NAME_EXCEPTIONS.get(result);
        if (exception != null) {
            result = exception;
        }
        return result;
    }

    private String getSchemaTagName(Name name) {
        String result = name.words().map(String::toLowerCase).collect(joining("_"));
        String exception = TAG_NAME_EXCEPTIONS.get(result);
        if (exception != null) {
            result = exception;
        }
        return result;
    }

    private String getSchemaEnumTypeValuesName(Name name) {
        return getSchemaTypeName(name) + "Values";
    }

    private String getSchemaEnumValueName(EnumValue value) {
        return value.getName().words().map(String::toLowerCase).collect(joining("_"));
    }

    private String getJavaEnumValueName(EnumValue value) {
        return value.getName().words().map(String::toUpperCase).collect(joining("_"));
    }

    private Name getPlural(Name singular) {
        List<String> words = singular.getWords();
        String last = words.get(words.size() - 1);
        last = getPlural(last);
        words.set(words.size() - 1, last);
        return new Name(words);
    }

    private String getPlural(String singular) {
        String plural = PLURALS.get(singular);
        if (plural == null) {
            if (singular.endsWith("y")) {
                plural = singular.substring(0, singular.length() - 1) + "ies";
            }
            else {
                plural = singular + "s";
            }
        }
        return plural;
    }

    private Name getSingular(Name plural) {
        List<String> words = plural.getWords();
        String last = words.get(words.size() - 1);
        last = getSingular(last);
        words.set(words.size() - 1, last);
        return new Name(words);
    }

    private String getSingular(String plural) {
        String singular = SINGULARS.get(plural);
        if (singular == null) {
            if (plural.endsWith("ies")) {
                singular = plural.substring(0, plural.length() - 3) + "y";
            }
            else if (plural.endsWith("s")) {
                singular = plural.substring(0, plural.length() - 1);
            }
        }
        return singular;
    }
}
