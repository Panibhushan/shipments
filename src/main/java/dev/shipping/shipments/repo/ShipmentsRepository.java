package dev.shipping.shipments.repo;

import dev.shipping.shipments.model.Shipments;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShipmentsRepository extends JpaRepository<Shipments, String> {
	
	
}
