/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.trellisldp.http;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Instant.MAX;
import static java.time.Instant.ofEpochSecond;
import static java.util.Arrays.asList;
import static java.util.Collections.emptySortedSet;
import static java.util.Collections.singletonList;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.apache.commons.io.IOUtils.readLines;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.trellisldp.api.Resource.SpecialResources.DELETED_RESOURCE;
import static org.trellisldp.api.Resource.SpecialResources.MISSING_RESOURCE;
import static org.trellisldp.api.TrellisUtils.TRELLIS_BNODE_PREFIX;
import static org.trellisldp.api.TrellisUtils.TRELLIS_DATA_PREFIX;
import static org.trellisldp.api.TrellisUtils.getInstance;
import static org.trellisldp.vocabulary.RDF.type;
import static org.trellisldp.vocabulary.Trellis.PreferAccessControl;
import static org.trellisldp.vocabulary.Trellis.PreferServerManaged;
import static org.trellisldp.vocabulary.Trellis.PreferUserManaged;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.apache.commons.rdf.api.BlankNode;
import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Literal;
import org.apache.commons.rdf.api.RDF;
import org.apache.commons.rdf.api.RDFTerm;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.HttpUrlConnectorProvider;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.trellisldp.api.AgentService;
import org.trellisldp.api.AuditService;
import org.trellisldp.api.Binary;
import org.trellisldp.api.BinaryMetadata;
import org.trellisldp.api.BinaryService;
import org.trellisldp.api.EventService;
import org.trellisldp.api.IOService;
import org.trellisldp.api.MementoService;
import org.trellisldp.api.Metadata;
import org.trellisldp.api.NoopAuditService;
import org.trellisldp.api.Resource;
import org.trellisldp.api.ResourceService;
import org.trellisldp.constraint.LdpConstraintService;
import org.trellisldp.http.core.DefaultEtagGenerator;
import org.trellisldp.http.core.DefaultTimemapGenerator;
import org.trellisldp.http.core.ServiceBundler;
import org.trellisldp.io.JenaIOService;
import org.trellisldp.vocabulary.ACL;
import org.trellisldp.vocabulary.DC;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.XSD;

abstract class BaseTrellisHttpResourceTest extends JerseyTest {

    protected static final IOService ioService = new JenaIOService();

    protected static final AuditService auditService = new NoopAuditService();

    protected static final int timestamp = 1496262729;

    protected static final Instant time = ofEpochSecond(timestamp);

    protected static final ObjectMapper MAPPER = new ObjectMapper();

    protected static final RDF rdf = getInstance();

    protected static final IRI agent = rdf.createIRI("user:agent");

    protected static final BlankNode bnode = rdf.createBlankNode();

    protected static final String BINARY_MIME_TYPE = "text/plain";

    protected static final String RANDOM_VALUE = "randomValue";

    protected static final String RESOURCE_PATH = "resource";
    protected static final String CHILD_PATH = RESOURCE_PATH + "/child";
    protected static final String BINARY_PATH = "binary";
    protected static final String NON_EXISTENT_PATH = "nonexistent";
    protected static final String DELETED_PATH = "deleted";
    protected static final String USER_DELETED_PATH = "userdeleted";
    protected static final String NEW_RESOURCE = RESOURCE_PATH + "/newresource";

    protected static final IRI identifier = rdf.createIRI(TRELLIS_DATA_PREFIX + RESOURCE_PATH);
    protected static final IRI root = rdf.createIRI(TRELLIS_DATA_PREFIX);
    protected static final IRI binaryIdentifier = rdf.createIRI(TRELLIS_DATA_PREFIX + BINARY_PATH);
    protected static final IRI binaryInternalIdentifier = rdf.createIRI("file:///some/file");
    protected static final IRI nonexistentIdentifier = rdf.createIRI(TRELLIS_DATA_PREFIX + NON_EXISTENT_PATH);
    protected static final IRI newresourceIdentifier = rdf.createIRI(TRELLIS_DATA_PREFIX + NEW_RESOURCE);
    protected static final IRI childIdentifier = rdf.createIRI(TRELLIS_DATA_PREFIX + CHILD_PATH);
    protected static final IRI deletedIdentifier = rdf.createIRI(TRELLIS_DATA_PREFIX + DELETED_PATH);
    protected static final IRI userDeletedIdentifier = rdf.createIRI(TRELLIS_DATA_PREFIX + USER_DELETED_PATH);
    protected static final Set<IRI> allInteractionModels = new HashSet<>(asList(LDP.Resource, LDP.RDFSource,
                LDP.NonRDFSource, LDP.Container, LDP.BasicContainer, LDP.DirectContainer, LDP.IndirectContainer));

    protected static final String BASE_URL = "http://example.org/";

    protected static final String HUB = "http://hub.example.org/";

    protected static final BinaryMetadata testBinary = BinaryMetadata.builder(binaryInternalIdentifier)
        .mimeType(BINARY_MIME_TYPE).build();

    @Mock
    protected ServiceBundler mockBundler;

    @Mock
    protected MementoService mockMementoService;

    @Mock
    protected ResourceService mockResourceService;

    @Mock
    protected BinaryService mockBinaryService;

    @Mock
    protected Binary mockBinary;

    @Mock
    protected AgentService mockAgentService;

    @Mock
    protected EventService mockEventService;

    @Mock
    protected Resource mockResource, mockVersionedResource, mockBinaryResource, mockBinaryVersionedResource,
              mockRootResource;

    @Mock
    protected InputStream mockInputStream;

    protected String getBaseUrl() {
        return BASE_URL;
    }

    @BeforeAll
    public void before() throws Exception {
        super.setUp();
    }

    @Override
    protected void configureClient(final ClientConfig config) {
        config.property(HttpUrlConnectorProvider.SET_METHOD_WORKAROUND, true);
    }

    @AfterAll
    public void after() throws Exception {
        super.tearDown();
    }

    @BeforeEach
    public void setUpMocks() throws Exception {
        setUpBundler();
        setUpResourceService();
        setUpMementoService();
        setUpBinaryService();
        setUpResources();

        when(mockAgentService.asAgent(anyString())).thenReturn(agent);
    }

    private void setUpBundler() {
        when(mockBundler.getResourceService()).thenReturn(mockResourceService);
        when(mockBundler.getIOService()).thenReturn(ioService);
        when(mockBundler.getBinaryService()).thenReturn(mockBinaryService);
        when(mockBundler.getMementoService()).thenReturn(mockMementoService);
        when(mockBundler.getAgentService()).thenReturn(mockAgentService);
        when(mockBundler.getAuditService()).thenReturn(auditService);
        when(mockBundler.getEventService()).thenReturn(mockEventService);
        when(mockBundler.getConstraintServices()).thenReturn(singletonList(new LdpConstraintService()));
        when(mockBundler.getEtagGenerator()).thenReturn(new DefaultEtagGenerator());
        when(mockBundler.getTimemapGenerator()).thenReturn(new DefaultTimemapGenerator());
    }

    private void setUpResourceService() {
        when(mockResourceService.get(eq(identifier))).thenAnswer(inv -> completedFuture(mockResource));
        when(mockResourceService.get(eq(rdf.createIRI(TRELLIS_DATA_PREFIX + "resource"))))
            .thenAnswer(inv -> completedFuture(mockResource));
        when(mockResourceService.get(eq(root))).thenAnswer(inv -> completedFuture(mockRootResource));
        when(mockResourceService.get(eq(childIdentifier))).thenAnswer(inv -> completedFuture(MISSING_RESOURCE));
        when(mockResourceService.supportedInteractionModels()).thenReturn(allInteractionModels);
        when(mockResourceService.get(eq(newresourceIdentifier))).thenAnswer(inv -> completedFuture(MISSING_RESOURCE));
        when(mockResourceService.get(eq(binaryIdentifier))).thenAnswer(inv -> completedFuture(mockBinaryResource));
        when(mockResourceService.get(eq(nonexistentIdentifier))).thenAnswer(inv -> completedFuture(MISSING_RESOURCE));
        when(mockResourceService.get(eq(deletedIdentifier))).thenAnswer(inv -> completedFuture(DELETED_RESOURCE));
        when(mockResourceService.get(eq(userDeletedIdentifier))).thenAnswer(inv -> completedFuture(DELETED_RESOURCE));
        when(mockResourceService.generateIdentifier()).thenReturn(RANDOM_VALUE);
        when(mockResourceService.unskolemize(any(IRI.class))).thenCallRealMethod();
        when(mockResourceService.toInternal(any(RDFTerm.class), any())).thenCallRealMethod();
        when(mockResourceService.toExternal(any(RDFTerm.class), any())).thenCallRealMethod();
        when(mockResourceService.add(any(IRI.class), any(Dataset.class))).thenReturn(completedFuture(null));
        when(mockResourceService.delete(any(Metadata.class))).thenReturn(completedFuture(null));
        when(mockResourceService.replace(any(Metadata.class), any(Dataset.class))).thenReturn(completedFuture(null));
        when(mockResourceService.create(any(Metadata.class), any(Dataset.class))).thenReturn(completedFuture(null));
        when(mockResourceService.unskolemize(any(Literal.class))).then(returnsFirstArg());
        when(mockResourceService.skolemize(any(Literal.class))).then(returnsFirstArg());
        when(mockResourceService.skolemize(any(IRI.class))).then(returnsFirstArg());
        when(mockResourceService.skolemize(any(BlankNode.class))).thenAnswer(inv ->
                rdf.createIRI(TRELLIS_BNODE_PREFIX + ((BlankNode) inv.getArgument(0)).uniqueReference()));
        when(mockResourceService.touch(any(IRI.class))).thenReturn(completedFuture(null));
        when(mockResource.stream()).thenAnswer(inv -> Stream.of(
                rdf.createQuad(PreferUserManaged, identifier, DC.title, rdf.createLiteral("A title")),
                rdf.createQuad(PreferServerManaged, identifier, DC.created,
                    rdf.createLiteral("2017-04-01T10:15:00Z", XSD.dateTime)),
                rdf.createQuad(PreferAccessControl, identifier, type, ACL.Authorization),
                rdf.createQuad(PreferAccessControl, identifier, ACL.mode, ACL.Control)));
        doCallRealMethod().when(mockResource).stream(anyCollection());
    }

    private void setUpMementoService() {
        when(mockMementoService.get(any(IRI.class), any(Instant.class)))
            .thenAnswer(inv -> completedFuture(mockVersionedResource));
        when(mockMementoService.get(eq(childIdentifier), any(Instant.class)))
            .thenAnswer(inv -> completedFuture(MISSING_RESOURCE));
        when(mockMementoService.get(eq(nonexistentIdentifier), any(Instant.class)))
            .thenAnswer(inv -> completedFuture(MISSING_RESOURCE));
        when(mockMementoService.get(eq(deletedIdentifier), any(Instant.class)))
            .thenAnswer(inv -> completedFuture(MISSING_RESOURCE));
        when(mockMementoService.get(eq(userDeletedIdentifier), any(Instant.class)))
            .thenAnswer(inv -> completedFuture(DELETED_RESOURCE));
        when(mockMementoService.get(eq(rdf.createIRI(TRELLIS_DATA_PREFIX + RANDOM_VALUE)), eq(MAX)))
            .thenAnswer(inv -> completedFuture(MISSING_RESOURCE));
        when(mockMementoService.get(eq(binaryIdentifier), any(Instant.class)))
            .thenReturn(completedFuture(mockBinaryVersionedResource));

        when(mockMementoService.mementos(any(IRI.class))).thenReturn(completedFuture(emptySortedSet()));
        when(mockMementoService.mementos(eq(identifier)))
                .thenReturn(completedFuture(new TreeSet<>(asList(
                    ofEpochSecond(timestamp - 2000), ofEpochSecond(timestamp - 1000), time))));
        when(mockMementoService.mementos(eq(binaryIdentifier))).thenReturn(completedFuture(new TreeSet<>(asList(
                ofEpochSecond(timestamp - 2000), ofEpochSecond(timestamp - 1000), time))));
        when(mockMementoService.mementos(eq(deletedIdentifier))).thenReturn(completedFuture(emptySortedSet()));
        when(mockMementoService.mementos(eq(userDeletedIdentifier))).thenReturn(completedFuture(emptySortedSet()));
        when(mockMementoService.put(any())).thenReturn(completedFuture(null));
        doCallRealMethod().when(mockMementoService).put(any(ResourceService.class), any(IRI.class));
    }

    private void setUpBinaryService() throws Exception {
        when(mockBinaryService.get(eq(binaryInternalIdentifier))).thenAnswer(inv -> completedFuture(mockBinary));
        when(mockBinary.getContent(eq(3), eq(10)))
                        .thenReturn(completedFuture(new ByteArrayInputStream("e input".getBytes(UTF_8))));
        when(mockBinary.getContent())
                        .thenReturn(completedFuture(new ByteArrayInputStream("Some input stream".getBytes(UTF_8))));
        when(mockBinaryService.setContent(any(BinaryMetadata.class), any(InputStream.class)))
        .thenAnswer(inv -> {
            readLines((InputStream) inv.getArguments()[1], UTF_8);
            return completedFuture(null);
        });
        when(mockBinaryService.purgeContent(any(IRI.class))).thenReturn(completedFuture(null));
        when(mockBinaryService.generateIdentifier()).thenReturn(RANDOM_VALUE);
    }

    private void setUpResources() {
        when(mockVersionedResource.getInteractionModel()).thenReturn(LDP.RDFSource);
        when(mockVersionedResource.getModified()).thenReturn(time);
        when(mockVersionedResource.getBinaryMetadata()).thenReturn(empty());
        when(mockVersionedResource.getIdentifier()).thenReturn(identifier);
        when(mockVersionedResource.getExtraLinkRelations()).thenAnswer(inv -> Stream.empty());

        when(mockBinaryVersionedResource.getInteractionModel()).thenReturn(LDP.NonRDFSource);
        when(mockBinaryVersionedResource.getModified()).thenReturn(time);
        when(mockBinaryVersionedResource.getBinaryMetadata()).thenReturn(of(testBinary));
        when(mockBinaryVersionedResource.getIdentifier()).thenReturn(binaryIdentifier);
        when(mockBinaryVersionedResource.getExtraLinkRelations()).thenAnswer(inv -> Stream.empty());

        when(mockBinaryResource.getInteractionModel()).thenReturn(LDP.NonRDFSource);
        when(mockBinaryResource.getModified()).thenReturn(time);
        when(mockBinaryResource.getBinaryMetadata()).thenReturn(of(testBinary));
        when(mockBinaryResource.getIdentifier()).thenReturn(binaryIdentifier);
        when(mockBinaryResource.getExtraLinkRelations()).thenAnswer(inv -> Stream.empty());

        when(mockResource.getContainer()).thenReturn(of(root));
        when(mockResource.getInteractionModel()).thenReturn(LDP.RDFSource);
        when(mockResource.getModified()).thenReturn(time);
        when(mockResource.getBinaryMetadata()).thenReturn(empty());
        when(mockResource.getIdentifier()).thenReturn(identifier);
        when(mockResource.getExtraLinkRelations()).thenAnswer(inv -> Stream.empty());

        when(mockRootResource.getInteractionModel()).thenReturn(LDP.BasicContainer);
        when(mockRootResource.getModified()).thenReturn(time);
        when(mockRootResource.getBinaryMetadata()).thenReturn(empty());
        when(mockRootResource.getIdentifier()).thenReturn(root);
        when(mockRootResource.getExtraLinkRelations()).thenAnswer(inv -> Stream.empty());
        when(mockRootResource.hasAcl()).thenReturn(true);
    }
}
