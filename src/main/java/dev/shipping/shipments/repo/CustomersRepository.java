package dev.shipping.shipments.repo;

import dev.shipping.shipments.model.Customers;
import dev.shipping.shipments.model.Shipments;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomersRepository extends JpaRepository<Customers, String> {
	
	// JPA is case-sesitive so use the reserved words like SELECT, FROM, WHERE, AND, OR, ORDER BY, GROUP BY in uppercase

	@Query("SELECT c FROM Customers c WHERE c.customerStatus = :status AND c.validUpto > :today")
    List<Customers> findByCustomerStatusAndValidUpto(@Param("status") String status, @Param("today") LocalDateTime localDateTime);
	
	@Query("SELECT c FROM Customers c WHERE c.customerId = :customerId AND c.customerStatus = 'Active' AND c.validUpto > current_date")
	Customers findIfCustomerIsActiveAndHasValidUptoDate(String customerId);

}	