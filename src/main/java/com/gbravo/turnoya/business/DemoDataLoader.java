package com.gbravo.turnoya.business;

import java.math.BigDecimal;
import java.time.LocalTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.gbravo.turnoya.business.BusinessDtos.CreateBusinessRequest;
import com.gbravo.turnoya.business.BusinessDtos.CreateServiceRequest;

/** Carga un negocio de ejemplo para probar la API apenas se levanta. */
@Component
@ConditionalOnProperty(name = "turnoya.demo-data", havingValue = "true")
public class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);
    private static final String SLUG = "barberia-don-pepe";

    private final BusinessService businessService;
    private final BusinessRepository businesses;

    public DemoDataLoader(BusinessService businessService, BusinessRepository businesses) {
        this.businessService = businessService;
        this.businesses = businesses;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (businesses.existsBySlug(SLUG)) {
            return;
        }
        Business business = businessService.create(new CreateBusinessRequest("Barbería Don Pepe", SLUG,
                "America/Argentina/Cordoba", LocalTime.of(9, 0), LocalTime.of(19, 0), 30));
        ServiceOffering corte = businessService.addService(business.getId(),
                new CreateServiceRequest("Corte de pelo", 30, new BigDecimal("12000"), 30));
        businessService.addService(business.getId(),
                new CreateServiceRequest("Corte + barba", 60, new BigDecimal("18000"), 30));
        log.info("Demo lista -> businessId={} serviceId(corte)={}", business.getId(), corte.getId());
    }
}
