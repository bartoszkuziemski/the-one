package routing;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import routing.bsk.RoutingTableEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class CustomRouter extends ActiveRouter {

    private final List<RoutingTableEntry> routingTable = new ArrayList<>();

    public CustomRouter(ActiveRouter r) {
        super(r);
        r.deleteDelivered = true;
    }

    public CustomRouter(Settings s) {
        super(s);
    }

    @Override
    public void update() {
        super.update();
        if (isTransferring() || !canStartTransfer()) {
            return; // transferring, don't try other connections yet
        }

        // Try first the messages that can be delivered to final recipient
        if (exchangeDeliverableMessages() != null) {
            return; // started a transfer, don't try others (yet)
        }

        List<Message> messages = new ArrayList<>(getMessageCollection());
        for (Message m : messages) {
            DTNHost destinationId = m.getTo();
            if (routingTable.stream().anyMatch(routingTableEntry -> routingTableEntry.getDestinationId().equals(destinationId))) {
                this.sendMessage(destinationId, m);
            } else {
                this.broadcast(m);
            }
        }
    }

    private void sendMessage(DTNHost destinationId, Message message) {
        Optional<Connection> connection = findConnectionFromRoutingTable(destinationId);
        connection.ifPresent(con -> startTransfer(message, con));
    }

    private void broadcast(Message message) {
        getConnections().forEach(connection -> startTransfer(message, connection));
    }

    private Optional<Connection> findConnectionFromRoutingTable(DTNHost destinationId) {
        List<RoutingTableEntry> routingTableEntries = routingTable
                .stream()
                .filter(entry -> entry.getDestinationId().equals(destinationId))
                .collect(Collectors.toList());
        RoutingTableEntry bestNode = findBestNode(routingTableEntries);
        DTNHost sendTo = bestNode.getNextHop();
        List<Connection> connections = getConnections();
        return connections
                .stream()
                .filter(connection -> connection.getOtherNode(getHost()).equals(sendTo))
                .findFirst();
    }

    private RoutingTableEntry findBestNode(List<RoutingTableEntry> routingTableEntries) {
        return routingTableEntries
                .stream()
                .min(Comparator.comparing(RoutingTableEntry::getHopCount))
                .orElse(routingTableEntries.get(0));
    }

    @Override
    public void changedConnection(Connection con) {
        if (con.isUp()) {
            return;
        }
        DTNHost nodeToRemove = con.getOtherNode(getHost());
        List<RoutingTableEntry> entriesToRemove = routingTable
                .stream()
                .filter(entry -> entry.getNextHop().equals(nodeToRemove))
                .collect(Collectors.toList());
        routingTable.removeAll(entriesToRemove);
    }

    @Override
    public MessageRouter replicate() {
        return new CustomRouter(this);
    }

    @Override
    public int receiveMessage(Message m, DTNHost from) {
        saveToRoutingTable(m, from);
        return super.receiveMessage(m, from);
    }

    private void saveToRoutingTable(Message message, DTNHost from) {
        if (containsEntry(message, from)) {
            return;
        }
        RoutingTableEntry routingTableEntry = new RoutingTableEntry();
        routingTableEntry.setDestinationId(message.getFrom());
        routingTableEntry.setNextHop(from);
        routingTableEntry.setHopCount(message.getHops().size());
        routingTable.add(routingTableEntry);
    }

    private boolean containsEntry(Message message, DTNHost from) {
        DTNHost destinationId = message.getFrom();
        return routingTable
                .stream()
                .anyMatch(entry -> entry.getDestinationId().equals(destinationId) && entry.getNextHop().equals(from));
    }

}
