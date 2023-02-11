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
    //sequenceNumber used for RREQ and RREP packets
    private int routeRequestId;
    private int routeReplyId;
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
    private final List<RoutingTableEntry> routingTable = new ArrayList<>();
    private final int processedRequestsBufferSize = 64;

    //circular buffer to store the last 64 RREQs id (to avoid re-broadcasting)
    private final CircularFifoQueue<String> processedRequests = new CircularFifoQueue(processedRequestsBufferSize);
    //circular buffer to remember the RREQs that the current node has answered to (avoid generating new unnecessary RREPs)
    private final CircularFifoQueue<String> handledRREQMemory = new CircularFifoQueue(processedRequestsBufferSize);

    public CustomRouter(ActiveRouter r) {
        super(r);
        r.deleteDelivered = true;
        this.routeRequestId = 0;
        this.routeReplyId = 0;
        this.RREQMemory = new HashMap<String, Double>();
    }

    public CustomRouter(Settings s) {
        super(s);
        deleteDelivered = true;
        this.routeRequestId = 0;
        this.routeReplyId = 0;
        this.RREQMemory = new HashMap<String, Double>();
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
                    //update routing table:
//                    DTNHost destinationId = m.getTo();
//                    DTNHost routeRequester = m.getFrom();
                    saveToRoutingTable(m);
//                  if (routingTable.stream().anyMatch(routingTableEntry -> routingTableEntry.getDestinationId().equals(destinationId))) {
//                        //1) I know the route to the destination, I can generate the RREP!
//                        List<RoutingTableEntry> routingTableEntries = routingTable
//                                .stream()
//                                .filter(entry -> entry.getDestinationId().equals(destinationId))
//                                .collect(Collectors.toList());
//                        RoutingTableEntry bestNode = findBestNode(routingTableEntries);
//                        Message rrep = buildIndirectRouteReply(m, bestNode);
//                        this.sendMessage(routeRequester, rrep);
//                    } else {
// 2) Broadcast the RREQ//                    }
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
    public void addMessageIdToDelete (String id) {
        if (this.getMessageCollection().stream().toList().contains(this.getMessage(id))) {
            this.messagesToDelete.add(id);
        }
    }
    private void clearMessagesToDelete() {
        this.messagesToDelete.forEach(id -> deleteMessage(id, false));
        this.messagesToDelete.clear();
    }
    private Set<String> messagesToDelete = new HashSet<>();
    @Override
    public Message messageTransferred(String id, DTNHost from) {
        CustomRouter fromRouter = (CustomRouter)from.getRouter();
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
        /** Send a new RREQ if enough time passed
         */
        DTNHost from = this.getHost();
        Message routeReq = this.buildRouteRequest(from, destination);
        this.addToMessages(routeReq, false);
        //this.addToMessages(routeReq,true);
        this.broadcast(routeReq);
    }

    /**
     * This is to avoid generating new RREQs every each update call
     * (introduces a delay between new RREQs)
     */
    private static final double howOftenRetryRREQ = 300.0;
    private HashMap<String, Double> RREQMemory;

    private boolean canRetryRREQ(String destination) {
        /** Checks how long ago was the last time of
         *  the RREQ to the selected destination send
         *  returns true if it was 'enough' long time ago
         */
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
        Message rreq = new Message(from, to, idStr, RREQ_SIZE, RREQ, requestId);
        return rreq;
    }

    Message buildRouteReply(DTNHost from, DTNHost to) {
        int requestId = this.getRouteReplyId();
        String idStr = "RREP_" + from.toString() + "_to_" + to.toString() + "_" + requestId;
        if (!from.equals(this.getHost())) {
            idStr += "_gen_by_" + this.getHost(); //when the author of the RREP is not the node at the end of the route...
        }
        Message rrep = new Message(from, to, idStr, RREP_SIZE, RREP, requestId);
        return rrep;
    }

    /**
     * This function is used to build a RREP in the node that is not the end-route node
     * but knows the route to the requested node (based on the RoutingTableEntry entry object)
     * and Message rreq
     */
    Message buildIndirectRouteReply(Message rreq, RoutingTableEntry entry) {
        Message rrep = buildRouteReply(entry.getDestinationId(), rreq.getFrom());
        double hopCount_double = entry.getHopCount();
        rrep.setIndirectRREPHopCount((int) hopCount_double);
        return rrep;
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
        //TODO
        return routingTableEntries
                .stream()
                .min(Comparator.comparing(RoutingTableEntry::getHopCount))
                .orElse(routingTableEntries.get(0)); //hopefully can't get there
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
                //this.sendMessage(routeRequester, rrep);
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
        List<RoutingTableEntry> entryToUpdate = routingTable
                .stream()
                .filter(entry -> entry.getDestinationId().equals(destinationId) &&
                        entry.getNextHop().equals(nextHop))
                .collect(Collectors.toList());
        if (entryToUpdate.isEmpty()) {
            //add the entry
            RoutingTableEntry routingTableEntry = new RoutingTableEntry();
            routingTableEntry.setDestinationId(destinationId);
            routingTableEntry.setNextHop(nextHop);
            routingTableEntry.setHopCount(hopCount);
            routingTable.add(routingTableEntry);
            return;
        }
        if (!(entryToUpdate.size() == 1)) {
            //Error, we shouldn't get there!
            throw new RuntimeException();
        }
        //entryTo.get(0).updateHopCount(message);
        entryToUpdate.get(0).updateHopCount(hopCount);
    }


    @Override
    public MessageRouter replicate() {
        return new CustomRouter(this);
    }
}