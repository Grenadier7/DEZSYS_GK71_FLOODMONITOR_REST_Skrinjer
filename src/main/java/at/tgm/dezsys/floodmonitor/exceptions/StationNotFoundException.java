package at.tgm.dezsys.floodmonitor.exceptions;

public class StationNotFoundException extends RuntimeException {
    public StationNotFoundException(String stationID) {
        super("The station with id " + stationID + " does not exist");
    }
}
