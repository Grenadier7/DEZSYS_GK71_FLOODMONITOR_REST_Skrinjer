package at.tgm.dezsys.floodmonitor.service;

import at.tgm.dezsys.floodmonitor.model.Measurement;
import at.tgm.dezsys.floodmonitor.model.Station;
import at.tgm.dezsys.floodmonitor.model.StationStatus;
import at.tgm.dezsys.floodmonitor.model.WarningLevel;
import at.tgm.dezsys.floodmonitor.config.SimulationProperties;
import at.tgm.dezsys.floodmonitor.repository.MeasurementRepository;
import at.tgm.dezsys.floodmonitor.repository.StationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Random;

/**
 * Erzeugt in regelmaessigen Abstaenden neue Messwerte fuer alle aktiven Stationen.
 *
 * <h2>Simulationsregeln</h2>
 * Jeder neue Wert baut auf dem vorherigen Wert derselben Station auf - der Pegel
 * springt also nie zufaellig, sondern bewegt sich schrittweise.
 * <ul>
 *   <li><b>Niederschlag:</b> klingt pro Schritt auf 80 % ab, mit gelegentlichem
 *       Schauer (8 % Wahrscheinlichkeit, bis zu 12 mm/h). Nie negativ.</li>
 *   <li><b>Wasserstand:</b> vorheriger Pegel, plus Ruecklauf Richtung Normalpegel
 *       (25 % der Differenz), plus Regeneinfluss (1,8 cm je mm/h), plus leichtes
 *       Rauschen. Wird auf minimal 0 cm begrenzt.</li>
 *   <li><b>Durchflussmenge:</b> waechst ueberproportional mit dem Pegel
 *       (vereinfachte Schluesselkurve: Verhaeltnis zum Normalpegel hoch 1,6).</li>
 *   <li><b>Temperatur:</b> schwankt langsam um 14 Grad Celsius.</li>
 *   <li><b>Akkustand:</b> sinkt gelegentlich um 1 %, bleibt im Bereich 0 bis 100,
 *       und steigt nie wieder.</li>
 *   <li><b>Status:</b> ab 15 % Akku oder weniger wechselt die Station auf
 *       {@code MAINTENANCE}, sonst {@code ONLINE}. Aus {@code MAINTENANCE} faellt
 *       sie mit 10 % Wahrscheinlichkeit je Schritt endgueltig auf
 *       {@code OFFLINE}.</li>
 *   <li><b>Keine neuen Werte</b> fuer deaktivierte Stationen
 *       ({@code isActive == false}) und fuer Stationen, deren letzter Status
 *       {@code OFFLINE} war.</li>
 *   <li><b>Warnstufe</b> kommt immer vom {@link WarningLevelCalculator}.</li>
 * </ul>
 *
 * <p>Alle genannten Zahlen sind Defaults und stehen in
 * {@link SimulationProperties} - zu aendern in {@code application.properties}
 * unter {@code simulation.*}, ohne Eingriff in diese Klasse.
 *
 * <p>{@code @ConditionalOnProperty} sorgt dafuer, dass diese Bean bei
 * {@code simulation.enabled=false} gar nicht erst angelegt wird. Genau das
 * brauchen die Tests: ohne laufenden Simulator bleiben die Testdaten stabil.
 */
@Service
@ConditionalOnProperty(name = "simulation.enabled", havingValue = "true", matchIfMissing = true)
public class SimulationService {

    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);

    /** Mittlere Lufttemperatur in Grad Celsius, um die die Werte schwanken. */
    private static final double BASE_TEMPERATURE = 14.0;

    private final StationRepository stations;
    private final MeasurementRepository measurements;
    private final WarningLevelCalculator warningLevels;
    private final SimulationProperties config;
    private final Random random = new Random();

    public SimulationService(StationRepository stations,
                             MeasurementRepository measurements,
                             WarningLevelCalculator warningLevels,
                             SimulationProperties config) {
        this.stations = stations;
        this.measurements = measurements;
        this.warningLevels = warningLevels;
        this.config = config;
    }

    /**
     * {@code fixedRateString} liest das Intervall aus {@code application.properties}.
     * Der Wert nach dem Doppelpunkt ist der Fallback, falls die Property fehlt.
     * <p>
     * {@code fixedRate} startet alle N Millisekunden neu, {@code fixedDelay} wuerde
     * erst N Millisekunden nach dem Ende des letzten Laufs starten.
     */
    @Scheduled(fixedRateString = "${simulation.interval:10000}")
    public void simulate() {
        int created = 0;

        for (Station station : stations.findAll()) {
            if (!station.isActive()) {
                continue;
            }

            Measurement previous = measurements.latest(station.id()).orElse(null);
            if (previous != null && previous.status() == StationStatus.OFFLINE) {
                continue;
            }

            measurements.add(station.id(), nextMeasurement(station, previous));
            created++;
        }

        log.debug("Simulationsschritt: {} neue Messwerte erzeugt", created);
    }

    private Measurement nextMeasurement(Station station, Measurement previous) {
        double rainfall = nextRainfall(previous);
        double waterLevel = nextWaterLevel(station, previous, rainfall);
        double flowRate = flowRateFor(station, waterLevel);
        double temperature = nextTemperature(previous);
        int batteryLevel = nextBatteryLevel(previous);

        StationStatus status = nextStatus(previous, batteryLevel);

        // Die Warnstufe kommt ausschliesslich von hier - nie aus einer Eingabe.
        WarningLevel warningLevel = warningLevels.calculate(station, waterLevel, status);

        return new Measurement(
                station.id(),
                Instant.now(),
                round(waterLevel),
                round(flowRate),
                round(rainfall),
                round(temperature),
                batteryLevel,
                status,
                warningLevel);
    }

    /**
     * Der technische Zustand der Station.
     * <p>
     * Ein Einweg-Verfall: ONLINE -&gt; MAINTENANCE (Akku schwach) -&gt; OFFLINE
     * (Akku endgueltig leer). Aus OFFLINE fuehrt kein Weg zurueck, weil
     * {@link #simulate()} fuer eine solche Station keine neuen Messwerte mehr
     * erzeugt - dort muesste ein Techniker hin.
     * <p>
     * Ohne diesen Schritt waere {@code StationStatus.OFFLINE} unerreichbar und
     * die OFFLINE-Pruefung in {@code simulate()} toter Code.
     */
    private StationStatus nextStatus(Measurement previous, int batteryLevel) {
        boolean inMaintenance = previous != null
                && previous.status() == StationStatus.MAINTENANCE;

        if (inMaintenance && random.nextDouble() < config.offlineChance()) {
            return StationStatus.OFFLINE;
        }
        return batteryLevel <= config.maintenanceBattery()
                ? StationStatus.MAINTENANCE
                : StationStatus.ONLINE;
    }

    /** Regen klingt ab, gelegentlich kommt ein Schauer dazu. Nie negativ. */
    private double nextRainfall(Measurement previous) {
        double base = previous == null ? 0.0 : previous.rainfall();
        double next = base * 0.8 + random.nextGaussian() * 0.8;

        if (random.nextDouble() < config.showerChance()) {
            next += random.nextDouble() * config.showerIntensity();
        }
        return Math.max(0.0, next);
    }

    /**
     * Der Kern der Simulation: neuer Pegel = alter Pegel + Ruecklauf zur Norm
     * + Regeneinfluss + Rauschen. Dadurch haengt jeder Wert am vorherigen,
     * und starker Regen hebt den Pegel spuerbar an.
     */
    private double nextWaterLevel(Station station, Measurement previous, double rainfall) {
        if (previous == null) {
            return station.normalWaterLevel();
        }

        double level = previous.waterLevel();
        double reversion = (station.normalWaterLevel() - level) * config.meanReversion();
        double rainEffect = rainfall * config.rainToLevel();
        double noise = random.nextGaussian() * config.levelNoiseSd();

        return Math.max(0.0, level + reversion + rainEffect + noise);
    }

    /**
     * Vereinfachte Schluesselkurve: die Durchflussmenge waechst ueberproportional
     * mit dem Pegel. Bei Normalpegel liegt sie etwa beim 2,5-fachen des
     * Normalpegelwerts, damit grosse und kleine Fluesse plausible
     * Groessenordnungen bekommen.
     */
    private double flowRateFor(Station station, double waterLevel) {
        double reference = Math.max(1.0, station.normalWaterLevel());
        double ratio = waterLevel / reference;
        return Math.max(0.0, Math.pow(ratio, 1.6) * reference * 2.5);
    }

    private double nextTemperature(Measurement previous) {
        double base = previous == null ? BASE_TEMPERATURE : previous.temperature();
        return base + (BASE_TEMPERATURE - base) * 0.1 + random.nextGaussian() * 0.4;
    }

    /**
     * Der Akku sinkt nur, nie steigt er - eine Messstation im Feld laedt nicht
     * nachts von selbst auf. {@code Math.max/min} haelt den Wert im von der
     * Angabe geforderten Bereich 0 bis 100.
     */
    private int nextBatteryLevel(Measurement previous) {
        if (previous == null) {
            return config.startBattery();
        }
        int battery = previous.batteryLevel();
        if (random.nextDouble() < config.batteryDrainChance()) {
            battery -= 1;
        }
        return Math.max(0, Math.min(100, battery));
    }

    /** Eine Nachkommastelle, damit die Ausgabe lesbar bleibt. */
    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
