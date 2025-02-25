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
package org.trellisldp.app;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.trellisldp.constraint.LdpConstraintService;

public class DefaultConstraintServicesTest {

    @Test
    public void testEmptyServices() {
        final ConstraintServices svcs = new DefaultConstraintServices(emptyList());
        assertFalse(svcs.iterator().hasNext());
    }

    @Test
    public void testSingleServices() {
        final ConstraintServices svcs = new DefaultConstraintServices(singletonList(new LdpConstraintService()));
        assertTrue(svcs.iterator().hasNext());
    }
}
