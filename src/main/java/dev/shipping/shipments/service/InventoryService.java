package dev.shipping.shipments.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.shipping.shipments.model.AuditFieldChange;
import dev.shipping.shipments.model.Audits;
import dev.shipping.shipments.model.Customers;
import dev.shipping.shipments.model.Warehouses;
import dev.shipping.shipments.model.Inventory;
import dev.shipping.shipments.model.InventoryCheckResult;
import dev.shipping.shipments.model.Items;
import dev.shipping.shipments.model.ShipmentLines;
import dev.shipping.shipments.repo.AuditsRepository;
import dev.shipping.shipments.repo.CustomersRepository;
import dev.shipping.shipments.repo.InventoryRepository;
import dev.shipping.shipments.repo.ItemsRepository;
import dev.shipping.shipments.repo.WarehousesRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import tools.jackson.databind.ObjectMapper;
import dev.shipping.shipments.utils.MyCustomUtils;

/**
 * Core business logic for inventory operations.
 *
 * Responsibilities: - Read inventory with optional filters (dynamic JPQL) -
 * Create or update inventory quantities with guard-rails (item must exist, item
 * must be Active, quantity cannot go negative) - Check inventory availability
 * for a list of proposed shipment lines using a single efficient SQL query with
 * CTEs
 *
 * All public mutating methods are @Transactional so that a failure
 * mid-operation rolls back every DB write made in that call.
 */
@Service
public class InventoryService {

	private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

	@Autowired
	private EntityManager entityManager;

	private final InventoryRepository inventoryRepo;
	private final ItemsRepository itemsRepo;
	private final CustomersRepository customersRepo;
	private final ItemsService itemsService;
	private final WarehousesRepository warehousesRepo;
	private final AuditsRepository auditsRepo;

	public InventoryService(InventoryRepository inventoryRepo, ItemsRepository itemsRepo,
			CustomersRepository customersRepo, ItemsService itemsService, WarehousesRepository warehousesRepo,
			AuditsRepository auditsRepo) {
		this.inventoryRepo = inventoryRepo;
		this.itemsRepo = itemsRepo;
		this.customersRepo = customersRepo;
		this.itemsService = itemsService;
		this.warehousesRepo = warehousesRepo;
		this.auditsRepo = auditsRepo;
	}

	// ─────────────────────────────────────────────
	// READ
	// ─────────────────────────────────────────────

	/**
	 * Returns all inventory records that are considered "valid" (as defined by the
	 * repo query — typically quantity > 0 or similar).
	 */
	public List<Inventory> getAllInventory() {
		return inventoryRepo.getValidInventory();
	}

	/**
	 * Looks up a single inventory record by its composite PK. PK format:
	 * itemId_customerId_itemUom_warehouseId (e.g. "SKU001_CUST01_EACH_WH01")
	 *
	 * Returns empty Optional if no record exists for that combination.
	 */
	public Optional<Inventory> getInventoryByItemCustomerUomWarehouseId(String itemCustomerUomWarehouseId) {
		return inventoryRepo.findById(itemCustomerUomWarehouseId);
	}

	/**
	 * Builds and executes a dynamic JPQL query for the inventory filter view. Any
	 * field passed as "ALL" is excluded from the WHERE clause entirely, so the
	 * query only filters on fields the user actually specified.
	 *
	 * @param customerId  customer filter, or "ALL" for no filter
	 * @param warehouseId warehouse filter, or "ALL" for no filter
	 * @param itemId      item ID filter, or "ALL" for no filter
	 * @param itemUom     unit-of-measure filter, or "ALL" for no filter
	 */
	@Transactional
	public List<Inventory> getInventoryDetails(String customerId, String warehouseId, String itemId, String itemUom) {

		log.info("getInventoryDetails() → customerId={}, warehouseId={}, itemId={}, itemUom={}", customerId,
				warehouseId, itemId, itemUom);

		StringBuilder query = new StringBuilder("SELECT i FROM Inventory i");
		List<String> conditions = new ArrayList<>();

		// Only include a condition for fields the user actually filtered on
		if (!customerId.equals("ALL"))
			conditions.add("i.customerId = :customerId");
		if (!warehouseId.equals("ALL"))
			conditions.add("i.warehouseId = :warehouseId");
		if (!itemId.equals("ALL"))
			conditions.add("i.itemId = :itemId");
		if (!itemUom.equals("ALL"))
			conditions.add("i.itemUom = :itemUom");

		if (!conditions.isEmpty()) {
			query.append(" WHERE ").append(String.join(" AND ", conditions));
		}

		log.debug("getInventoryDetails() → JPQL: {}", query);

		TypedQuery<Inventory> typedQuery = entityManager.createQuery(query.toString(), Inventory.class);

		if (!customerId.equals("ALL"))
			typedQuery.setParameter("customerId", customerId);
		if (!warehouseId.equals("ALL"))
			typedQuery.setParameter("warehouseId", warehouseId);
		if (!itemId.equals("ALL"))
			typedQuery.setParameter("itemId", itemId);
		if (!itemUom.equals("ALL"))
			typedQuery.setParameter("itemUom", itemUom);

		List<Inventory> resultList = typedQuery.getResultList();
		log.info("getInventoryDetails() → returned {} record(s)", resultList.size());
		return resultList;
	}

	// ─────────────────────────────────────────────
	// CREATE / UPDATE
	// ─────────────────────────────────────────────

	/**
	 * Creates a new inventory record or adjusts the quantity of an existing one.
	 *
	 * Pre-conditions (checked in order): 1. The item-customer-UOM combination must
	 * exist in the items table. 2. The item's status must be "Active" (not
	 * "Disabled"). 3. For a "decreaseBy" adjustment, the requested quantity must
	 * not exceed the current on-hand quantity (no negative inventory allowed).
	 *
	 * Adjustment logic: - "increaseBy" → quantity is added to the existing value -
	 * "decreaseBy" → quantity is subtracted from the existing value - New record →
	 * the passed-in inventory object is saved as-is
	 *
	 * @param inventory                  the inventory entity (used only for new
	 *                                   records)
	 * @param itemCustomerUomId          composite key: itemId_customerId_itemUom
	 * @param itemCustomerUomWarehouseId full composite PK:
	 *                                   itemId_customerId_itemUom_warehouseId
	 * @param quantity                   the adjustment amount (always positive;
	 *                                   direction set by adjustmentType)
	 * @param adjustmentType             "increaseBy" or "decreaseBy"
	 *
	 * @return one of: "ITEM_NOT_FOUND" – the item-customer-UOM combination doesn't
	 *         exist "ITEM_DISABLED" – the item exists but is in Disabled status
	 *         "CANNOT_MAKE_INVENTORY_NEGATIVE" – decreaseBy qty exceeds current
	 *         on-hand qty "INVENTORY_UPDATED" – record was successfully created or
	 *         updated
	 */
	@Transactional
	public String createOrUpdateInventory(Inventory inventory, String customerId, String warehouseId,
			String itemCustomerUomId, String itemCustomerUomWarehouseId, int quantity, String adjustmentType) {

		log.info("createOrUpdateInventory() → itemCustomerUomWarehouseId={}, quantity={}, adjustmentType={}",
				itemCustomerUomWarehouseId, quantity, adjustmentType);

		// ── 1. Customer existence & Active check
		Customers customer = customersRepo.findIfCustomerIsActiveAndHasValidUptoDate(customerId);

		log.info("createOrUpdateInventory() → findIfCustomerIsActiveAndHasValidUptoDate for customer={} is {} ",
				customerId, customer);

		if (customer == null) {
			log.warn("createOrUpdateInventory() →  customer not found/inactive/expired: customerId={}", customerId);
			return "CUSTOMER_ERROR";
		}

		// ── 2. Warehouse existence & Active check
		Warehouses warehouse = warehousesRepo.findIfWarehouseIsActive(warehouseId);

		log.info("createOrUpdateInventory() → findIfWarehouseIsActive for warehouse={} is {} ", warehouseId, warehouse);

		if (warehouse == null) {
			log.warn("createOrUpdateInventory() → item not found: itemCustomerUomId={}", itemCustomerUomId);
			return "WAREHOUSE_INACTIVE";
		}

		// ── 3. Item existence check ───────────────────────────────────────────
		Optional<Items> item = itemsRepo.findById(itemCustomerUomId);
		if (item.isEmpty()) {
			log.warn("createOrUpdateInventory() → item not found: itemCustomerUomId={}", itemCustomerUomId);
			return "ITEM_NOT_FOUND";
		}

		// ── 4. Item active status check ───────────────────────────────────────
		if ("DISABLED".equalsIgnoreCase(item.get().getItemStatus())) {
			log.warn("createOrUpdateInventory() → item is Disabled: itemCustomerUomId={}", itemCustomerUomId);
			return "ITEM_DISABLED";
		}

		Optional<Inventory> existing = inventoryRepo.findById(itemCustomerUomWarehouseId);

		if (existing.isPresent()) {
			// ── Update existing record ────────────────────────────────────────
			Inventory toUpdate = existing.get();
			int currentQty = toUpdate.getQuantity();

			// ── 3. Negative inventory guard ───────────────────────────────────
			if ("decreaseBy".equals(adjustmentType) && quantity > currentQty) {
				log.warn(
						"createOrUpdateInventory() → would make inventory negative: "
								+ "itemCustomerUomWarehouseId={}, currentQty={}, requestedDecrease={}",
						itemCustomerUomWarehouseId, currentQty, quantity);
				return "CANNOT_MAKE_INVENTORY_NEGATIVE";
			}

			// This is to build the audit-json and this needs to be before the item.set
			// methods else the old & new values will be same and audit will not reflect
			// correctly
			Inventory currentInventory = inventoryRepo.findById(itemCustomerUomWarehouseId)
					.orElseThrow(() -> new RuntimeException("Inventory not found: " + itemCustomerUomWarehouseId));

			// Apply the adjustment: increase adds, decrease subtracts
			int adjustedQty = "increaseBy".equals(adjustmentType) ? quantity : -quantity;
			toUpdate.setQuantity(currentQty + adjustedQty);

			inventoryRepo.save(toUpdate);
			log.info(
					"createOrUpdateInventory() → updated: itemCustomerUomWarehouseId={}, oldQty={}, adjustment={}, newQty={}",
					itemCustomerUomWarehouseId, currentQty, adjustedQty, toUpdate.getQuantity());

			String auditData = buildInventoryChangesObjectForAudit_UPDATE(currentQty , adjustmentType, quantity);
			log.info("createOrUpdateInventory() auditData = {}", auditData);
			log.info("createOrUpdateInventory() completed → itemCustomerUomWarehouseId={}", itemCustomerUomId);
			Audits audit = MyCustomUtils.setFieldsForAudit("INVENTORY", itemCustomerUomWarehouseId, "MODIFY", auditData,
					"ADMIN");
			auditsRepo.save(audit);
			log.info("createOrUpdateInventory() audit={} saved for → itemCustomerUomWarehouseId={}", audit.toString(),
					itemCustomerUomWarehouseId);

		} else {
			// ── Create new record ─────────────────────────────────────────────
			inventoryRepo.save(inventory);
			log.info("createOrUpdateInventory() → created new record: itemCustomerUomWarehouseId={}",
					itemCustomerUomWarehouseId);

			Inventory createdInventory = inventoryRepo.findById(itemCustomerUomWarehouseId).get();

			String auditData = buildInventoryChangesObjectForAudit_CREATE(createdInventory,
					new ArrayList<AuditFieldChange>());
			log.info("createOrUpdateInventory() auditData = {}", auditData);
			Audits audit = MyCustomUtils.setFieldsForAudit("INVENTORY", itemCustomerUomWarehouseId, "CREATE", auditData,
					"SYSTEM");
			auditsRepo.save(audit);
			log.info("createOrUpdateInventory() audit={} saved for → itemCustomerUomWarehouseId={}", audit.toString(),
					itemCustomerUomWarehouseId);

		}

		return "INVENTORY_UPDATED";
	}

	public String buildInventoryChangesObjectForAudit_CREATE(Inventory inventory, List<AuditFieldChange> changes) {
		changes.add(new AuditFieldChange("ItemCustomerUomWarehouseId", "", inventory.getItemCustomerUomWarehouseId()));
		changes.add(new AuditFieldChange("Item Id", "", inventory.getItemId()));
		changes.add(new AuditFieldChange("Uom", "", inventory.getItemUom()));
		changes.add(new AuditFieldChange("Customer", "", inventory.getCustomerId()));
		changes.add(new AuditFieldChange("Quantity", "", Integer.toString(inventory.getQuantity())));
		changes.add(new AuditFieldChange("Allocated Quantity", "", Integer.toString(inventory.getAllocatedQuantity())));
		changes.add(new AuditFieldChange("Available Quantity", "", Integer.toString(inventory.getAvailableQuantity())));
		changes.add(new AuditFieldChange("Warehouse Id", "", inventory.getWarehouseId()));
		changes.add(new AuditFieldChange("Created At", "", inventory.getCreatedAt()));
		changes.add(new AuditFieldChange("Modified At", "", inventory.getModifiedAt()));

		ObjectMapper mapper = new ObjectMapper();

		String auditData = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(changes);

		return auditData;
	}

	public String buildInventoryChangesObjectForAudit_UPDATE(int existingQuantity, String adjustmentType, int quantity) {
		List<AuditFieldChange> changes = new ArrayList<>();

		changes.add(new AuditFieldChange("Adjustment Type", "", adjustmentType+" "+quantity));
		
		int newQuantity= 0;
		String newQuantityString = "", oldQuantityString = Integer.toString(existingQuantity), quantityToUpdateString=Integer.toString(quantity);
		if("increaseBy".equals(adjustmentType)) {
			newQuantity = existingQuantity + quantity;
			newQuantityString =		"( "+oldQuantityString+" + "+ quantityToUpdateString+" = ) "+ Integer.toString(newQuantity);
		}else {
			newQuantity = existingQuantity - quantity;
			newQuantityString =		"( "+oldQuantityString+" - "+ quantityToUpdateString+" = ) "+ Integer.toString(newQuantity);
		}		

		changes.add(new AuditFieldChange("Quantity", oldQuantityString,newQuantityString));

		ObjectMapper mapper = new ObjectMapper();

		String auditData = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(changes);

		return auditData;
	}

	// ─────────────────────────────────────────────
	// INVENTORY CHECK FOR SHIPMENT LINES
	// ─────────────────────────────────────────────

	/**
	 * Checks inventory availability for a list of proposed shipment lines in a
	 * single SQL round-trip, using two CTEs to keep the logic readable.
	 *
	 * CTE 1 – ExistingCustomerWarehouses: Finds all warehouses that are configured
	 * for the given customer. Used to flag lines whose warehouse is not yet enabled
	 * for this customer.
	 *
	 * CTE 2 – RequestedLines: Built dynamically from the input list using UNION
	 * ALL, creating an in-memory table of (item_id, item_uom, requested_qty) rows
	 * for the join.
	 *
	 * Main SELECT: Joins inventory records against RequestedLines on item_id +
	 * item_uom, scoped to the given customer and records with quantity > 0. The
	 * CASE expression in the SELECT derives a human-readable stock_status per line:
	 * - "WAREHOUSE NOT CONFIGURED / ENABLED FOR THIS CUSTOMER" - "SHORTAGE BY <n>"
	 * (when available_quantity < requested_qty) - "OK"
	 *
	 * Each result row is mapped to an InventoryCheckResult DTO.
	 *
	 * @param customerId the customer whose inventory and warehouse config to check
	 * @param lines      the proposed shipment lines to check
	 * @return list of InventoryCheckResult DTOs, one per matching inventory record;
	 *         empty list if lines is null or empty.
	 */
	@Transactional(readOnly = true)
	public List<InventoryCheckResult> getShortageInventoryForShipmentLines(String customerId,
			List<ShipmentLines> lines) {

		log.info("getShortageInventoryForShipmentLines() → customerId={}, lineCount={}", customerId,
				lines == null ? 0 : lines.size());

		if (lines == null || lines.isEmpty()) {
			log.warn(
					"getShortageInventoryForShipmentLines() → called with null or empty lines list; returning empty result");
			return Collections.emptyList();
		}

		// ── Build the RequestedLines CTE rows from the input list ─────────────
		// Each line becomes one SELECT row; rows are combined with UNION ALL.
		// Example output for two lines:
		// SELECT 'SKU001' AS item_id, 'EACH' AS item_uom, 10 AS requested_qty
		// UNION ALL
		// SELECT 'SKU002' AS item_id, 'MTR' AS item_uom, 5 AS requested_qty
		StringBuilder requestedLineRows = new StringBuilder();
		for (int i = 0; i < lines.size(); i++) {
			ShipmentLines line = lines.get(i);
			requestedLineRows.append("SELECT '").append(line.getItemId()).append("' AS item_id, '")
					.append(line.getItemUom()).append("' AS item_uom, ").append(line.getQuantity())
					.append(" AS requested_qty");
			if (i < lines.size() - 1) {
				requestedLineRows.append(" UNION ALL ");
			}
		}

		log.debug("getShortageInventoryForShipmentLines() → RequestedLines CTE rows: {}", requestedLineRows);

		// ── Assemble the full SQL query ───────────────────────────────────────
		// Note: INTERVAL syntax is MySQL-specific; the time-unit 'NOW(6)' uses
		// microsecond precision to avoid rounding issues at midnight.
		String sql = """
				WITH
				ExistingCustomerWarehouses AS (
				    SELECT warehouse_id
				    FROM customer_warehouses
				    WHERE TRIM(customer_id) = :customerId
				),
				RequestedLines AS (
				""" + requestedLineRows + """
				)
				SELECT
				    i.customer_id,
				    i.item_id,
				    i.warehouse_id,
				    i.item_uom,
				    i.quantity,
				    i.allocated_quantity,
				    i.available_quantity,
				    r.requested_qty,
				    CASE
				        WHEN i.warehouse_id NOT IN (SELECT warehouse_id FROM ExistingCustomerWarehouses)
				            THEN 'WAREHOUSE NOT CONFIGURED / ENABLED FOR THIS CUSTOMER'
				        WHEN i.available_quantity < r.requested_qty
				            THEN CONCAT('SHORTAGE BY ', (r.requested_qty - i.available_quantity))
				        ELSE 'OK'
				    END AS stock_status,
				    i.item_customer_uom_warehouse_id
				FROM inventory i
				JOIN RequestedLines r
				    ON TRIM(i.item_id) = r.item_id
				    AND TRIM(i.item_uom) = r.item_uom
				WHERE TRIM(i.customer_id) = :customerId
				  AND i.quantity > 0
				""";

		Query query = entityManager.createNativeQuery(sql);
		query.setParameter("customerId", customerId);

		// ── Map raw Object[] rows to InventoryCheckResult DTOs ────────────────
		// Column order must match the SELECT list above exactly.
		@SuppressWarnings("unchecked")
		List<Object[]> rows = query.getResultList();
		List<InventoryCheckResult> results = new ArrayList<>();

		for (Object[] row : rows) {
			results.add(new InventoryCheckResult((String) row[0], // customer_id
					(String) row[1], // item_id
					(String) row[2], // warehouse_id
					(String) row[3], // item_uom
					((Number) row[4]).intValue(), // quantity (on-hand)
					((Number) row[5]).intValue(), // allocated_quantity
					((Number) row[6]).intValue(), // available_quantity
					((Number) row[7]).intValue(), // requested_qty
					(String) row[8], // stock_status
					(String) row[9] // item_customer_uom_warehouse_id
			));
		}

		log.info("getShortageInventoryForShipmentLines() → returned {} result(s) for customerId={}", results.size(),
				customerId);
		return results;
	}

	/*
	 * public List<Object> checkIfItemsAndInventoryExists(String customerId,
	 * List<ShipmentLines> lines) {
	 * 
	 * List<Object> results = new ArrayList<>(); String itemId, itemUom, itemErrors
	 * = "", inventoryErrors = "", errorMessages = ""; List<Inventory> invRes; int i
	 * = 0;
	 * 
	 * log.info("InventoryService:: checkIfItemsAndInventoryExists() → lines: " +
	 * lines.toString());
	 * 
	 * for (ShipmentLines sl : lines) { itemId = sl.getItemId(); itemUom =
	 * sl.getItemUom(); boolean itemExist = itemsService.itemExists(itemId + "_" +
	 * customerId + "_" + itemUom);
	 * 
	 * if (!itemExist) { ++i; itemErrors += "<br /> Item: " + itemId + ", Uom: " +
	 * itemUom + ", Customer: " + customerId;
	 * log.warn("InventoryService:: checkIfItemsAndInventoryExists() → ITEM: " +
	 * itemId + ", UOM: " + itemUom + ", CUTSOMER: " + customerId +
	 * " -- Item doesnt exist"); } else { invRes =
	 * inventoryRepo.getInventoryDetailsToVerifyAvailability(customerId, itemId,
	 * itemUom); if (invRes.isEmpty()) { ++i; inventoryErrors += "<br /> Item: " +
	 * itemId + ", Uom: " + itemUom + ", Customer: " + customerId; log.
	 * warn("InventoryService:: checkIfItemsAndInventoryExists() → invRes.isEmpty(): "
	 * + invRes.toString() + " → ITEM: " + itemId + ", UOM: " + itemUom +
	 * ", CUTSOMER: " + customerId + " -- Inventory doesnt exist"); } else { log.
	 * info("InventoryService:: checkIfItemsAndInventoryExists() → invRes.isEmpty(): "
	 * + invRes.toString() + " → ITEM: " + itemId + ", UOM: " + itemUom +
	 * ", CUTSOMER: " + customerId + " -- Item is valid & Inventory exists"); } } }
	 * 
	 * if (itemErrors != "") errorMessages +=
	 * "Below items are invalid. The combination of item, uom, customer doesnt exist.  "
	 * + itemErrors +
	 * "<br />------------------------------------------------------------------------------------------------------------------------------<br /><br />"
	 * ;
	 * 
	 * if (inventoryErrors != "") errorMessages +=
	 * "Inventory doesnt exist for below item, uom, customer combination in any warehouse(s).  "
	 * + inventoryErrors +
	 * "<br /><br />------------------------------------------------------------------------------------------------------------------------------ "
	 * ;
	 * 
	 * log.
	 * info("InventoryService:: checkIfItemsAndInventoryExists() → itemErrors & inventoryErrors: "
	 * + itemErrors + " , " + inventoryErrors);
	 * 
	 * results.add(i); results.add(errorMessages);
	 * 
	 * log.info("InventoryService:: checkIfItemsAndInventoryExists() → results[]: "
	 * + results.get(0) + " , " + results.get(1));
	 * 
	 * return results; }
	 */

	public List<Object> checkIfItemsAndInventoryExists(String customerId, List<ShipmentLines> lines) {

		List<Object> results = new ArrayList<>();
		String itemId, itemUom, errorMessages = "";
		List<Inventory> invRes;
		int i = 0;

		log.info("InventoryService:: checkIfItemsAndInventoryExists() → lines: " + lines.toString());

		String tableStyle = "style='width:50%; border-collapse:collapse; pointer-events:none;'";
		String thStyle = "style='border:1px solid #000; padding:6px 10px; background-color:#f2f2f2;'";
		String tdStyle = "style='border:1px solid #000; padding:6px 10px;'";

		String tableOpen = "<table " + tableStyle + ">" + "<tr>" + "<th " + thStyle + ">Customer</th>" + "<th "
				+ thStyle + ">Item</th>" + "<th " + thStyle + ">UOM</th>" + "</tr>";

		String itemErrors = tableOpen;
		String inventoryErrors = tableOpen;
		boolean hasItemErrors = false;
		boolean hasInventoryErrors = false;

		for (ShipmentLines sl : lines) {
			itemId = sl.getItemId();
			itemUom = sl.getItemUom();
			boolean itemExist = itemsService.itemExistsAndActive(itemId + "_" + customerId + "_" + itemUom);

			if (!itemExist) {
				++i;
				hasItemErrors = true;
				itemErrors += "<tr>" + "<td " + tdStyle + ">" + customerId + "</td>" + "<td " + tdStyle + ">" + itemId
						+ "</td>" + "<td " + tdStyle + ">" + itemUom + "</td>" + "</tr>";
				log.warn("InventoryService:: checkIfItemsAndInventoryExists() → ITEM: " + itemId + ", UOM: " + itemUom
						+ ", CUTSOMER: " + customerId + " -- Item doesnt exist or Inactive");
			} else {
				invRes = inventoryRepo.getInventoryDetailsToVerifyAvailability(customerId, itemId, itemUom);
				if (invRes.isEmpty()) {
					++i;
					hasInventoryErrors = true;
					inventoryErrors += "<tr>" + "<td " + tdStyle + ">" + customerId + "</td>" + "<td " + tdStyle + ">"
							+ itemId + "</td>" + "<td " + tdStyle + ">" + itemUom + "</td>" + "</tr>";
					log.warn("InventoryService:: checkIfItemsAndInventoryExists() → invRes.isEmpty(): "
							+ invRes.toString() + " → ITEM: " + itemId + ", UOM: " + itemUom + ", CUTSOMER: "
							+ customerId + " -- Inventory doesnt exist");
				} else {
					log.info("InventoryService:: checkIfItemsAndInventoryExists() → invRes.isEmpty(): "
							+ invRes.toString() + " → ITEM: " + itemId + ", UOM: " + itemUom + ", CUTSOMER: "
							+ customerId + " -- Item is valid & Inventory exists");
				}
			}
		}

		if (hasItemErrors)
			errorMessages += "Below items are invalid. The combination of item, uom, customer doesnt exist or item is Inactive.  "
					+ itemErrors + "</table>"
					+ "<br />------------------------------------------------------------------------------------------------------------------------------<br /><br />";

		if (hasInventoryErrors)
			errorMessages += "Inventory doesnt exist for below item, uom, customer combination in any warehouse(s).  "
					+ inventoryErrors + "</table>"
					+ "<br />------------------------------------------------------------------------------------------------------------------------------ ";

		log.info("InventoryService:: checkIfItemsAndInventoryExists() → itemErrors & inventoryErrors: " + itemErrors
				+ " , " + inventoryErrors);

		results.add(i);
		results.add(errorMessages);

		log.info("InventoryService:: checkIfItemsAndInventoryExists() → results[]: " + results.get(0) + " , "
				+ results.get(1));

		return results;
	}

}