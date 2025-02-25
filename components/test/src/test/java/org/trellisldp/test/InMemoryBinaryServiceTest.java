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

package org.trellisldp.test;

import static org.apache.commons.io.IOUtils.toByteArray;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDF;
import org.junit.jupiter.api.Test;
import org.trellisldp.api.Binary;
import org.trellisldp.api.BinaryMetadata;
import org.trellisldp.api.BinaryService;
import org.trellisldp.api.TrellisUtils;

class InMemoryBinaryServiceTest {

    private static final RDF rdfFactory = TrellisUtils.getInstance();

    private final BinaryService testService = new InMemoryBinaryService();

    @Test
    void storeAndRetrieve() throws InterruptedException, ExecutionException, IOException {
        final String uuid = testService.generateIdentifier();
        final IRI id = rdfFactory.createIRI(uuid);
        final BinaryMetadata metadata = BinaryMetadata.builder(id).mimeType("mime/type").build();
        final byte[] answer = new byte[] { 1, 2, 3 };
        final InputStream stream = new ByteArrayInputStream(answer);
        testService.setContent(metadata, stream);
        final Binary binary = testService.get(id).toCompletableFuture().get();
        final byte[] result;
        try (InputStream bytes = binary.getContent().toCompletableFuture().get()) {
            result = toByteArray(bytes);
        }
        assertArrayEquals(answer, result);
    }

    @Test
    void storeAndRetrievePart() throws InterruptedException, ExecutionException, IOException {
        final String uuid = testService.generateIdentifier();
        final IRI id = rdfFactory.createIRI(uuid);
        final BinaryMetadata metadata = BinaryMetadata.builder(id).mimeType("mime/type").build();
        final byte[] fullAnswer = new byte[] { 1, 2, 3 };
        final InputStream stream = new ByteArrayInputStream(fullAnswer);
        testService.setContent(metadata, stream);
        final Binary binary = testService.get(id).toCompletableFuture().get();
        final byte[] result;
        try (InputStream bytes = binary.getContent(0, 1).toCompletableFuture().get()) {
            result = toByteArray(bytes);
        }
        final byte[] answer = new byte[] { 1, 2 };
        assertArrayEquals(answer, result);
    }
}
