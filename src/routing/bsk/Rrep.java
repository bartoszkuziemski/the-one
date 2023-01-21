package routing.bsk;

import core.DTNHost;

public class Rrep {

    private DTNHost sourceId;
    private DTNHost destinationId;
    private int sequenceNumber;
    private int hopCount;

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

    public int getHopCount() {
        return hopCount;
    }

    public void setHopCount(int hopCount) {
        this.hopCount = hopCount;
    }
}
