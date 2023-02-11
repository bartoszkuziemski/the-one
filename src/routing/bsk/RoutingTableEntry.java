package routing.bsk;

import core.DTNHost;

public class RoutingTableEntry {
    private DTNHost destinationId;
    private DTNHost nextHop;
    private double hopCount = 0.0;
    private double distance = 0.0;
    private int howManyMeasurements = 1;

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

    public double getHopCount() {
        return hopCount;
    }

    public void setHopCount(int hopCount) {
        this.hopCount = hopCount;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public void updateHopCount(int currentMeasureHopCount) {
        double currentMeasureHopCountDouble = currentMeasureHopCount;
        this.hopCount = (this.hopCount * this.howManyMeasurements + currentMeasureHopCountDouble);
        this.hopCount = this.hopCount / (++this.howManyMeasurements);
    }

}
