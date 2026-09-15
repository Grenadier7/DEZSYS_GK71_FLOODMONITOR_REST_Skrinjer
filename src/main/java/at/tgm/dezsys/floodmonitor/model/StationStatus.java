package at.tgm.dezsys.floodmonitor.model;

/**
 * Technischer Zustand einer Messstation zum Zeitpunkt einer Messung.
 */
public enum StationStatus {

    /** Station sendet regulaer Messwerte. */
    ONLINE,

    /** Station ist eingeschraenkt betriebsbereit, z. B. bei niedrigem Akkustand. */
    MAINTENANCE,

    /** Station sendet nicht. Fuer sie werden keine neuen Messwerte erzeugt. */
    OFFLINE
}
