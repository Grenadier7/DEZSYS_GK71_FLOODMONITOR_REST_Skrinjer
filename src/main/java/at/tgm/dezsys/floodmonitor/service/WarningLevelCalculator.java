package at.tgm.dezsys.floodmonitor.service;

import at.tgm.dezsys.floodmonitor.model.Measurement;
import at.tgm.dezsys.floodmonitor.model.Station;
import at.tgm.dezsys.floodmonitor.model.StationStatus;
import at.tgm.dezsys.floodmonitor.model.WarningLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Berechnet die Hochwasserwarnstufe einer Station.
 * <p>
 * Bewusst eine eigene Klasse ohne jeden HTTP-Bezug: die Angabe verlangt, dass
 * die Warnstufe serverseitig bestimmt wird und nie vom Client kommt. Ausserdem
 * laesst sich diese Klasse ohne laufenden Spring-Context testen.
 * <p>
 * <b>Regeln</b> (aus der Angabe, Punkt 3):
 * <ul>
 *   <li>Wasserstand kleiner als Warnwert -&gt; {@code NORMAL}</li>
 *   <li>Wasserstand ab Warnwert (inklusive) -&gt; {@code WARNING}</li>
 *   <li>Wasserstand ab kritischem Wert (inklusive) -&gt; {@code CRITICAL}</li>
 *   <li>Keine Station, keine Messung oder Station {@code OFFLINE} -&gt; {@code UNKNOWN}</li>
 *   <li>Messung zu alt -&gt; {@code UNKNOWN} (siehe {@link #effective(Measurement)})</li>
 * </ul>
 */
@Service
public class WarningLevelCalculator {

    /**
     * Ab diesem Alter gilt eine Messung nicht mehr als "aktuell".
     * <p>
     * Die Angabe nennt als UNKNOWN-Bedingung "keine aktuelle <i>oder</i> gueltige
     * Messung". Ohne Altersgrenze waere nur der Fall "gar keine Messung"
     * abgedeckt - eine Station, die vor zwei Tagen zuletzt NORMAL gemeldet hat,
     * wuerde weiterhin als NORMAL gelten und damit falsche Sicherheit liefern.
     * <p>
     * Konfigurierbar ueber {@code floodmonitor.measurement-max-age}. Der Default
     * hinter dem Doppelpunkt greift, wenn die Property fehlt - ohne ihn wuerde
     * der Context beim Start mit einem Fehler abbrechen.
     */
    private final Duration maxAge;

    public WarningLevelCalculator(
            @Value("${floodmonitor.measurement-max-age:PT5M}") Duration maxAge) {
        this.maxAge = maxAge;
    }

    /**
     * Variante fuer den Simulator: dort steht der Wasserstand schon fest,
     * das {@link Measurement} wird aber erst gebaut.
     */
    public WarningLevel calculate(Station station, double waterLevel, StationStatus status) {
        if (station == null || status == null || status == StationStatus.OFFLINE) {
            return WarningLevel.UNKNOWN;
        }
        if (waterLevel >= station.criticalWaterLevel()) {
            return WarningLevel.CRITICAL;
        }
        if (waterLevel >= station.warningWaterLevel()) {
            return WarningLevel.WARNING;
        }
        return WarningLevel.NORMAL;
    }

    /** Variante fuer bereits vorhandene Messwerte. */
    public WarningLevel calculate(Station station, Measurement measurement) {
        if (measurement == null) {
            return WarningLevel.UNKNOWN;
        }
        return calculate(station, measurement.waterLevel(), measurement.status());
    }

    /**
     * Die Warnstufe, die fuer eine Station <em>jetzt</em> gilt.
     * <p>
     * Liest den beim Erzeugen gespeicherten Wert, statt neu zu rechnen: der Wert
     * in der Messung ist die serverseitige Wahrheit und bleibt so auch dann
     * erhalten, wenn jemand spaeter die Schwellwerte der Station aendert.
     * Einzige Ausnahme ist das Alter - eine veraltete Messung sagt nichts
     * mehr ueber die Lage aus und wird zu {@code UNKNOWN}.
     *
     * @param measurement die letzte Messung der Station, darf {@code null} sein
     */
    public WarningLevel effective(Measurement measurement) {
        if (measurement == null || measurement.warningLevel() == null) {
            return WarningLevel.UNKNOWN;
        }
        if (measurement.timestamp() == null
                || measurement.timestamp().isBefore(Instant.now().minus(maxAge))) {
            return WarningLevel.UNKNOWN;
        }
        return measurement.warningLevel();
    }
}
