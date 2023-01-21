package routing;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import routing.bsk.RoutingTableEntry;
import routing.bsk.Rrep;
import routing.bsk.Rreq;

import java.util.*;

public class CustomRouter extends ActiveRouter {

    private List<Rreq> rreqList;
    private List<Rrep> rrepList;
    private Map<String, RoutingTableEntry> routingTable = new HashMap<>();

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

        // then try any/all message to any/all connection
        // this.tryAllMessagesToAllConnections();

        List<Message> messages = new ArrayList<>(getMessageCollection());
        for (Message m : messages) {
            String destinationId = m.getTo().toString();
            if (routingTable.containsKey(destinationId)) {
                Optional<Connection> connection = findConnectionFromRoutingTable(destinationId);
                connection.ifPresent(con -> startTransfer(m, con));
            }
            else {
                broadcast(m);
            }
        }
    }

    private void broadcast(Message message) {
        getConnections().forEach(connection -> startTransfer(message, connection));
    }

    private Optional<Connection> findConnectionFromRoutingTable(String destinationId) {
        RoutingTableEntry routingTableEntry = routingTable.get(destinationId);
        DTNHost sendTo = routingTableEntry.getNextHop();
        List<Connection> connections = getConnections();
        Optional<Connection> con = connections
                .stream()
                .filter(connection -> connection.getOtherNode(getHost()).equals(sendTo))
                .findFirst();
        return con;
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
        String key = message.getFrom().toString();
        RoutingTableEntry routingTableEntry = new RoutingTableEntry();
        routingTableEntry.setDestinationId(message.getFrom());
        routingTableEntry.setNextHop(from);
        routingTableEntry.setHopCount(message.getHops().size());
        routingTable.put(key, routingTableEntry);
    }

}
