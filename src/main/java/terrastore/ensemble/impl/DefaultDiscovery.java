package terrastore.ensemble.impl;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import terrastore.common.ErrorMessage;
import terrastore.communication.Cluster;
import terrastore.communication.Node;
import terrastore.communication.ProcessingException;
import terrastore.ensemble.View.Member;
import terrastore.communication.protocol.MembershipCommand;
import terrastore.ensemble.Discovery;
import terrastore.ensemble.RemoteNodeFactory;
import terrastore.ensemble.View;
import terrastore.router.MissingRouteException;
import terrastore.router.Router;

/**
 * @author Sergio Bossa
 */
public class DefaultDiscovery implements Discovery {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultDiscovery.class);
    //
    private final Router router;
    private final RemoteNodeFactory nodeFactory;
    private final Map<Cluster, List<Node>> perClusterNodes;
    private final Map<Cluster, View> perClusterViews;

    public DefaultDiscovery(Router router, RemoteNodeFactory nodeFactory) {
        this.router = router;
        this.nodeFactory = nodeFactory;
        this.perClusterNodes = new HashMap<Cluster, List<Node>>();
        this.perClusterViews = new HashMap<Cluster, View>();
    }

    public synchronized void join(Cluster cluster, String seed) throws MissingRouteException, ProcessingException {
        String[] hostPortPair = seed.split(":");
        Node bootstrap = nodeFactory.makeNode(hostPortPair[0], Integer.parseInt(hostPortPair[1]), seed);
        try {
            bootstrap.connect();
            View view = requestMembership(cluster, Arrays.asList(bootstrap));
            calculateView(cluster, view);
        } catch (Exception ex) {
            LOG.warn(ex.getMessage(), ex);
            throw new MissingRouteException(new ErrorMessage(ErrorMessage.UNAVAILABLE_ERROR_CODE, "Seed is unavailable: " + seed));
        } finally {
            bootstrap.disconnect();
        }
    }

    public synchronized void update() throws MissingRouteException, ProcessingException {
        for (Map.Entry<Cluster, List<Node>> entry : perClusterNodes.entrySet()) {
            Cluster cluster = entry.getKey();
            List<Node> nodes = entry.getValue();
            View view = requestMembership(cluster, nodes);
            calculateView(cluster, view);
        }
    }

    private View requestMembership(Cluster cluster, List<Node> contactNodes) throws MissingRouteException, ProcessingException {
        View view = null;
        Iterator<Node> nodeIterator = contactNodes.iterator();
        boolean successful = false;
        while (!successful && nodeIterator.hasNext()) {
            Node node = nodeIterator.next();
            view = node.<View>send(new MembershipCommand());
            successful = true;
        }
        if (view != null) {
            return view;
        } else {
            throw new MissingRouteException(new ErrorMessage(ErrorMessage.UNAVAILABLE_ERROR_CODE, "Missing route to cluster: " + cluster));
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
            Set<View.Member> leavingMembers = Sets.difference(currentView.getMembers(), updatedView.getMembers());
            Set<View.Member> joiningMembers = Sets.difference(updatedView.getMembers(), currentView.getMembers());
            for (View.Member member : leavingMembers) {
                Node node = findNode(currentNodes, member);
                router.removeRouteTo(cluster, node);
                node.disconnect();
                currentNodes.remove(node);
            }
            for (View.Member member : joiningMembers) {
                Node node = nodeFactory.makeNode(member.getHost(), member.getPort(), member.getName());
                router.addRouteTo(cluster, node);
                node.connect();
                currentNodes.add(node);
            }
        } else {
            perClusterViews.put(cluster, updatedView);
            for (View.Member member : updatedView.getMembers()) {
                Node node = nodeFactory.makeNode(member.getHost(), member.getPort(), member.getName());
                router.addRouteTo(cluster, node);
                node.connect();
                currentNodes.add(node);
            }
        }
    }

    private Node findNode(List<Node> nodes, Member member) {
        try {
            return Iterables.find(nodes, new NodeFinder(member.getName()));
        } catch (NoSuchElementException ex) {
            return null;
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
