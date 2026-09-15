package at.tgm.dezsys.floodmonitor.model;

/**
 * Hochwasserwarnstufe einer Messstation.
 * <p>
 * Wird ausschliesslich serverseitig berechnet (siehe WarningLevelCalculator)
 * und darf nie vom Client vorgegeben werden.
 */
public enum WarningLevel {

    /** Wasserstand unter dem Warnwert der Station. */
    NORMAL,

    /** Wasserstand hat den Warnwert erreicht oder ueberschritten. */
    WARNING,

    /** Wasserstand hat den kritischen Wert erreicht oder ueberschritten. */
    CRITICAL,

    /** Keine aktuelle oder gueltige Messung vorhanden. */
    UNKNOWN
}
