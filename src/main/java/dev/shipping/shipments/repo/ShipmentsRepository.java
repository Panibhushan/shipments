package dev.shipping.shipments.repo;

import dev.shipping.shipments.model.Shipments;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentsRepository extends JpaRepository<Shipments, String> {
	
}
