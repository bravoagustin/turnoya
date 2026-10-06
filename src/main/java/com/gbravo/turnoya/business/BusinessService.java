package com.gbravo.turnoya.business;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gbravo.turnoya.business.BusinessDtos.CreateBusinessRequest;
import com.gbravo.turnoya.business.BusinessDtos.CreateServiceRequest;
import com.gbravo.turnoya.shared.BusinessRuleException;
import com.gbravo.turnoya.shared.NotFoundException;

@Service
public class BusinessService {

    private final BusinessRepository businesses;
    private final ServiceOfferingRepository services;
    private final Clock clock;

    public BusinessService(BusinessRepository businesses, ServiceOfferingRepository services, Clock clock) {
        this.businesses = businesses;
        this.services = services;
        this.clock = clock;
    }

    @Transactional
    public Business create(CreateBusinessRequest request) {
        if (businesses.existsBySlug(request.slug())) {
            throw new BusinessRuleException("Ya existe un negocio con el slug " + request.slug());
        }
        if (!request.openingTime().isBefore(request.closingTime())) {
            throw new BusinessRuleException("El horario de apertura debe ser anterior al de cierre");
        }
        return businesses.save(new Business(request.name(), request.slug(), ZoneId.of(request.timezone()),
                request.openingTime(), request.closingTime(), request.slotMinutes(), clock.instant()));
    }

    @Transactional
    public ServiceOffering addService(UUID businessId, CreateServiceRequest request) {
        Business business = get(businessId);
        return services.save(new ServiceOffering(business, request.name(), request.durationMinutes(),
                request.price(), request.depositPercent()));
    }

    @Transactional(readOnly = true)
    public Business get(UUID businessId) {
        return businesses.findById(businessId).orElseThrow(() -> new NotFoundException("Negocio", businessId));
    }

    @Transactional(readOnly = true)
    public List<Business> listAll() {
        return businesses.findAll(Sort.by("name"));
    }

    @Transactional(readOnly = true)
    public List<ServiceOffering> listServices(UUID businessId) {
        get(businessId);
        return services.findByBusinessIdAndActiveTrueOrderByName(businessId);
    }

    @Transactional(readOnly = true)
    public ServiceOffering getService(UUID businessId, UUID serviceId) {
        return services.findByIdAndBusinessId(serviceId, businessId)
                .filter(ServiceOffering::isActive)
                .orElseThrow(() -> new NotFoundException("Servicio", serviceId));
    }
}
