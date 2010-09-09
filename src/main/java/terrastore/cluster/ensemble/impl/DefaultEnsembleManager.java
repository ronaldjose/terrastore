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
package terrastore.cluster.ensemble.impl;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import terrastore.common.ErrorMessage;
import terrastore.communication.Cluster;
import terrastore.communication.Node;
import terrastore.communication.ProcessingException;
import terrastore.communication.RemoteNodeFactory;
import terrastore.cluster.ensemble.impl.View.Member;
import terrastore.communication.protocol.MembershipCommand;
import terrastore.cluster.ensemble.EnsembleManager;
import terrastore.cluster.ensemble.EnsembleConfiguration;
import terrastore.cluster.ensemble.EnsembleScheduler;
import terrastore.router.MissingRouteException;
import terrastore.router.Router;

/**
 * Default {@link terrastore.ensemble.EnsembleManager} implementation.
 *
 * @author Sergio Bossa
 */
public class DefaultEnsembleManager implements EnsembleManager {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultEnsembleManager.class);
    //
    private final EnsembleScheduler ensembleScheduler;
    private final Router router;
    private final RemoteNodeFactory remoteNodeFactory;
    //
    private final ConcurrentMap<Cluster, Node> bootstrapNodes;
    private final ConcurrentMap<Cluster, List<Node>> perClusterNodes;
    private final ConcurrentMap<Cluster, View> perClusterViews;

    public DefaultEnsembleManager(EnsembleScheduler ensembleScheduler, Router router, RemoteNodeFactory nodeFactory) {
        this.ensembleScheduler = ensembleScheduler;
        this.router = router;
        this.remoteNodeFactory = nodeFactory;
        this.bootstrapNodes = new ConcurrentHashMap<Cluster, Node>();
        this.perClusterNodes = new ConcurrentHashMap<Cluster, List<Node>>();
        this.perClusterViews = new ConcurrentHashMap<Cluster, View>();
    }

    @Override
    public final synchronized void join(Cluster cluster, String seed, EnsembleConfiguration ensembleConfiguration) throws MissingRouteException, ProcessingException {
        if (!cluster.isLocal()) {
            String[] hostPortPair = seed.split(":");
            bootstrapNodes.put(cluster, remoteNodeFactory.makeRemoteNode(hostPortPair[0], Integer.parseInt(hostPortPair[1]), seed));
            ensembleScheduler.schedule(cluster, this, ensembleConfiguration);
        } else {
            throw new IllegalArgumentException("No need to join local cluster: " + cluster);
        }
    }

    @Override
    public synchronized final void update(Cluster cluster) throws MissingRouteException, ProcessingException {
        try {
            List<Node> nodes = perClusterNodes.get(cluster);
            if (nodes == null || nodes.isEmpty()) {
                LOG.debug("Bootstrapping discovery for cluster {}", cluster);
                Node bootstrap = bootstrapNodes.get(cluster);
                if (bootstrap != null) {
                    try {
                        bootstrap.connect();
                        View view = requestMembership(cluster, Arrays.asList(bootstrap));
                        calculateView(cluster, view);
                    } finally {
                        bootstrap.disconnect();
                    }
                }
            } else {
                LOG.debug("Updating cluster view for {}", cluster);
                View view = requestMembership(cluster, nodes);
                calculateView(cluster, view);
            }
        } catch (Exception ex) {
            LOG.info("Error updating membership information for cluster {}", cluster);
            LOG.debug(ex.getMessage(), ex);
        }
    }

    @Override
    public synchronized void shutdown() {
        cancelScheduler();
        disconnectAllClustersNodes();
    }

    @Override
    public EnsembleScheduler getEnsembleScheduler() {
        return ensembleScheduler;
    }

    @Override
    public Router getRouter() {
        return router;
    }

    @Override
    public RemoteNodeFactory getRemoteNodeFactory() {
        return remoteNodeFactory;
    }
    
    private void cancelScheduler() {
        ensembleScheduler.shutdown();
    }

    private View requestMembership(Cluster cluster, List<Node> contactNodes) throws MissingRouteException, ProcessingException {
        Iterator<Node> nodeIterator = contactNodes.iterator();
        boolean successful = false;
        View view = null;
        while (!successful && nodeIterator.hasNext()) {
            Node node = null;
            try {
                node = nodeIterator.next();
                view = node.<View>send(new MembershipCommand());
                successful = true;
                LOG.debug("Updated cluster view from node {}:{}", cluster, node);
            } catch (Exception ex) {
                LOG.warn("Failed to contact node {}:{} for updating cluster view!", cluster, node);
                router.removeRouteTo(cluster, node);
                node.disconnect();
                nodeIterator.remove();
                LOG.info("Disconnected remote node {}:{}", cluster, node);
            }
        }
        if (successful) {
            return view;
        } else {
            throw new MissingRouteException(new ErrorMessage(ErrorMessage.UNAVAILABLE_ERROR_CODE, "No route to cluster " + cluster));
        }
    }

    private void calculateView(Cluster cluster, View updatedView) {
        List<Node> currentNodes = perClusterNodes.get(cluster);
        if (currentNodes == null) {
            currentNodes = new LinkedList<Node>();
            perClusterNodes.put(cluster, currentNodes);
        }
        //
        View currentView = perClusterViews.get(cluster);
        if (currentView != null) {
            LOG.debug("Current view for cluster {} : {}", cluster, currentView);
            LOG.debug("Updated view for cluster {} : {}", cluster, updatedView);
            Set<View.Member> leavingMembers = Sets.difference(currentView.getMembers(), updatedView.getMembers());
            Set<View.Member> joiningMembers = Sets.difference(updatedView.getMembers(), currentView.getMembers());
            for (View.Member member : leavingMembers) {
                Node node = findNode(currentNodes, member);
                if (node != null) {
                    router.removeRouteTo(cluster, node);
                    node.disconnect();
                    currentNodes.remove(node);
                    LOG.info("Disconnected remote node {}:{}", cluster, node);
                }
            }
            for (View.Member member : joiningMembers) {
                Node node = remoteNodeFactory.makeRemoteNode(member.getHost(), member.getPort(), member.getName());
                router.addRouteTo(cluster, node);
                node.connect();
                currentNodes.add(node);
                LOG.info("Joining remote node {}:{}", cluster, node);
            }
        } else {
            LOG.debug("No current view for cluster {}", cluster);
            LOG.debug("Updated view for cluster {} :  {}", cluster, updatedView);
            for (View.Member member : updatedView.getMembers()) {
                Node node = remoteNodeFactory.makeRemoteNode(member.getHost(), member.getPort(), member.getName());
                router.addRouteTo(cluster, node);
                node.connect();
                currentNodes.add(node);
                LOG.info("Joining remote node {}:{}", cluster, node);
            }
        }
        perClusterViews.put(cluster, updatedView);
    }

    private Node findNode(List<Node> nodes, Member member) {
        try {
            return Iterables.find(nodes, new NodeFinder(member.getName()));
        } catch (NoSuchElementException ex) {
            return null;
        }
    }

    private void disconnectAllClustersNodes() {
        for (List<Node> nodes : perClusterNodes.values()) {
            for (Node node : nodes) {
                try {
                    node.disconnect();
                } catch (Exception ex) {
                }
            }
        }
    }

    private static class NodeFinder implements Predicate<Node> {

        private final String name;

        public NodeFinder(String name) {
            this.name = name;
        }

        @Override
        public boolean apply(Node node) {
            return node.getName().equals(name);
        }
    }
}