package com.gbravo.turnoya.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.gbravo.turnoya.business.Business;
import com.gbravo.turnoya.business.BusinessDtos.CreateBusinessRequest;
import com.gbravo.turnoya.business.BusinessDtos.CreateServiceRequest;
import com.gbravo.turnoya.business.BusinessService;
import com.gbravo.turnoya.business.ServiceOffering;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTest.ClockConfig.class)
public abstract class IntegrationTest {

    /** Lunes 5/10/2026 09:00 en Córdoba (UTC-3). */
    protected static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    /** Martes 6/10/2026 10:00 en Córdoba. */
    protected static final Instant TOMORROW_10AM = Instant.parse("2026-10-06T13:00:00Z");

    @TestConfiguration
    static class ClockConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(NOW);
        }
    }

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected BusinessService businessService;

    @Autowired
    private WebApplicationContext context;

    protected MockMvc mvc;

    @BeforeEach
    void baseSetUp() {
        clock.set(NOW);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    /** Negocio 09:00-19:00, turnos cada 30 min, con un servicio de 30 min a $12.000 y seña del 30%. */
    protected ServiceOffering newBusinessWithHaircut() {
        String slug = "negocio-" + UUID.randomUUID().toString().substring(0, 8);
        Business business = businessService.create(new CreateBusinessRequest("Barbería Test", slug,
                "America/Argentina/Cordoba", LocalTime.of(9, 0), LocalTime.of(19, 0), 30));
        return businessService.addService(business.getId(),
                new CreateServiceRequest("Corte", 30, new BigDecimal("12000"), 30));
    }
}
