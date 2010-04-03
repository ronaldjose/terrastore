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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.easymock.classextension.EasyMock;
import org.junit.Test;
import terrastore.common.ErrorMessage;
import terrastore.communication.Cluster;
import terrastore.communication.Node;
import terrastore.communication.ProcessingException;
import terrastore.communication.protocol.GetKeysCommand;
import terrastore.communication.protocol.RangeQueryCommand;
import terrastore.communication.protocol.GetBucketsCommand;
import terrastore.communication.protocol.GetValueCommand;
import terrastore.communication.protocol.GetValuesCommand;
import terrastore.router.MissingRouteException;
import terrastore.router.Router;
import terrastore.service.QueryOperationException;
import terrastore.store.Value;
import terrastore.store.features.Predicate;
import terrastore.store.features.Range;
import terrastore.store.operators.Comparator;
import terrastore.store.operators.Condition;
import terrastore.store.types.JsonValue;
import terrastore.util.collect.Maps;
import terrastore.util.collect.Sets;
import static org.junit.Assert.*;
import static org.easymock.classextension.EasyMock.*;

/**
 * @author Sergio Bossa
 */
public class DefaultQueryServiceTest {

    private static final String JSON_VALUE = "{\"test\":\"test\"}";

    @Test
    public void testGetBuckets() throws Exception {
        Node node = createMock(Node.class);
        Router router = createMock(Router.class);

        router.routeToLocalNode();
        expectLastCall().andReturn(node).once();
        node.send(EasyMock.<GetBucketsCommand>anyObject());
        expectLastCall().andReturn(Sets.hash("test1", "test2")).once();
        replay(node, router);

        DefaultQueryService service = new DefaultQueryService(router);
        Collection<String> result = service.getBuckets();
        assertEquals(2, result.size());
        assertTrue(result.contains("test1"));
        assertTrue(result.contains("test2"));

        verify(node, router);
    }

    @Test
    public void testGetValue() throws Exception {
        Value value = new JsonValue(JSON_VALUE.getBytes());

        Node node = createMock(Node.class);
        Router router = createMock(Router.class);

        router.routeToNodeFor("bucket", "test1");
        expectLastCall().andReturn(node).once();
        node.send(EasyMock.<GetValueCommand>anyObject());
        expectLastCall().andReturn(value).once();

        replay(node, router);

        DefaultQueryService service = new DefaultQueryService(router);
        assertEquals(JSON_VALUE, new String(service.getValue("bucket", "test1", new Predicate(null)).getBytes()));

        verify(node, router);
    }

    @Test
    public void testGetAllValues() throws Exception {
        Cluster cluster1 = createMock(Cluster.class);
        Cluster cluster2 = createMock(Cluster.class);
        Node node1 = createMock(Node.class);
        Node node2 = createMock(Node.class);
        Router router = createMock(Router.class);
        Map<Node, Set<String>> nodeToKeys = new HashMap<Node, Set<String>>();
        nodeToKeys.put(node1, new HashSet<String>(Arrays.asList("test1")));
        nodeToKeys.put(node2, new HashSet<String>(Arrays.asList("test2")));
        Map<String, Value> values1 = new HashMap<String, Value>();
        values1.put("test1", new JsonValue(JSON_VALUE.getBytes()));
        Map<String, Value> values2 = new HashMap<String, Value>();
        values2.put("test2", new JsonValue(JSON_VALUE.getBytes()));

        router.broadcastRoute();
        expectLastCall().andReturn(Maps.hash(new Cluster[]{cluster1, cluster2}, new Set[]{Sets.hash(node1), Sets.hash(node2)})).once();

        node1.send(EasyMock.<GetKeysCommand>anyObject());
        expectLastCall().andReturn(Sets.hash("test1")).once();
        node2.send(EasyMock.<GetKeysCommand>anyObject());
        expectLastCall().andReturn(Sets.hash("test2")).once();

        router.routeToNodesFor("bucket", Sets.hash("test1", "test2"));
        expectLastCall().andReturn(nodeToKeys).once();

        node1.send(EasyMock.<GetValuesCommand>anyObject());
        expectLastCall().andReturn(values1).once();
        node2.send(EasyMock.<GetValuesCommand>anyObject());
        expectLastCall().andReturn(values2).once();

        replay(cluster1, cluster2, node1, node2, router);

        DefaultQueryService service = new DefaultQueryService(router);
        Map<String, Value> result = service.getAllValues("bucket", 0);
        assertEquals(2, result.size());
        assertEquals(JSON_VALUE, new String(result.get("test1").getBytes()));
        assertEquals(JSON_VALUE, new String(result.get("test2").getBytes()));

        verify(cluster1, cluster2, node1, node2, router);
    }

    @Test
    public void testGetAllValuesSucceedsBySkippingFailingNodes() throws Exception {
        Cluster cluster1 = createMock(Cluster.class);
        Node node1 = createMock(Node.class);
        Node node2 = createMock(Node.class);
        Router router = createMock(Router.class);
        Map<Node, Set<String>> nodeToKeys = new HashMap<Node, Set<String>>();
        nodeToKeys.put(node2, new HashSet<String>(Arrays.asList("test1", "test2")));
        Map<String, Value> values = new HashMap<String, Value>();
        values.put("test1", new JsonValue(JSON_VALUE.getBytes()));
        values.put("test2", new JsonValue(JSON_VALUE.getBytes()));

        router.broadcastRoute();
        expectLastCall().andReturn(Maps.hash(new Cluster[]{cluster1}, new Set[]{Sets.linked(node1, node2)})).once();

        node1.send(EasyMock.<GetKeysCommand>anyObject());
        expectLastCall().andThrow(new ProcessingException(new ErrorMessage(0, ""))).once();
        node2.send(EasyMock.<GetKeysCommand>anyObject());
        expectLastCall().andReturn(Sets.hash("test1", "test2")).once();

        router.routeToNodesFor("bucket", Sets.hash("test1", "test2"));
        expectLastCall().andReturn(nodeToKeys).once();

        node2.send(EasyMock.<GetValuesCommand>anyObject());
        expectLastCall().andReturn(values).once();

        replay(cluster1, node1, node2, router);

        DefaultQueryService service = new DefaultQueryService(router);
        Map<String, Value> result = service.getAllValues("bucket", 0);
        assertEquals(2, result.size());
        assertEquals(JSON_VALUE, new String(result.get("test1").getBytes()));
        assertEquals(JSON_VALUE, new String(result.get("test2").getBytes()));

        verify(cluster1, node1, node2, router);
    }

    @Test(expected = QueryOperationException.class)
    public void testGetAllValuesFailsWhenAllNodesFail() throws Exception {
        Cluster cluster1 = createMock(Cluster.class);
        Node node1 = createMock(Node.class);
        Node node2 = createMock(Node.class);
        Router router = createMock(Router.class);
        Map<Node, Set<String>> nodeToKeys = new HashMap<Node, Set<String>>();
        nodeToKeys.put(node2, new HashSet<String>(Arrays.asList("test1", "test2")));
        Map<String, Value> values = new HashMap<String, Value>();
        values.put("test1", new JsonValue(JSON_VALUE.getBytes()));
        values.put("test2", new JsonValue(JSON_VALUE.getBytes()));

        router.broadcastRoute();
        expectLastCall().andReturn(Maps.hash(new Cluster[]{cluster1}, new Set[]{Sets.linked(node1, node2)})).once();

        node1.send(EasyMock.<GetKeysCommand>anyObject());
        expectLastCall().andThrow(new ProcessingException(new ErrorMessage(0, ""))).once();
        node2.send(EasyMock.<GetKeysCommand>anyObject());
        expectLastCall().andThrow(new ProcessingException(new ErrorMessage(0, ""))).once();

        replay(cluster1, node1, node2, router);

        try {
            DefaultQueryService service = new DefaultQueryService(router);
            service.getAllValues("bucket", 0);
        } finally {
            verify(cluster1, node1, node2, router);
        }
    }

    @Test(expected = QueryOperationException.class)
    public void testGetAllValuesFailsWithMissingRoute() throws Exception {
        Router router = createMock(Router.class);

        router.broadcastRoute();
        expectLastCall().andThrow(new MissingRouteException(new ErrorMessage(0, ""))).once();

        replay(router);

        try {
            DefaultQueryService service = new DefaultQueryService(router);
            service.getAllValues("bucket", 0);
        } finally {
            verify(router);
        }
    }

    @Test
    public void testQueryByRangeWithNoPredicate() throws Exception {
        Comparator stringComparator = new Comparator() {

            @Override
            public int compare(String o1, String o2) {
                return o1.compareTo(o2);
            }
        };

        Cluster cluster1 = createMock(Cluster.class);
        Cluster cluster2 = createMock(Cluster.class);
        Node node1 = createMock(Node.class);
        Node node2 = createMock(Node.class);
        Router router = createMock(Router.class);
        Map<Node, Set<String>> nodeToKeys = new HashMap<Node, Set<String>>();
        nodeToKeys.put(node1, new HashSet<String>(Arrays.asList("test1")));
        nodeToKeys.put(node2, new HashSet<String>(Arrays.asList("test2")));
        Map<String, Value> values1 = new HashMap<String, Value>();
        values1.put("test1", new JsonValue(JSON_VALUE.getBytes()));
        Map<String, Value> values2 = new HashMap<String, Value>();
        values2.put("test2", new JsonValue(JSON_VALUE.getBytes()));

        router.broadcastRoute();
        expectLastCall().andReturn(Maps.hash(new Cluster[]{cluster1, cluster2}, new Set[]{Sets.hash(node1), Sets.hash(node2)})).once();

        node1.send(EasyMock.<RangeQueryCommand>anyObject());
        expectLastCall().andReturn(Sets.hash("test1")).once();
        node2.send(EasyMock.<RangeQueryCommand>anyObject());
        expectLastCall().andReturn(Sets.hash("test2")).once();

        router.routeToNodesFor("bucket", Sets.hash("test1", "test2"));
        expectLastCall().andReturn(nodeToKeys).once();

        node1.send(EasyMock.<GetValuesCommand>anyObject());
        expectLastCall().andReturn(values1).once();
        node2.send(EasyMock.<GetValuesCommand>anyObject());
        expectLastCall().andReturn(values2).once();

        replay(cluster1, cluster2, node1, node2, router);

        Map<String, Comparator> comparators = new HashMap<String, Comparator>();
        comparators.put("order", stringComparator);

        DefaultQueryService service = new DefaultQueryService(router);
        service.setComparators(comparators);

        Map<String, Value> result = service.queryByRange("bucket", new Range("test1", "test2", 0, "order"), new Predicate(null), 0);
        assertEquals(2, result.size());
        assertEquals("test1", result.keySet().toArray()[0]);
        assertEquals("test2", result.keySet().toArray()[1]);

        verify(cluster1, cluster2, node1, node2, router);
    }

    @Test
    public void testQueryByRangeWithPredicate() throws Exception {
        Comparator stringComparator = new Comparator() {

            @Override
            public int compare(String o1, String o2) {
                return o1.compareTo(o2);
            }
        };

        Condition trueCondition = new Condition() {

            @Override
            public boolean isSatisfied(String key, Map<String, Object> value, String expression) {
                return true;
            }
        };

        Cluster cluster1 = createMock(Cluster.class);
        Cluster cluster2 = createMock(Cluster.class);
        Node node1 = createMock(Node.class);
        Node node2 = createMock(Node.class);
        Router router = createMock(Router.class);
        Map<Node, Set<String>> nodeToKeys = new HashMap<Node, Set<String>>();
        nodeToKeys.put(node1, new HashSet<String>(Arrays.asList("test1")));
        nodeToKeys.put(node2, new HashSet<String>(Arrays.asList("test2")));
        Map<String, Value> values1 = new HashMap<String, Value>();
        values1.put("test1", new JsonValue(JSON_VALUE.getBytes()));
        Map<String, Value> values2 = new HashMap<String, Value>();
        values2.put("test2", new JsonValue(JSON_VALUE.getBytes()));

        router.broadcastRoute();
        expectLastCall().andReturn(Maps.hash(new Cluster[]{cluster1, cluster2}, new Set[]{Sets.hash(node1), Sets.hash(node2)})).once();

        node1.send(EasyMock.<RangeQueryCommand>anyObject());
        expectLastCall().andReturn(Sets.hash("test1")).once();
        node2.send(EasyMock.<RangeQueryCommand>anyObject());
        expectLastCall().andReturn(Sets.hash("test2")).once();

        router.routeToNodesFor("bucket", Sets.hash("test1", "test2"));
        expectLastCall().andReturn(nodeToKeys).once();

        node1.send(EasyMock.<GetValuesCommand>anyObject());
        expectLastCall().andReturn(values1).once();
        node2.send(EasyMock.<GetValuesCommand>anyObject());
        expectLastCall().andReturn(values2).once();

        replay(cluster1, cluster2, node1, node2, router);

        Map<String, Comparator> comparators = new HashMap<String, Comparator>();
        comparators.put("order", stringComparator);
        Map<String, Condition> conditions = new HashMap<String, Condition>();
        conditions.put("test", trueCondition);

        DefaultQueryService service = new DefaultQueryService(router);
        service.setComparators(comparators);
        service.setConditions(conditions);

        Map<String, Value> result = service.queryByRange("bucket", new Range("test1", "test2", 0, "order"), new Predicate("test:true"), 0);
        assertEquals(2, result.size());
        assertEquals("test1", result.keySet().toArray()[0]);
        assertEquals("test2", result.keySet().toArray()[1]);

        verify(cluster1, cluster2, node1, node2, router);
    }

    @Test(expected = QueryOperationException.class)
    public void testQueryByRangeWithPredicateFailsDueToNoConditionFound() throws Exception {
        Comparator stringComparator = new Comparator() {

            @Override
            public int compare(String o1, String o2) {
                return o1.compareTo(o2);
            }
        };

        Condition trueCondition = new Condition() {

            @Override
            public boolean isSatisfied(String key, Map<String, Object> value, String expression) {
                return true;
            }
        };

        Router router = createMock(Router.class);

        replay(router);

        Map<String, Comparator> comparators = new HashMap<String, Comparator>();
        comparators.put("order", stringComparator);
        Map<String, Condition> conditions = new HashMap<String, Condition>();
        conditions.put("true", trueCondition);

        DefaultQueryService service = new DefaultQueryService(router);
        service.setComparators(comparators);
        service.setConditions(conditions);

        try {
            service.queryByRange("bucket", new Range("test1", "test2", 0, "order"), new Predicate("notfound:true"), 0);
        } finally {
            verify(router);
        }
    }

    @Test
    public void testQueryByRangeSucceedsBySkippingFailingNodes() throws Exception {
        Comparator stringComparator = new Comparator() {

            @Override
            public int compare(String o1, String o2) {
                return o1.compareTo(o2);
            }
        };

        Cluster cluster1 = createMock(Cluster.class);
        Node node1 = createMock(Node.class);
        Node node2 = createMock(Node.class);
        Router router = createMock(Router.class);
        Map<Node, Set<String>> nodeToKeys = new HashMap<Node, Set<String>>();
        nodeToKeys.put(node2, new HashSet<String>(Arrays.asList("test1", "test2")));
        Map<String, Value> values = new HashMap<String, Value>();
        values.put("test1", new JsonValue(JSON_VALUE.getBytes()));
        values.put("test2", new JsonValue(JSON_VALUE.getBytes()));

        router.broadcastRoute();
        expectLastCall().andReturn(Maps.hash(new Cluster[]{cluster1}, new Set[]{Sets.linked(node1, node2)})).once();

        node1.send(EasyMock.<RangeQueryCommand>anyObject());
        expectLastCall().andThrow(new ProcessingException(new ErrorMessage(0, ""))).once();
        node2.send(EasyMock.<RangeQueryCommand>anyObject());
        expectLastCall().andReturn(Sets.hash("test1", "test2")).once();

        router.routeToNodesFor("bucket", Sets.hash("test1", "test2"));
        expectLastCall().andReturn(nodeToKeys).once();

        node2.send(EasyMock.<GetValuesCommand>anyObject());
        expectLastCall().andReturn(values).once();

        replay(cluster1, node1, node2, router);

        Map<String, Comparator> comparators = new HashMap<String, Comparator>();
        comparators.put("order", stringComparator);
        DefaultQueryService service = new DefaultQueryService(router);
        service.setComparators(comparators);
        Map<String, Value> result = service.queryByRange("bucket", new Range("test1", "test2", 0, "order"), new Predicate(null), 0);
        assertEquals(2, result.size());
        assertEquals(JSON_VALUE, new String(result.get("test1").getBytes()));
        assertEquals(JSON_VALUE, new String(result.get("test2").getBytes()));

        verify(cluster1, node1, node2, router);
    }

    @Test(expected = QueryOperationException.class)
    public void testQueryByRangeFailsWhenAllNodesFail() throws Exception {
        Comparator stringComparator = new Comparator() {

            @Override
            public int compare(String o1, String o2) {
                return o1.compareTo(o2);
            }
        };

        Cluster cluster1 = createMock(Cluster.class);
        Node node1 = createMock(Node.class);
        Node node2 = createMock(Node.class);
        Router router = createMock(Router.class);
        Map<Node, Set<String>> nodeToKeys = new HashMap<Node, Set<String>>();
        nodeToKeys.put(node2, new HashSet<String>(Arrays.asList("test1", "test2")));
        Map<String, Value> values = new HashMap<String, Value>();
        values.put("test1", new JsonValue(JSON_VALUE.getBytes()));
        values.put("test2", new JsonValue(JSON_VALUE.getBytes()));

        router.broadcastRoute();
        expectLastCall().andReturn(Maps.hash(new Cluster[]{cluster1}, new Set[]{Sets.linked(node1, node2)})).once();

        node1.send(EasyMock.<RangeQueryCommand>anyObject());
        expectLastCall().andThrow(new ProcessingException(new ErrorMessage(0, ""))).once();
        node2.send(EasyMock.<RangeQueryCommand>anyObject());
        expectLastCall().andThrow(new ProcessingException(new ErrorMessage(0, ""))).once();

        replay(cluster1, node1, node2, router);

        try {
            Map<String, Comparator> comparators = new HashMap<String, Comparator>();
            comparators.put("order", stringComparator);
            DefaultQueryService service = new DefaultQueryService(router);
            service.setComparators(comparators);
            service.queryByRange("bucket", new Range("test1", "test2", 0, "order"), new Predicate(null), 0);
        } finally {
            verify(cluster1, node1, node2, router);
        }
    }

    @Test(expected = QueryOperationException.class)
    public void testQueryByRangeFailsWithMissingRoute() throws Exception {
        Comparator stringComparator = new Comparator() {

            @Override
            public int compare(String o1, String o2) {
                return o1.compareTo(o2);
            }
        };

        Router router = createMock(Router.class);

        router.broadcastRoute();
        expectLastCall().andThrow(new MissingRouteException(new ErrorMessage(0, ""))).once();

        replay(router);

        try {
            Map<String, Comparator> comparators = new HashMap<String, Comparator>();
            comparators.put("order", stringComparator);
            DefaultQueryService service = new DefaultQueryService(router);
            service.setComparators(comparators);
            service.queryByRange("bucket", new Range("test1", "test2", 0, "order"), new Predicate(null), 0);
        } finally {
            verify(router);
        }
    }

    @Test
    public void testQueryByPredicate() throws Exception {
        Condition trueCondition = new Condition() {

            @Override
            public boolean isSatisfied(String key, Map<String, Object> value, String expression) {
                return true;
            }
        };

        Cluster cluster1 = createMock(Cluster.class);
        Cluster cluster2 = createMock(Cluster.class);
        Node node1 = createMock(Node.class);
        Node node2 = createMock(Node.class);
        Router router = createMock(Router.class);
        Map<Node, Set<String>> nodeToKeys = new HashMap<Node, Set<String>>();
        nodeToKeys.put(node1, new HashSet<String>(Arrays.asList("test1")));
        nodeToKeys.put(node2, new HashSet<String>(Arrays.asList("test2")));
        Map<String, Value> values1 = new HashMap<String, Value>();
        values1.put("test1", new JsonValue(JSON_VALUE.getBytes()));
        Map<String, Value> values2 = new HashMap<String, Value>();
        values2.put("test2", new JsonValue(JSON_VALUE.getBytes()));

        router.broadcastRoute();
        expectLastCall().andReturn(Maps.hash(new Cluster[]{cluster1, cluster2}, new Set[]{Sets.hash(node1), Sets.hash(node2)})).once();

        node1.send(EasyMock.<GetKeysCommand>anyObject());
        expectLastCall().andReturn(Sets.hash("test1")).once();
        node2.send(EasyMock.<GetKeysCommand>anyObject());
        expectLastCall().andReturn(Sets.hash("test2")).once();

        router.routeToNodesFor("bucket", Sets.hash("test1", "test2"));
        expectLastCall().andReturn(nodeToKeys).once();

        node1.send(EasyMock.<GetValuesCommand>anyObject());
        expectLastCall().andReturn(values1).once();
        node2.send(EasyMock.<GetValuesCommand>anyObject());
        expectLastCall().andReturn(values2).once();

        replay(cluster1, cluster2, node1, node2, router);

        Map<String, Condition> conditions = new HashMap<String, Condition>();
        conditions.put("test", trueCondition);

        DefaultQueryService service = new DefaultQueryService(router);
        service.setConditions(conditions);

        Map<String, Value> result = service.queryByPredicate("bucket", new Predicate("test:true"));
        assertEquals(2, result.size());
        assertTrue(result.containsKey("test1"));
        assertTrue(result.containsKey("test2"));

        verify(cluster1, cluster2, node1, node2, router);
    }

    @Test(expected = QueryOperationException.class)
    public void testQueryByPredicateFailsDueToNoConditionFound() throws Exception {
        Condition trueCondition = new Condition() {

            @Override
            public boolean isSatisfied(String key, Map<String, Object> value, String expression) {
                return true;
            }
        };

        Router router = createMock(Router.class);

        replay(router);

        Map<String, Condition> conditions = new HashMap<String, Condition>();
        conditions.put("test", trueCondition);

        DefaultQueryService service = new DefaultQueryService(router);
        service.setConditions(conditions);

        try {
            service.queryByPredicate("bucket", new Predicate("notfound:true"));
        } finally {
            verify(router);
        }
    }
}
