package routing;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import routing.bsk.RoutingTableEntry;
import routing.bsk.Rrep;
import routing.bsk.Rreq;

import java.util.*;
import java.util.stream.Collectors;

public class CustomRouter extends ActiveRouter {

    private List<Rreq> rreqList;
    private List<Rrep> rrepList;
    private List<RoutingTableEntry> routingTable = new ArrayList<>();

    public CustomRouter(ActiveRouter r) {
        super(r);
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

        // then try any/all message to any/all connection
        // this.tryAllMessagesToAllConnections();

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
        RoutingTableEntry routingTableEntry = routingTableEntries
                .stream()
                .min(Comparator.comparing(RoutingTableEntry::getHopCount))
                .orElse(routingTableEntries.get(0));
        DTNHost sendTo = routingTableEntry.getNextHop();
        List<Connection> connections = getConnections();
        Optional<Connection> con = connections
                .stream()
                .filter(connection -> connection.getOtherNode(getHost()).equals(sendTo))
                .findFirst();
        return con;
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

    public boolean isInTable(Rreq rreq) {
        return rreqList.contains(rreq);
    }

    public boolean isInTable(Rrep rrep) {
        return rrepList.contains(rrep);
    }

    public void sendRreq(Connection connection, Rreq rreq) {

    }

    public void sendRrep(Connection connection, Rrep rrep) {

    }

    @Override
    public int receiveMessage(Message m, DTNHost from) {
        int recvCheck = checkReceiving(m, from);
        if (recvCheck != RCV_OK) {
            return recvCheck;
        }

        saveToRoutingTable(m, from);

        // seems OK, start receiving the message
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
