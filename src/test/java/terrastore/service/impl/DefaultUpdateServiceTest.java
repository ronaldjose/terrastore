/**
 * Copyright 2009 - 2010 Sergio Bossa (sergio.bossa@gmail.com)
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package terrastore.service.impl;

import java.util.HashMap;
import java.util.Map;
import org.easymock.classextension.EasyMock;
import org.junit.Test;
import terrastore.communication.Node;
import terrastore.communication.protocol.AddBucketCommand;
import terrastore.communication.protocol.PutValueCommand;
import terrastore.communication.protocol.RemoveBucketCommand;
import terrastore.communication.protocol.RemoveValueCommand;
import terrastore.communication.protocol.UpdateCommand;
import terrastore.router.Router;
import terrastore.store.operators.Function;
import terrastore.store.features.Update;
import terrastore.store.types.JsonValue;
import static org.easymock.classextension.EasyMock.*;

/**
 * @author Sergio Bossa
 */
public class DefaultUpdateServiceTest {

    private static final String JSON_VALUE = "{\"test\":\"test\"}";

    @Test
    public void testAddBucket() throws Exception {
        Node node = createMock(Node.class);
        Router router = createMock(Router.class);

        router.getLocalNode();
        expectLastCall().andReturn(node).once();
        node.send(EasyMock.<AddBucketCommand>anyObject());
        expectLastCall().andReturn(null).once();

        replay(node, router);

        DefaultUpdateService service = new DefaultUpdateService(router);
        service.addBucket("bucket");

        verify(node, router);
    }

    @Test
    public void testRemoveBucket() throws Exception {
        Node node = createMock(Node.class);
        Router router = createMock(Router.class);

        router.getLocalNode();
        expectLastCall().andReturn(node).once();
        node.send(EasyMock.<RemoveBucketCommand>anyObject());
        expectLastCall().andReturn(null).once();

        replay(node, router);

        DefaultUpdateService service = new DefaultUpdateService(router);
        service.removeBucket("bucket");

        verify(node, router);
    }

    @Test
    public void testPutValue() throws Exception {
        Node node = createMock(Node.class);
        Router router = createMock(Router.class);

        router.routeToNodeFor("bucket", "test1");
        expectLastCall().andReturn(node).once();
        node.send(EasyMock.<PutValueCommand>anyObject());
        expectLastCall().andReturn(null).once();

        replay(node, router);

        DefaultUpdateService service = new DefaultUpdateService(router);
        service.putValue("bucket", "test1", new JsonValue(JSON_VALUE.getBytes()));

        verify(node, router);
    }

    @Test
    public void testRemoveValue() throws Exception {
        Node node = createMock(Node.class);
        Router router = createMock(Router.class);

        router.routeToNodeFor("bucket", "test1");
        expectLastCall().andReturn(node).once();
        node.send(EasyMock.<RemoveValueCommand>anyObject());
        expectLastCall().andReturn(null).once();

        replay(node, router);

        DefaultUpdateService service = new DefaultUpdateService(router);
        service.removeValue("bucket", "test1");

        verify(node, router);
    }

    @Test
    public void testExecuteUpdate() throws Exception {
        Function function = new Function() {

            @Override
            public Map<String, Object> apply(String key, Map<String, Object> value, Map<String, Object> parameters) {
                return value;
            }
        };

        Node node = createMock(Node.class);
        Router router = createMock(Router.class);

        router.routeToNodeFor("bucket", "test1");
        expectLastCall().andReturn(node).once();
        node.send(EasyMock.<UpdateCommand>anyObject());
        expectLastCall().andReturn(null).once();

        replay(node, router);

        Map<String, Function> functions = new HashMap<String, Function>();
        functions.put("update", function);

        DefaultUpdateService service = new DefaultUpdateService(router);
        service.setFunctions(functions);
        service.executeUpdate("bucket", "test1", new Update("update", 1000, new HashMap<String, Object>()));

        verify(node, router);
    }
}