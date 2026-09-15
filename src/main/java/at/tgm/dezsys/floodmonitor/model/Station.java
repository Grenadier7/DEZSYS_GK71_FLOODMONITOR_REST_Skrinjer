package at.tgm.dezsys.floodmonitor.model;

/**
 * Eine Messstation an einem Flussabschnitt.
 * <p>
 * Als Record umgesetzt: unveraenderlich, und Jackson kann Records sowohl
 * serialisieren als auch deserialisieren. Ein Record liefert ausserdem den
 * Accessor {@code isActive()}, wodurch das JSON-Feld {@code isActive} heisst.
 * Bei einer klassischen Klasse mit Getter {@code isActive()} wuerde Jackson
 * daraus {@code active} machen.
 *
 * @param normalWaterLevel   ueblicher Wasserstand in cm
 * @param warningWaterLevel  ab diesem Wasserstand gilt WARNING (in cm)
 * @param criticalWaterLevel ab diesem Wasserstand gilt CRITICAL (in cm)
 * @param isActive           deaktivierte Stationen erzeugen keine Messwerte
 */
public record Station(
        String id,
        String name,
        String river,
        Location location,
        double normalWaterLevel,
        double warningWaterLevel,
        double criticalWaterLevel,
        boolean isActive
) {
}
