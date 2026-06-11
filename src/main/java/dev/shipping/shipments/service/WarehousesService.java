package dev.shipping.shipments.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;

import dev.shipping.shipments.model.Address;
import dev.shipping.shipments.model.Customers;
import dev.shipping.shipments.model.Inventory;
import dev.shipping.shipments.model.Warehouses;
import dev.shipping.shipments.repo.AddressRepository;
import dev.shipping.shipments.repo.WarehousesRepository;
import dev.shipping.shipments.utils.MyCustomUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

/**
 * Core business logic for warehouse operations.
 *
 * Responsibilities:
 *  - CRUD operations for Warehouses and their linked addresses
 *  - Input validation for both new warehouse creation and updates
 *  - Dynamic JPQL query building for the warehouse filter/list view
 *  - Populating the edit-warehouse view model
 *
 * Address handling:
 *  Addresses are stored in a separate table and referenced by a hash-based PK
 *  (built from the address field values). This deduplicates identical addresses
 *  across warehouses. Updating an address therefore creates a new address record
 *  (with a new hash) rather than mutating the old one.
 *
 * All public mutating methods are @Transactional so that a failure
 * mid-operation rolls back every DB write made in that call.
 */
@Service
public class WarehousesService {

    private static final Logger log = LoggerFactory.getLogger(WarehousesService.class);

    /** Valid status values for a warehouse. Used in both create and update validation. */
    private static final List<String> VALID_WAREHOUSE_STATUSES = List.of("Active", "Disabled");

    @Autowired
    private EntityManager entityManager;

    private final WarehousesRepository warehousesRepo;
    private final AddressRepository addressRepo;
    private final AddressService addressService;

    public WarehousesService(WarehousesRepository warehousesRepo, AddressRepository addressRepo,
            AddressService addressService) {
        this.warehousesRepo = warehousesRepo;
        this.addressRepo = addressRepo;
        this.addressService = addressService;
    }

    // ─────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────

    /** Returns all warehouse records with no ordering or filter applied. */
    public List<Warehouses> getAllWarehouses() {
        return warehousesRepo.findAll();
    }

    /** Returns true if a warehouse record exists for the given ID. */
    public boolean warehouseExists(String warehouseId) {
        return warehousesRepo.findById(warehouseId).isPresent();
    }

    /** Returns a single warehouse by ID, or empty Optional if not found. */
    public Optional<Warehouses> getWarehouseById(String warehouseId) {
        return warehousesRepo.findById(warehouseId);
    }

    /**
     * Returns the human-readable warehouse name for a given ID.
     * Throws NoSuchElementException if the warehouse does not exist —
     * caller should verify existence first.
     */
    public String getWarehouseNameById(String warehouseId) {
        return warehousesRepo.findById(warehouseId).get().getWarehouseName();
    }

    /** Returns the delivery address linked to a warehouse, if one exists. */
    public Optional<Address> getWarehouseAddressById(String addressId) {
        return addressRepo.findById(addressId);
    }

    /**
     * Populates all model attributes required by the edit-warehouse page.
     *
     * Attributes set:
     *  - warehouse           – the warehouse entity
     *  - options             – status dropdown values (Active / Disabled)
     *  - selectedStatus      – pre-selects the warehouse's current status
     *  - fullWarehouseAddress– formatted single-line address string for display
     *  - fullAddressToEdit   – raw Address entity for the editable address form
     *
     * Throws RuntimeException if the warehouseId does not exist (caller must verify first).
     */
    public void populateEditWarehouseModel(String warehouseId, Model model) {
        log.info("populateEditWarehouseModel() → warehouseId={}", warehouseId);

        Warehouses warehouse = warehousesRepo.findById(warehouseId)
                .orElseThrow(() -> new RuntimeException("Warehouse not found: " + warehouseId));

        model.addAttribute("warehouse", warehouse);
        model.addAttribute("options", VALID_WAREHOUSE_STATUSES);
        model.addAttribute("selectedStatus", warehouse.getWarehouseStatus());

        // Fetch and format the linked address for display and editing
        Optional<Address> address = getWarehouseAddressById(warehouse.getAddressId());
        model.addAttribute("fullWarehouseAddress", MyCustomUtils.getFormattedAddress(address.get()));
        model.addAttribute("fullAddressToEdit", address.get());
    }

    /**
     * Builds and executes a dynamic JPQL query for the warehouse filter/list view.
     * Any field passed as "ALL" is excluded from the WHERE clause entirely.
     *
     * @param warehouseId     exact ID filter, or "ALL" for no filter
     * @param warehouseStatus status filter, or "ALL" for no filter
     */
    @Transactional
    public List<Warehouses> getWarehousesList(String warehouseId, String warehouseStatus) {

        log.info("getWarehousesList() → warehouseId={}, warehouseStatus={}", warehouseId, warehouseStatus);

        StringBuilder query = new StringBuilder("SELECT w FROM Warehouses w");
        List<String> conditions = new ArrayList<>();

        if (!warehouseId.equals("ALL"))     conditions.add("w.warehouseId = :warehouseId");
        if (!warehouseStatus.equals("ALL")) conditions.add("w.warehouseStatus = :warehouseStatus");

        if (!conditions.isEmpty()) {
            query.append(" WHERE ").append(String.join(" AND ", conditions));
        }

        log.debug("getWarehousesList() → JPQL: {}", query);

        TypedQuery<Warehouses> typedQuery = entityManager.createQuery(query.toString(), Warehouses.class);

        if (!warehouseId.equals("ALL"))     typedQuery.setParameter("warehouseId", warehouseId);
        if (!warehouseStatus.equals("ALL")) typedQuery.setParameter("warehouseStatus", warehouseStatus);

        List<Warehouses> resultList = typedQuery.getResultList();
        log.info("getWarehousesList() → returned {} warehouse(s)", resultList.size());
        return resultList;
    }

    // ─────────────────────────────────────────────
    // VALIDATION
    // ─────────────────────────────────────────────

    /**
     * Validates all fields for a brand-new warehouse.
     *
     * Rules enforced:
     *  - Warehouse ID: alphanumeric + hyphens only, 3–5 characters
     *  - Warehouse Name: 5–30 characters (trimmed)
     *  - Warehouse Status: must be "Active" or "Disabled"
     *
     * @return list of human-readable error messages; empty list means all fields are valid.
     */
    public List<String> validateNewWarehouse(Warehouses warehouse) {

        List<String> errors = new ArrayList<>();
        String warehouseId     = warehouse.getWarehouseId();
        String warehouseName   = warehouse.getWarehouseName();
        String warehouseStatus = warehouse.getWarehouseStatus();

        log.debug("validateNewWarehouse() → warehouseId={}, warehouseStatus={}", warehouseId, warehouseStatus);

        // ── Warehouse ID checks ───────────────────────────────────────────────
        if (!warehouseId.matches("^[a-zA-Z0-9-]+$")) {
            errors.add("Warehouse ID can contain only alphabets, numbers, and hyphens "
                    + "(spaces, symbols, and special characters are not allowed).");
        } else {
            if (warehouseId.length() < 3) {
                errors.add("Warehouse ID \"" + warehouseId + "\" must be at least 3 characters long.");
            } else if (warehouseId.length() > 5) {
                errors.add("Warehouse ID \"" + warehouseId + "\" must be a maximum of 5 characters.");
            }
        }

        // ── Warehouse Name checks ─────────────────────────────────────────────
        int nameLen = warehouseName.trim().length();
        if (nameLen < 5) {
            errors.add("Warehouse Name \"" + warehouseName + "\" must be at least 5 characters long.");
        } else if (nameLen > 30) {
            errors.add("Warehouse Name \"" + warehouseName + "\" must be a maximum of 30 characters.");
        }

        // ── Warehouse Status check ────────────────────────────────────────────
        if (!VALID_WAREHOUSE_STATUSES.contains(warehouseStatus.trim())) {
            errors.add("\"" + warehouseStatus + "\" is not a valid Warehouse Status. "
                    + "Please select Active or Disabled.");
        }

        if (!errors.isEmpty()) {
            log.warn("validateNewWarehouse() → {} validation error(s) for warehouseId={}: {}",
                    errors.size(), warehouseId, errors);
        }

        return errors;
    }

    /**
     * Validates fields when updating an existing warehouse.
     * Warehouse ID cannot be changed on update, so only name and status are checked.
     *
     * Rules enforced:
     *  - Warehouse Name: 5–30 characters (trimmed)
     *  - Warehouse Status: must be "Active" or "Disabled"
     *
     * @return list of human-readable error messages; empty list means all fields are valid.
     */
    public List<String> validateWarehouseUpdate(String warehouseName, String warehouseStatus) {

        List<String> errors = new ArrayList<>();

        log.debug("validateWarehouseUpdate() → warehouseStatus={}", warehouseStatus);

        // ── Warehouse Name checks ─────────────────────────────────────────────
        int nameLen = warehouseName.trim().length();
        if (nameLen < 5) {
            errors.add("Warehouse Name \"" + warehouseName + "\" must be at least 5 characters long.");
        } else if (nameLen > 30) {
            errors.add("Warehouse Name \"" + warehouseName + "\" must be a maximum of 30 characters.");
        }

        // ── Warehouse Status check ────────────────────────────────────────────
        if (!VALID_WAREHOUSE_STATUSES.contains(warehouseStatus.trim())) {
            errors.add("\"" + warehouseStatus + "\" is not a valid Warehouse Status. "
                    + "Please select Active or Disabled.");
        }

        if (!errors.isEmpty()) {
            log.warn("validateWarehouseUpdate() → {} validation error(s): {}", errors.size(), errors);
        }

        return errors;
    }

    // ─────────────────────────────────────────────
    // WRITE
    // ─────────────────────────────────────────────

    /**
     * Persists a new warehouse record and returns the generated warehouse ID.
     * Caller is responsible for duplicate checking before calling this method.
     */
    @Transactional
    public String createWarehouse(Warehouses warehouse) {
        log.info("createWarehouse() → warehouseId={}", warehouse.getWarehouseId());
        String savedId = warehousesRepo.save(warehouse).getWarehouseId();
        log.info("createWarehouse() completed → warehouseId={}", savedId);
        return savedId;
    }

    /**
     * Creates a warehouse and then immediately links a delivery address to it.
     *
     * Address deduplication:
     *  MyCustomUtils.buildAddressFields() hashes the address field values to produce
     *  a deterministic PK. If an identical address already exists in the DB, the
     *  existing record is reused rather than creating a duplicate.
     *
     * @return "WAREHOUSE_CREATED_SUCCESSFULLY_WITH_ID: {id}" on success,
     *         or the exception message string on unexpected failure.
     */
    @Transactional
    public String createWarehouseWithAddress(String warehouseId, Address warehouseAddress) {

        log.info("createWarehouseWithAddress() → warehouseId={}", warehouseId);

        try {
            String addressId = resolveAndPersistAddress(warehouseAddress);

            if (addressId == null || addressId.isEmpty()) {
                log.warn("createWarehouseWithAddress() → address service returned empty ID for warehouseId={}",
                        warehouseId);
                return "FAILED_TO_CREATE_ADDRESS_FOR_WAREHOUSE: " + warehouseId;
            }

            // Link the address and set the short display address on the warehouse record
            warehousesRepo.findById(warehouseId).ifPresent(warehouse -> {
                warehouse.setAddressId(addressId);
                warehouse.setWarehouseShortAddress(warehouseAddress.getState() + ", IN");
                warehousesRepo.save(warehouse);
                log.debug("createWarehouseWithAddress() → linked addressId={} to warehouseId={}",
                        addressId, warehouseId);
            });

            log.info("createWarehouseWithAddress() completed → warehouseId={}", warehouseId);
            return "WAREHOUSE_CREATED_SUCCESSFULLY_WITH_ID: " + warehouseId;

        } catch (Exception e) {
            log.error("createWarehouseWithAddress() → unexpected error for warehouseId={}: {}",
                    warehouseId, e.getMessage(), e);
            return e.getMessage();
        }
    }

    /**
     * Updates the name and status of an existing warehouse.
     * Throws RuntimeException if the warehouseId does not exist.
     */
    @Transactional
    public void updateWarehouse(String warehouseId, String warehouseName, String warehouseStatus) {
        log.info("updateWarehouse() → warehouseId={}, warehouseStatus={}", warehouseId, warehouseStatus);

        Warehouses warehouse = warehousesRepo.findById(warehouseId)
                .orElseThrow(() -> new RuntimeException("Warehouse not found: " + warehouseId));

        warehouse.setWarehouseName(warehouseName);
        warehouse.setWarehouseStatus(warehouseStatus);
        warehousesRepo.save(warehouse);

        log.info("updateWarehouse() completed → warehouseId={}", warehouseId);
    }

    /**
     * Deletes a warehouse only if it has no inventory records.
     *
     * Deletion is blocked if inventory exists for the warehouse, because deleting
     * a warehouse with live inventory would leave orphaned inventory records.
     * The user must first adjust all inventory to zero before deletion is allowed.
     *
     * @return "WAREHOUSE_DELETED" on success,
     *         "WAREHOUSE_DELETION_FAILED_BECAUSE_INVENTORY_EXISTS" if inventory is present.
     */
    @Transactional
    public String deleteWarehouse(String warehouseId) {
        log.info("deleteWarehouse() → warehouseId={}", warehouseId);

        List<Inventory> inventory = warehousesRepo.getInventoryByWarehouseId(warehouseId);

        if (!inventory.isEmpty()) {
            log.warn("deleteWarehouse() blocked: {} inventory record(s) exist for warehouseId={}",
                    inventory.size(), warehouseId);
            return "WAREHOUSE_DELETION_FAILED_BECAUSE_INVENTORY_EXISTS";
        }

        warehousesRepo.deleteById(warehouseId);
        log.info("deleteWarehouse() completed → warehouseId={}", warehouseId);
        return "WAREHOUSE_DELETED";
    }

    /**
     * Updates the delivery address linked to a warehouse.
     *
     * Because the address PK is a hash of the field values, changing any field
     * produces a different hash → a new address record is created rather than
     * mutating the existing one. The warehouse's addressId foreign key is then
     * updated to point to the new record.
     *
     * @return "ADDRESS UPDATED SUCCESSFULLY" on success, "FAILED TO UPDATE ADDRESS" otherwise.
     */
    @Transactional
    public String updateAddress(String warehouseId, Address address) {
        log.info("updateAddress() → warehouseId={}", warehouseId);

        // Creating a new address record (old hash no longer matches after field changes)
        String addressId = resolveAndPersistAddress(address);

        if (addressId == null) {
            log.warn("updateAddress() → address service returned null for warehouseId={}", warehouseId);
            return "FAILED TO UPDATE ADDRESS";
        }

        // Point the warehouse at the newly created address record
        warehousesRepo.findById(warehouseId).ifPresent(warehouse -> {
            warehouse.setAddressId(addressId);
            warehousesRepo.save(warehouse);
            log.debug("updateAddress() → warehouse {} now points to addressId={}", warehouseId, addressId);
        });

        log.info("updateAddress() completed → warehouseId={}, newAddressId={}", warehouseId, addressId);
        return "ADDRESS UPDATED SUCCESSFULLY";
    }

    // ─────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────

    /**
     * Builds the normalised address entity from the raw input, derives its
     * hash-based PK, and delegates to AddressService to upsert it.
     *
     * Returns the addressId (hash string) that was persisted, or null on failure.
     */
    private String resolveAndPersistAddress(Address rawAddress) {
        // buildAddressFields normalises the address fields and returns Map<hash → Address>
        Map<String, Address> addrMap = MyCustomUtils.buildAddressFields(rawAddress);
        String addrHash = addrMap.keySet().iterator().next();
        Address normalisedAddress = addrMap.getOrDefault(addrHash, new Address());

        log.debug("resolveAndPersistAddress() → addrHash={}", addrHash);
        return addressService.createAddress(addrHash, normalisedAddress);
    }

	public List<Customers> getCustomersByWarehouseId(String getCustomersByWarehouseId) {
		return warehousesRepo.getCustomersByWarehouseId(getCustomersByWarehouseId);		  
	}
}