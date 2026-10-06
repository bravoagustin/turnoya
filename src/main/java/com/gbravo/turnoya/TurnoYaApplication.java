package com.gbravo.turnoya;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TurnoYaApplication {

    public static void main(String[] args) {
        SpringApplication.run(TurnoYaApplication.class, args);
    }
}
