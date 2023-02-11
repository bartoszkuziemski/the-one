package routing;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import routing.bsk.RoutingTableEntry;

import java.util.*;
import java.util.stream.Collectors;

import static core.Message.*;
import static core.SimClock.getTime;

public class CustomRouter extends ActiveRouter {
    private int routeRequestId;
    private int routeReplyId;

    private final List<RoutingTableEntry> routingTable = new ArrayList<>();
    private final int processedRequestsBufferSize = 64;

    //circular buffer to remember the RREQs that the current node has answered to (avoid generating new unnecessary RREPs)
    private final CircularFifoQueue<String> handledRREQMemory = new CircularFifoQueue(processedRequestsBufferSize);
    private Set<String> messagesToDelete = new HashSet<>();
    private static final double howOftenRetryRREQ = 300.0;
    private HashMap<String, Double> RREQMemory;


    public CustomRouter(ActiveRouter r) {
        super(r);
        r.deleteDelivered = true;
        this.routeRequestId = 0;
        this.routeReplyId = 0;
        this.RREQMemory = new HashMap<>();
    }

    public CustomRouter(Settings s) {
        super(s);
        deleteDelivered = true;
        this.routeRequestId = 0;
        this.routeReplyId = 0;
        this.RREQMemory = new HashMap<>();
    }

    private int getRouteRequestId() {
        int id = this.routeRequestId;
        this.routeRequestId += 1;
        return id;
    }

    private int getRouteReplyId() {
        int id = this.routeReplyId;
        this.routeReplyId += 1;
        return id;
    }

    @Override
    public void update() {
        super.update();
        clearMessagesToDelete();
        if (isTransferring() || !canStartTransfer()) {
            return; // transferring, don't try other connections yet
        }
        List<Message> messages = new ArrayList<>(getMessageCollection());
        for (Message m : messages) {
            if (skipMessage(m)) {
                continue;
            }
            switch (m.getType()) {
                case RREQ: {
                    saveToRoutingTable(m);
                    this.broadcast(m);
                    break;
                }
                case RREP: {
                    DTNHost destinationId = m.getTo();
                    if (routingTable.stream().anyMatch(routingTableEntry -> routingTableEntry.getDestinationId().equals(destinationId))) {
                        //1) I am not the receiver, but I know the route to the receiver
                        this.sendMessage(destinationId, m);
                    } else {
                        //2) I don't know the route - broadcast RREQ - it can happen if the routingTableEntry was deleted in the meantime...
                        askForRoute(destinationId);
                    }
                    break;
                }
                default: {
                    DTNHost destinationId = m.getTo();
                    if (routingTable.stream().anyMatch(routingTableEntry -> routingTableEntry.getDestinationId().equals(destinationId))) {
                        //1)I know the route
                        this.sendMessage(destinationId, m);
                    } else {
                        //2) I don't know the route - broadcast RREQ
                        askForRoute(destinationId);
                    }
                }
            }
        }
    }

    public void addMessageIdToDelete(String id) {
        if (this.getMessageCollection().stream().toList().contains(this.getMessage(id))) {
            this.messagesToDelete.add(id);
        }
    }

    private void clearMessagesToDelete() {
        this.messagesToDelete.forEach(id -> deleteMessage(id, false));
        this.messagesToDelete.clear();
    }

    @Override
    public Message messageTransferred(String id, DTNHost from) {
        CustomRouter fromRouter = (CustomRouter) from.getRouter();
        fromRouter.addMessageIdToDelete(id);
        return super.messageTransferred(id, from);
    }

    private boolean skipMessage(Message m) {
        // The message author is the host of this router and the message came back there
        if (m.getHopCount() > 0 && m.getFrom().equals(this.getHost())) {
            this.deleteMessage(m.getId(), false);
            return true;
        }
        return false;
    }

    private void askForRoute(DTNHost destination) {
        if (!this.canRetryRREQ(destination.toString())) {
            return;
        }
        // Send a new RREQ if enough time passed
        DTNHost from = this.getHost();
        Message routeReq = this.buildRouteRequest(from, destination);
        this.addToMessages(routeReq, false);
        this.broadcast(routeReq);
    }

    /**
     * This is to avoid generating new RREQs every each update call
     * (introduces a delay between new RREQs)
     * Checks how long ago was the last time of
     * the RREQ to the selected destination send
     * returns true if it was 'enough' long time ago
     */
    private boolean canRetryRREQ(String destination) {
        Double curTime = Double.valueOf(getTime());
        Double mapVal = RREQMemory.get(destination);
        if (mapVal != null) {
            Double diff = curTime - mapVal;
            if (diff > howOftenRetryRREQ) {
                RREQMemory.put(destination, curTime);
                return true;
            }
            return false;
        }
        // null returned - no RREQ sent to that destination yet -
        // create an entry in the RREQMemory
        RREQMemory.put(destination, curTime);
        return true;
    }

    Message buildRouteRequest(DTNHost from, DTNHost to) {
        int requestId = this.getRouteRequestId();
        String idStr = "RREQ_" + from.toString() + "_to_" + to.toString() + "_" + requestId;
        return new Message(from, to, idStr, RREQ_SIZE, RREQ, requestId);
    }

    Message buildRouteReply(DTNHost from, DTNHost to) {
        int requestId = this.getRouteReplyId();
        String idStr = "RREP_" + from.toString() + "_to_" + to.toString() + "_" + requestId;
        if (!from.equals(this.getHost())) {
            idStr += "_gen_by_" + this.getHost(); //when the author of the RREP is not the node at the end of the route...
        }
        return new Message(from, to, idStr, RREP_SIZE, RREP, requestId);
    }

    private void sendMessage(DTNHost destinationId, Message message) {
        Optional<Connection> connection = findConnectionFromRoutingTable(destinationId);
        connection.ifPresent(con -> startTransfer(message, con));
    }

    private void broadcast(Message message) {
        for (Connection connection : getConnections()) {
            startTransfer(message, connection);
        }
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
                .min((o1, o2) -> {
                    if (o1.getDistance() > o2.getDistance()) {
                        return 1;
                    } else if (o1.getDistance() < o2.getDistance()) {
                        return -1;
                    } else {
                        return Double.compare(o1.getHopCount(), o2.getHopCount());
                    }
                })
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
    public int receiveMessage(Message m, DTNHost from) {
        if (m.getType() == RREQ) {
            //update routing table:
            saveToRoutingTable(m);
            DTNHost destinationId = m.getTo();
            DTNHost routeRequester = m.getFrom();
            if (destinationId.equals(this.getHost()) && !haveGeneratedRREPToThisRREQ(m.getId())) {
                Message rrep = buildRouteReply(this.getHost(), routeRequester);
                this.addToMessages(rrep, false);
            }
        }
        if (m.getType() == RREP) {
            DTNHost destinationId = m.getTo();
            if (destinationId.equals(this.getHost())) {
                saveToRoutingTable(m);
            }
        }
        return super.receiveMessage(m, from);
    }

    boolean haveGeneratedRREPToThisRREQ(String rreqId) {
        if (this.handledRREQMemory.stream().anyMatch(mId -> mId.equals(rreqId))) {
            return true;
        }
        this.handledRREQMemory.add(rreqId);
        return false;
    }

    private void saveToRoutingTable(Message message) {
        DTNHost destinationId = message.getFrom();
        List<DTNHost> listOfDTNHost = message.getHops();
        if (this.getHost().equals(destinationId)) {
            return;
        }
        int hopCount = listOfDTNHost.size(); //
        DTNHost nextHop = listOfDTNHost.get(hopCount - 1); // -1 because casting count to index
        // look for the entry to update
        Optional<RoutingTableEntry> entryToUpdate = routingTable
                .stream()
                .filter(entry -> entry.getDestinationId().equals(destinationId) &&
                        entry.getNextHop().equals(nextHop))
                .findFirst();
        if (entryToUpdate.isPresent()) {
            entryToUpdate.get().updateHopCount(hopCount);
            entryToUpdate.get().setDistance(this.calculateDistanceFromMessage(message));
        }
        //add the entry
        RoutingTableEntry routingTableEntry = new RoutingTableEntry();
        routingTableEntry.setDestinationId(destinationId);
        routingTableEntry.setNextHop(nextHop);
        routingTableEntry.setHopCount(hopCount);
        routingTableEntry.setDistance(this.calculateDistanceFromMessage(message));
        routingTable.add(routingTableEntry);
    }

    private double calculateDistanceFromMessage(Message message) {
        if (message.getPreviousNodeLocation().equals(getHost().getLocation())) {
            return 0.0;
        }
        double previousNodeX = message.getPreviousNodeLocation().getX();
        double previousNodeY = message.getPreviousNodeLocation().getY();
        double thisNodeX = getHost().getLocation().getX();
        double thisNodeY = getHost().getLocation().getY();
        double diffX = Math.abs(thisNodeX - previousNodeX);
        double diffY = Math.abs(thisNodeY - previousNodeY);
        double meanDistance = Math.sqrt(Math.pow(diffX, 2) + Math.pow(diffY, 2));
        double previousMeanDistance = message.getDistanceTravelled();
        return previousMeanDistance + meanDistance;
    }

    @Override
    public MessageRouter replicate() {
        return new CustomRouter(this);
    }
}