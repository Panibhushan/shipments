package dev.shipping.shipments.repo;

import dev.shipping.shipments.model.Customers;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomersRepository extends JpaRepository<Customers, String> {
	
}