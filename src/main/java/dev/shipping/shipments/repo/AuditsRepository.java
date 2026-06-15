package dev.shipping.shipments.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.shipping.shipments.model.Audits;
 
public interface AuditsRepository extends JpaRepository<Audits, String> {

	// JPA is case-sesitive so use the reserved words like SELECT, FROM, WHERE, AND, OR, ORDER BY, GROUP BY in uppercase
	 
	  @Query("SELECT a FROM Audits a WHERE a.entityId = :entityId ORDER BY a.auditId DESC")
      Optional<List<Audits>> getAuditDetailsByEntityId(String entityId);
	 	 
}