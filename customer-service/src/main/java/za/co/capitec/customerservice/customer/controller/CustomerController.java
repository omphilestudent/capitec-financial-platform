package za.co.capitec.customerservice.customer.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import za.co.capitec.customerservice.customer.dto.CreateCustomerRequest;
import za.co.capitec.customerservice.customer.dto.CustomerResponse;
import za.co.capitec.customerservice.customer.service.CustomerService;

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
            @Valid @RequestBody CreateCustomerRequest request
    ) {
        var customer = customerService.createCustomer(
                request.firstName(),
                request.lastName(),
                request.email(),
                request.phoneNumber(),
                request.clientType()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(CustomerResponse.from(customer));
    }

    @GetMapping
    public ResponseEntity<List<CustomerResponse>> getAllCustomers() {
        List<CustomerResponse> customers = customerService.getAllCustomers()
                .stream()
                .map(CustomerResponse::from)
                .toList();

        return ResponseEntity.ok(customers);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> getCustomerById(
            @PathVariable UUID id
    ) {
        var customer = customerService.getCustomerById(id);

        return ResponseEntity.ok(CustomerResponse.from(customer));
    }
}