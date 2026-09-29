package at.tgm.dezsys.floodmonitor.model;

import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

/**
 * Huelle fuer eine Liste von Warnungen - gleiches Muster wie
 * {@link StationList} und {@link MeasurementList}.
 * <p>
 * Grund fuer die Huelle: XML braucht immer genau ein Wurzelelement. Eine nackte
 * {@code List<Alert>} laesst sich nach JSON serialisieren, nach XML aber nicht
 * sinnvoll benennen.
 */
@JacksonXmlRootElement(localName = "alerts")
public record AlertList(
        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "alert")
        List<Alert> alerts) {
}
