package at.tgm.dezsys.floodmonitor.exceptions;

public class MeasurementNotFoundException extends RuntimeException {
    public MeasurementNotFoundException(String stationID) {
        super("No Measurement found for Station ID: " + stationID);
    }
}
