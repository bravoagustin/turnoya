package com.gbravo.turnoya.business;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gbravo.turnoya.business.BusinessDtos.BusinessResponse;
import com.gbravo.turnoya.business.BusinessDtos.CreateBusinessRequest;
import com.gbravo.turnoya.business.BusinessDtos.CreateServiceRequest;
import com.gbravo.turnoya.business.BusinessDtos.ServiceResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/businesses")
public class BusinessController {

    private final BusinessService businessService;

    public BusinessController(BusinessService businessService) {
        this.businessService = businessService;
    }

    @PostMapping
    ResponseEntity<BusinessResponse> create(@Valid @RequestBody CreateBusinessRequest request) {
        Business business = businessService.create(request);
        return ResponseEntity.created(URI.create("/api/businesses/" + business.getId()))
                .body(BusinessResponse.from(business));
    }

    @GetMapping
    List<BusinessResponse> list() {
        return businessService.listAll().stream().map(BusinessResponse::from).toList();
    }

    @GetMapping("/{businessId}")
    BusinessResponse get(@PathVariable UUID businessId) {
        return BusinessResponse.from(businessService.get(businessId));
    }

    @PostMapping("/{businessId}/services")
    ResponseEntity<ServiceResponse> addService(@PathVariable UUID businessId,
            @Valid @RequestBody CreateServiceRequest request) {
        ServiceOffering service = businessService.addService(businessId, request);
        return ResponseEntity.created(URI.create("/api/businesses/" + businessId + "/services/" + service.getId()))
                .body(ServiceResponse.from(service));
    }

    @GetMapping("/{businessId}/services")
    List<ServiceResponse> listServices(@PathVariable UUID businessId) {
        return businessService.listServices(businessId).stream().map(ServiceResponse::from).toList();
    }
}
