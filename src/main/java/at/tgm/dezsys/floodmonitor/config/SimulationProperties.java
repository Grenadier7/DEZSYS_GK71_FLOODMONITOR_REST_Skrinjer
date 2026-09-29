package at.tgm.dezsys.floodmonitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Stellschrauben der Messwert-Simulation, gebunden an den Prefix
 * {@code simulation.*} in {@code application.properties}.
 * <p>
 * Vorher standen diese Werte als {@code private static final} im
 * {@link at.tgm.dezsys.floodmonitor.service.SimulationService}. Als Properties
 * sind sie ohne Neukompilieren aenderbar - fuer eine Vorfuehrung laesst sich der
 * Akku so in Sekunden leerlaufen lassen statt in Stunden.
 * <p>
 * Constructor Binding: weil das hier ein {@code record} ist, bindet Spring die
 * Werte ueber den Kanonischen Konstruktor. Die Felder sind dadurch final - die
 * Konfiguration kann zur Laufzeit nicht versehentlich veraendert werden.
 * {@code @DefaultValue} greift, wenn die Property gar nicht gesetzt ist; ohne
 * die Annotation waere ein fehlender Eintrag stillschweigend {@code 0.0}.
 * <p>
 * Nicht enthalten sind {@code simulation.enabled} und {@code simulation.interval}:
 * die werden von {@code @ConditionalOnProperty} bzw. {@code @Scheduled} gelesen,
 * also von Annotationen, die zur Kompilierzeit ausgewertet werden und kein Bean
 * einsehen koennen.
 *
 * @param meanReversion      Anteil der Differenz zum Normalpegel, der pro Schritt
 *                           ausgeglichen wird (0 = Pegel kehrt nie zurueck,
 *                           1 = Pegel springt sofort auf Normalniveau). Muss
 *                           stark genug sein, um {@code rainToLevel}
 *                           auszugleichen - sonst laeuft der Pegel davon
 * @param rainToLevel        Pegelanstieg in cm je mm/h Niederschlag
 * @param levelNoiseSd       Standardabweichung des Rauschens auf dem Pegel in cm
 * @param showerChance       Wahrscheinlichkeit eines Regenschauers je Schritt (0-1)
 * @param showerIntensity    maximale zusaetzliche Regenmenge eines Schauers in mm/h
 * @param maintenanceBattery Akkustand in Prozent, ab dem die Station auf
 *                           MAINTENANCE wechselt
 * @param batteryDrainChance Wahrscheinlichkeit je Schritt, dass der Akku um 1 %
 *                           sinkt (0.05 = etwa jeder 20. Schritt)
 * @param offlineChance      Wahrscheinlichkeit je Schritt, dass eine Station in
 *                           MAINTENANCE endgueltig auf OFFLINE wechselt
 * @param startBattery       Akkustand der ersten Messung einer Station in Prozent
 */
@ConfigurationProperties(prefix = "simulation")
public record SimulationProperties(
        @DefaultValue("0.25") double meanReversion,
        @DefaultValue("1.8") double rainToLevel,
        @DefaultValue("1.5") double levelNoiseSd,
        @DefaultValue("0.08") double showerChance,
        @DefaultValue("12.0") double showerIntensity,
        @DefaultValue("15") int maintenanceBattery,
        @DefaultValue("0.05") double batteryDrainChance,
        @DefaultValue("0.1") double offlineChance,
        @DefaultValue("100") int startBattery
) {
}
