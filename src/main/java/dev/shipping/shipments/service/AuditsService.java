
package dev.shipping.shipments.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;

import dev.shipping.shipments.model.Address;
import dev.shipping.shipments.model.Audits;
import dev.shipping.shipments.model.Items;
import dev.shipping.shipments.model.Warehouses;
import dev.shipping.shipments.repo.AddressRepository;
import dev.shipping.shipments.repo.AuditsRepository;
import dev.shipping.shipments.repo.WarehousesRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import dev.shipping.shipments.utils.MyCustomUtils;

@Service
public class AuditsService {

	private static final Logger log = LoggerFactory.getLogger(ItemsService.class);

	private final AuditsRepository auditsRepo;

	public AuditsService(AuditsRepository auditsRepo) {
		this.auditsRepo = auditsRepo;
	}
	

	public List<Audits> getAuditDetailsList(String entityId) {

		Optional<List<Audits>> auditDetails = auditsRepo.getAuditDetailsByEntityId(entityId);

		if (!auditDetails.isEmpty())
			return auditDetails.get();

		return null;
	}

	public Audits getAuditByAuditId(String auditId) {

		Optional<Audits> audit = auditsRepo.findById(auditId);

		log.info("AuditsService:getAuditByAuditId() → auditId={}, auditData={}", auditId, audit.toString());
		
		if (audit.isPresent())
			return audit.get();

		return null;
	}

}
