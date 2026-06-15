package dev.shipping.shipments.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;

import dev.shipping.shipments.model.Audits;
import dev.shipping.shipments.model.AuditFieldChange;
import dev.shipping.shipments.model.Customers;
import dev.shipping.shipments.model.Items;
import dev.shipping.shipments.repo.AuditsRepository;
import dev.shipping.shipments.repo.CustomersRepository;
import dev.shipping.shipments.repo.InventoryRepository;
import dev.shipping.shipments.repo.ItemsRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import utils.MyCustomUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Core business logic for item operations.
 *
 * Responsibilities: - CRUD operations for Items - Input validation for both new
 * item creation and updates - Dynamic JPQL query building for the item
 * filter/list view - Populating the edit-item view model
 *
 * The composite PK for every item is: itemId_customerId_itemUom (e.g.
 * "SKU001_CUST01_EACH"). This is referred to throughout as itemCustomerUomId.
 *
 * All public mutating methods are @Transactional so that a failure
 * mid-operation rolls back every DB write made in that call.
 */
@Service
public class ItemsService {

	private static final Logger log = LoggerFactory.getLogger(ItemsService.class);

	/** Valid status values for an item. Used in update validation. */
	private static final List<String> VALID_ITEM_STATUSES = List.of("Active", "Disabled");

	@Autowired
	private EntityManager entityManager;

	private final ItemsRepository itemsRepo;
	private final CustomersRepository customersRepo;
	private final InventoryRepository inventoryRepo;
	private final AuditsRepository auditsRepo;

	public ItemsService(ItemsRepository itemsRepo, CustomersRepository customersRepo, InventoryRepository inventoryRepo,
			AuditsRepository auditsRepo) {
		this.itemsRepo = itemsRepo;
		this.customersRepo = customersRepo;
		this.inventoryRepo = inventoryRepo;
		this.auditsRepo = auditsRepo;
	}

	// ─────────────────────────────────────────────
	// READ
	// ─────────────────────────────────────────────

	/**
	 * Returns true if an item record exists for the given composite PK. Used as a
	 * pre-check before create (duplicate guard) and before update/delete.
	 */
	public boolean itemExists(String itemCustomerUomId) {
		log.debug("itemExists() → itemCustomerUomId={}", itemCustomerUomId);
		return itemsRepo.findById(itemCustomerUomId).isPresent();
	}
	
	public boolean itemExistsAndActive(String itemCustomerUomId) {
		log.debug("itemExists() → itemCustomerUomId={}", itemCustomerUomId);
		return itemsRepo.findIfItemExistsAndActive(itemCustomerUomId).isPresent();
	}

	/**
	 * Looks up a single item by its composite PK (itemId_customerId_itemUom).
	 * Returns empty Optional if no record exists.
	 */
	public Optional<Items> getItemById(String itemCustomerUomId) {
		log.debug("getItemById() → itemCustomerUomId={}", itemCustomerUomId);
		return itemsRepo.findById(itemCustomerUomId);
	}

	/** Returns all item records with no ordering or filter applied. */
	public List<Items> getAllItems() {
		return itemsRepo.findAll();
	}

	/** Returns all items belonging to a specific customer. */
	public List<Items> getItemsByCustomer(String customerId) {
		return itemsRepo.findItemsByCustomer(customerId);
	}

	/**
	 * Returns only customers whose status is "Active" AND whose contract expiry is
	 * after the start of today. Used to populate the customer dropdown on the
	 * create-item form.
	 */
	public List<Customers> getActiveAndValidCustomers() {
		LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
		return customersRepo.findByCustomerStatusAndValidUpto("Active", startOfToday);
	}

	/**
	 * Populates all model attributes required by the edit-item page.
	 *
	 * Attributes set: - item – the item entity - options – status dropdown values
	 * (Active / Disabled) - selectedItemStatus– pre-selects the item's current
	 * status - selectedUom – pre-selects the item's current UOM
	 *
	 * Throws RuntimeException if the itemCustomerUomId does not exist (caller must
	 * verify existence first).
	 */
	public void populateEditItemModel(String itemCustomerUomId, Model model) {
		log.info("populateEditItemModel() → itemCustomerUomId={}", itemCustomerUomId);

		Items item = itemsRepo.findById(itemCustomerUomId)
				.orElseThrow(() -> new RuntimeException("Item not found: " + itemCustomerUomId));

		model.addAttribute("item", item);
		model.addAttribute("options", VALID_ITEM_STATUSES);
		model.addAttribute("selectedItemStatus", item.getItemStatus());
		model.addAttribute("selectedUom", item.getItemUom());
	}

	/**
	 * Builds and executes a dynamic JPQL query for the item filter/list view. Any
	 * field passed as "ALL" is excluded from the WHERE clause entirely, so the
	 * query only filters on fields the user actually specified.
	 *
	 * @param customerId customer filter, or "ALL" for no filter
	 * @param itemId     item ID filter, or "ALL" for no filter
	 * @param itemUom    UOM filter, or "ALL" for no filter
	 */
	@Transactional
	public List<Items> getItemsList(String customerId, String itemId, String itemUom) {

		log.info("getItemsList() → customerId={}, itemId={}, itemUom={}", customerId, itemId, itemUom);

		StringBuilder query = new StringBuilder("SELECT i FROM Items i");
		List<String> conditions = new ArrayList<>();

		// Only include a condition for fields the user actually filtered on
		if (!customerId.equals("ALL"))
			conditions.add("i.customerId = :customerId");
		if (!itemId.equals("ALL"))
			conditions.add("i.itemId = :itemId");
		if (!itemUom.equals("ALL"))
			conditions.add("i.itemUom = :itemUom");

		if (!conditions.isEmpty()) {
			query.append(" WHERE ").append(String.join(" AND ", conditions));
		}

		log.debug("getItemsList() → JPQL: {}", query);

		TypedQuery<Items> typedQuery = entityManager.createQuery(query.toString(), Items.class);

		if (!customerId.equals("ALL"))
			typedQuery.setParameter("customerId", customerId);
		if (!itemId.equals("ALL"))
			typedQuery.setParameter("itemId", itemId);
		if (!itemUom.equals("ALL"))
			typedQuery.setParameter("itemUom", itemUom);

		List<Items> resultList = typedQuery.getResultList();
		log.info("getItemsList() → returned {} item(s)", resultList.size());
		return resultList;
	}

	// ─────────────────────────────────────────────
	// VALIDATION
	// ─────────────────────────────────────────────

	/**
	 * Validates all fields for a brand-new item.
	 *
	 * Rules enforced: - Item ID: alphanumeric + hyphens only, 5–15 characters -
	 * Item UOM: must be in the provided allow-list - Item Description: 10–50
	 * characters (trimmed)
	 *
	 * @param item         the item entity submitted from the form
	 * @param itemUomsList the list of valid UOM values (passed in from the
	 *                     controller constant)
	 * @return list of human-readable error messages; empty list means all fields
	 *         are valid.
	 */
	public List<String> validateNewItem(Items item, List<String> itemUomsList) {

		List<String> errors = new ArrayList<>();
		String itemId = item.getItemId();
		String itemUom = item.getItemUom();
		String itemDescription = item.getItemDescription();
		String itemName = item.getItemName();

		log.debug("validateNewItem() → itemId={}, itemUom={}", itemId, itemUom);

		// ── Item ID checks ────────────────────────────────────────────────────
		if (!itemId.matches("^[a-zA-Z0-9-]+$")) {
			errors.add("Item ID can contain only alphabets, numbers, and hyphens "
					+ "(spaces, symbols, and special characters are not allowed).");
		} else {
			if (itemId.length() < 5) {
				errors.add("Item ID \"" + itemId + "\" must be at least 5 characters long.");
			} else if (itemId.length() > 15) {
				errors.add("Item ID \"" + itemId + "\" must be a maximum of 15 characters.");
			}
		}

		// ── Item UOM check ────────────────────────────────────────────────────
		// Validates against the allow-list passed in from the controller, keeping
		// the valid values in one place (the controller constant)
		if (!itemUomsList.contains(itemUom)) {
			errors.add("Invalid Item UOM: \"" + itemUom + "\". Please select a valid UOM from the dropdown.");
		}

		// ── Item Name checks ───────────────────────────────────────────
		int nameLen = itemName.trim().length();
		if (nameLen < 5) {
			errors.add("Item Name " + itemName + " must be at least 5 characters long.");
		} else if (nameLen > 15) {
			errors.add("Item Name " + itemName + " must be a maximum of 15 characters.");
		}

		// ── Item Description checks ───────────────────────────────────────────
		int descLen = itemDescription.trim().length();
		if (descLen < 10) {
			errors.add("Item Description " + itemDescription + " must be at least 10 characters long.");
		} else if (descLen > 60) {
			errors.add("Item Description " + itemDescription + " must be a maximum of 60 characters.");
		}

		if (!errors.isEmpty()) {
			log.warn("validateNewItem() → {} validation error(s) for itemId={}: {}", errors.size(), itemId, errors);
		}

		return errors;
	}

	/**
	 * Validates fields when updating an existing item. Item ID and UOM cannot be
	 * changed on update, so only description and status are checked.
	 *
	 * Rules enforced: - Item Description: 10–50 characters (trimmed) - Item Status:
	 * must be "Active" or "Disabled"
	 * 
	 * @param itemName
	 *
	 * @return list of human-readable error messages; empty list means all fields
	 *         are valid.
	 */
	public List<String> validateItemUpdate(String itemDescription, String itemStatus, String itemName) {

		List<String> errors = new ArrayList<>();

		log.debug("validateItemUpdate() → itemStatus={}", itemStatus);

		// ── Item Name checks ───────────────────────────────────────────
		int nameLen = itemName.trim().length();
		if (nameLen < 5) {
			errors.add("Item Name \"" + itemName + "\" must be at least 5 characters long.");
		} else if (nameLen > 15) {
			errors.add("Item Name \"" + itemName + "\" must be a maximum of 15 characters.");
		}

		// ── Item Description checks ───────────────────────────────────────────
		int descLen = itemDescription.trim().length();
		if (descLen < 10) {
			errors.add("Item Description " + itemDescription + " must be at least 10 characters long.");
		} else if (descLen > 60) {
			errors.add("Item Description " + itemDescription + " must be a maximum of 60 characters.");
		}

		// ── Item Status check ─────────────────────────────────────────────────
		if (!VALID_ITEM_STATUSES.contains(itemStatus)) {
			errors.add("Invalid Item Status: \"" + itemStatus + "\". Please select a valid status from the dropdown.");
		}

		if (!errors.isEmpty()) {
			log.warn("validateItemUpdate() → {} validation error(s): {}", errors.size(), errors);
		}

		return errors;
	}

	// ─────────────────────────────────────────────
	// WRITE
	// ─────────────────────────────────────────────

	/**
	 * Persists a new item record. The composite PK (itemId_customerId_itemUom) must
	 * be set on the entity before calling this method; duplicate checking is the
	 * caller's responsibility.
	 */
	@Transactional
	public void createItem(Items item) {

		log.info("createItem() → item={}", item.toString());
		itemsRepo.save(item);
		String itemCustomerUomId = item.getItemCustomerUomId();
		log.info("createItem() completed → itemCustomerUomId={}", itemCustomerUomId);

		String auditData = buildItemChangesObjectForAudit_CREATE(item, new ArrayList<AuditFieldChange>());
		log.info("createItem() auditData = {}", auditData);
		Audits audit = MyCustomUtils.setFieldsForAudit("ITEM", itemCustomerUomId, "CREATE", auditData, "SYSTEM");
		auditsRepo.save(audit);
		log.info("createItem() audit={} saved for → itemCustomerUomId={}", audit.toString(), itemCustomerUomId);

	}

	/**
	 * Updates the editable fields of an existing item (description, UOM, status).
	 * Item ID and customer cannot be changed after creation. Throws
	 * RuntimeException if the itemCustomerUomId does not exist.
	 */
	@Transactional
	public void updateItem(String itemCustomerUomId, String itemDescription, String itemStatus, String itemName) {
		log.info("updateItem() → itemCustomerUomId={}, itemStatus={}, itemUom={}", itemCustomerUomId, itemStatus,
				itemName);

		Items item = itemsRepo.findById(itemCustomerUomId)
				.orElseThrow(() -> new RuntimeException("Item not found: " + itemCustomerUomId));

		// This is to build the audit-json and this needs to be before the item.set
		// methods else the old & new values will be same and audit will not reflect
		// correctly
		String auditData = buildItemChangesObjectForAudit_UPDATE(item, itemDescription, itemName, itemStatus);
		log.info("updateItem() auditData = {}", auditData);

		item.setItemDescription(itemDescription);
		item.setItemName(itemName);
		item.setItemStatus(itemStatus);
		itemsRepo.save(item);
		log.info("updateItem() completed → itemCustomerUomId={}", itemCustomerUomId);

		Audits audit = MyCustomUtils.setFieldsForAudit("ITEM", itemCustomerUomId, "MODIFY", auditData, "ADMIN");
		auditsRepo.save(audit);
		log.info("updateItem() audit={} saved for → itemCustomerUomId={}", audit.toString(), itemCustomerUomId);
	}

	public String buildItemChangesObjectForAudit_UPDATE(Items item, String itemDescription, String itemName,
			String itemStatus) {
		List<AuditFieldChange> changes = new ArrayList<>();

		if (!item.getItemDescription().equals(itemDescription)) {
			changes.add(new AuditFieldChange("Description", item.getItemDescription(), itemDescription));
		}

		if (!item.getItemName().equals(itemName)) {
			changes.add(new AuditFieldChange("Name", item.getItemName(), itemName));
		}

		if (!item.getItemStatus().equals(itemStatus)) {
			changes.add(new AuditFieldChange("Status", item.getItemStatus(), itemStatus));
		}

		ObjectMapper mapper = new ObjectMapper();

		String auditData = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(changes);

		return auditData;
	}

	public String buildItemChangesObjectForAudit_CREATE(Items item, List<AuditFieldChange> changes) {
		changes.add(new AuditFieldChange("ItemCustomerUomId", "", item.getItemCustomerUomId()));
		changes.add(new AuditFieldChange("Item Id", "", item.getItemId()));
		changes.add(new AuditFieldChange("Uom", "", item.getItemUom()));
		changes.add(new AuditFieldChange("Customer", "", item.getCustomerId()));
		changes.add(new AuditFieldChange("Name", "", item.getItemName()));
		changes.add(new AuditFieldChange("Description", "", item.getItemDescription()));
		changes.add(new AuditFieldChange("Status", "", item.getItemStatus()));
		changes.add(new AuditFieldChange("Created At", "", item.getCreatedAt()));
		changes.add(new AuditFieldChange("Modified At", "", item.getModifiedAt()));

		ObjectMapper mapper = new ObjectMapper();

		String auditData = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(changes);

		return auditData;
	}

	/**
	 * Deletes an item record by its composite PK. Caller is responsible for
	 * verifying existence first to surface a meaningful error message rather than a
	 * silent no-op.
	 */
	@Transactional
	public void deleteItem(String itemCustomerUomId) {
		log.info("deleteItem() → itemCustomerUomId={}", itemCustomerUomId);
		Items item = itemsRepo.findById(itemCustomerUomId).get();
		itemsRepo.deleteById(itemCustomerUomId);
		log.info("deleteItem() completed → itemCustomerUomId={}", itemCustomerUomId);

		String auditData = buildItemChangesObjectForAudit_DELETE(item, new ArrayList<AuditFieldChange>());
		log.info("deleteItem() auditData = {}", auditData);
		Audits audit = MyCustomUtils.setFieldsForAudit("ITEM", itemCustomerUomId, "DELETE", auditData, "ADMIN");
		auditsRepo.save(audit);
		log.info("deleteItem() audit={} saved for → itemCustomerUomId={}", audit.toString(), itemCustomerUomId);
	}

	public String buildItemChangesObjectForAudit_DELETE(Items item, List<AuditFieldChange> changes) {
		changes.add(new AuditFieldChange("ItemCustomerUomId", item.getItemCustomerUomId(), ""));
		changes.add(new AuditFieldChange("Item Id", item.getItemId(), ""));
		changes.add(new AuditFieldChange("Uom", item.getItemUom(), ""));
		changes.add(new AuditFieldChange("Customer", item.getCustomerId(), ""));
		changes.add(new AuditFieldChange("Name", item.getItemName(), ""));
		changes.add(new AuditFieldChange("Description", item.getItemDescription(), ""));
		changes.add(new AuditFieldChange("Status", item.getItemStatus(), ""));
		changes.add(new AuditFieldChange("Created At", item.getCreatedAt(), ""));
		changes.add(new AuditFieldChange("Modified At", item.getModifiedAt(), ""));

		ObjectMapper mapper = new ObjectMapper();

		String auditData = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(changes);

		return auditData;
	}

	public List<Items> getCustomerItems(String customerId) {
		return itemsRepo.findItemsByCustomer(customerId);
	}

	public String validateBeforeDeleting(String itemCustomerUomId, String itemId, String customerId, String itemUom) {
		String message = "";

		if (!itemExists(itemCustomerUomId)) {
			message = "Item " + itemId + " doesn't exist for customer: " + customerId + " & UOM: " + itemUom + "!\n";
		}

		if (!inventoryRepo.getInventoryDetailsToVerifyAvailability(customerId, itemId, itemUom).isEmpty()) {
			message += "Inventory exists for Item " + itemId
					+ ". Clear the inventory and then you can delete the item!\n"
					+ "&nbsp;&nbsp;&nbsp;&nbsp;<a style='color: #ffffff;' target='_blank'"
					+ " href='/inventory/showInventoryByFilters?itemId=" + itemId + "&itemUom=" + itemUom
					+ "&customerId=" + customerId + "&warehouseSelect=ALL'>View Inventory</a>";
		}

		log.info("ItemsService → validateBeforeDeleting: itemCustomerUomId={}, message={}", itemCustomerUomId, message);

		return message;
	}

	public String[] itemExistsAndActive(int i, String itemCustomerUomId, String itemId, String itemUom) {
		  
		String formattedItem = "Line# "+Integer.toString(i)+" -- Item: "+ itemId +" -- Uom: "+itemUom;
		if(itemsRepo.findIfItemExistsAndActive(itemCustomerUomId).isPresent()) {
			return new String[] {formattedItem, "TRUE"};
		}
				
		return new String[] {formattedItem, null};	
				 	
	}

}