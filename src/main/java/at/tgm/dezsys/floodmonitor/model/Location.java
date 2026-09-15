package at.tgm.dezsys.floodmonitor.model;

/**
 * Geografische Position einer Messstation.
 *
 * @param latitude  Breitengrad in Dezimalgrad
 * @param longitude Laengengrad in Dezimalgrad
 */
public record Location(double latitude, double longitude) {
}
