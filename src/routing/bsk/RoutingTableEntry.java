package routing.bsk;

import core.DTNHost;

public class RoutingTableEntry {

    private DTNHost destinationId;
    private DTNHost nextHop;
    private int hopCount = 0;
    private double distance = 0.0;
    private int sequenceNumber;

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public DTNHost getDestinationId() {
        return destinationId;
    }

    public void setDestinationId(DTNHost destinationId) {
        this.destinationId = destinationId;
    }

    public DTNHost getNextHop() {
        return nextHop;
    }

    public void setNextHop(DTNHost nextHop) {
        this.nextHop = nextHop;
    }

    public int getHopCount() {
        return hopCount;
    }

    public void setHopCount(int hopCount) {
        this.hopCount = hopCount;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }
}
