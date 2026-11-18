
package za.co.capitec.financialplatform.customer.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import za.co.capitec.financialplatform.customer.dto.CreateCustomerRequest;
import za.co.capitec.financialplatform.customer.dto.CustomerResponse;
import za.co.capitec.financialplatform.customer.service.CustomerService;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> createCustomer(
            @Valid @RequestBody CreateCustomerRequest request) {

        CustomerResponse customer = customerService.createCustomer(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(customer.id())
                .toUri();

        return ResponseEntity
                .created(location)
                .body(customer);
    }

    @GetMapping
    public ResponseEntity<List<CustomerResponse>> listCustomers() {
        return ResponseEntity.ok(customerService.listCustomers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> getCustomerById(
            @PathVariable UUID id) {

        CustomerResponse customer = customerService.getCustomerById(id);

        return ResponseEntity.ok(customer);
    }
}
