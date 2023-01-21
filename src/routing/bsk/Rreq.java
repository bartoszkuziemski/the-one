package routing.bsk;

import core.DTNHost;

public class Rreq {

    private DTNHost sourceId;
    private DTNHost destinationId;
    private int sequenceNumber;
    private String broadcastId;
    private int hopCount = 0;

    public DTNHost getSourceId() {
        return sourceId;
    }

    public void setSourceId(DTNHost sourceId) {
        this.sourceId = sourceId;
    }

    public DTNHost getDestinationId() {
        return destinationId;
    }

    public void setDestinationId(DTNHost destinationId) {
        this.destinationId = destinationId;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public String getBroadcastId() {
        return broadcastId;
    }

    public void setBroadcastId(String broadcastId) {
        this.broadcastId = broadcastId;
    }

    public int getHopCount() {
        return hopCount;
    }

    public void setHopCount(int hopCount) {
        this.hopCount = hopCount;
    }
}
