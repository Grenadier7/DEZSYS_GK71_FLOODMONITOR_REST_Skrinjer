package at.tgm.dezsys.floodmonitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Einstiegspunkt der Anwendung.
 * <p>
 * {@code @SpringBootApplication} buendelt {@code @Configuration},
 * {@code @EnableAutoConfiguration} und {@code @ComponentScan}. Der Scan
 * durchsucht dieses Package und alle Unterpackages - deshalb muessen alle
 * eigenen Beans unterhalb von {@code at.tgm.dezsys.floodmonitor} liegen.
 * <p>
 * {@code @EnableScheduling} schaltet den Scheduler ein. Ohne diese Zeile wuerde
 * die mit {@code @Scheduled} annotierte Methode des Simulators nie aufgerufen -
 * und zwar ohne jede Fehlermeldung.
 * <p>
 * {@code @ConfigurationPropertiesScan} sucht Klassen mit
 * {@code @ConfigurationProperties} und macht daraus Beans - hier
 * {@link at.tgm.dezsys.floodmonitor.config.SimulationProperties}. Ohne diese
 * Zeile scheitert der Start mit einer {@code NoSuchBeanDefinitionException},
 * weil der {@code SimulationService} die Konfiguration im Konstruktor erwartet.
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class FloodmonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(FloodmonitorApplication.class, args);
    }

}
