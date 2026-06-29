
package dev.shipping.shipments.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;

import dev.shipping.shipments.model.AuditFieldChange;
import dev.shipping.shipments.model.Audits;
import dev.shipping.shipments.service.AuditsService;
import dev.shipping.shipments.service.ItemsService;
import tools.jackson.databind.ObjectMapper;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller

public class AuditsController {

	private static final Logger log = LoggerFactory.getLogger(ItemsService.class);

	private final AuditsService auditsService;

	public AuditsController(AuditsService auditsService) {
		this.auditsService = auditsService;
	}

	@PostMapping("/audit/showAuditDetails")
	public String showAuditDetails(@RequestParam String auditId, @RequestParam String entityType, Model model) {
		String auditForEntity = "";
		Audits audit = auditsService.getAuditByAuditId(auditId);

		log.info("AuditsController:showAuditDetails() → auditId={}, auditData={}", auditId, audit.toString());

		if (audit != null) {

			ObjectMapper mapper = new ObjectMapper();

			List<AuditFieldChange> changes = Arrays
					.asList(mapper.readValue(audit.getAction(), AuditFieldChange[].class));

			model.addAttribute("audit", audit);
			model.addAttribute("changes", changes);

			// If entityType is ITEM, then instead of displaying itemCustomerUomId, we are
			// splitting it and showing as item, customer, uom.. and if its INVENTORY then adding warehouseId
			// else just show the actual id for customer, warehouse 
			if (entityType.equals("ITEM") || entityType.equals("INVENTORY")) {
				String entityId = audit.getEntityId();

				String[] parts = entityId.split("_");
				String itemId = parts[0];
				String customerId = parts[1];
				String itemUom = parts[2];
				auditForEntity = "Item: " + itemId + "<br /> Uom: " + itemUom + "<br /> Customer: " + customerId;
				
				if (entityType.equals("INVENTORY")) { // if type id INVENTORY then add get warehouseId from entity and add it
					String warehouseId = parts[3];
					auditForEntity += "<br /> Warehouse: " + warehouseId;
				}
			} else {
				auditForEntity = audit.getEntityId();
			}
		}

		model.addAttribute("audit", audit);
		model.addAttribute("auditForEntity", auditForEntity);
		return "show-audit-details";
	}
}
