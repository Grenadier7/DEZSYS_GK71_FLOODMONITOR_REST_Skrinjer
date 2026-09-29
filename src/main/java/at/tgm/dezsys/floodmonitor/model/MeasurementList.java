package at.tgm.dezsys.floodmonitor.model;

import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

@JacksonXmlRootElement(localName = "measurements")
public record MeasurementList(
        @JacksonXmlElementWrapper(useWrapping = false) //verhindert doppelte hülle <stations><stations><station></station></stations></stations>
        @JacksonXmlProperty(localName = "measurement")
        List<Measurement> measurements) {
}
