package dev.shipping.shipments.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;

import dev.shipping.shipments.model.CustomerWarehouses;
import dev.shipping.shipments.model.Customers;
import dev.shipping.shipments.model.Warehouses;
import dev.shipping.shipments.repo.CustomerWarehousesRepository;
import dev.shipping.shipments.repo.CustomersRepository;
import dev.shipping.shipments.repo.WarehousesRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;

/**
 * Core business logic for customer operations.
 *
 * Responsibilities:
 *  - CRUD operations for Customers and their warehouse assignments
 *  - Input validation for both new customer creation and updates
 *  - Dynamic query building for the customer filter/list view
 *  - Populating the edit-customer view model
 *
 * All public mutating methods are @Transactional so that a failure
 * mid-operation rolls back every DB write made in that call.
 */
@Service
public class CustomersService {

    private static final Logger log = LoggerFactory.getLogger(CustomersService.class);

    /** Format used when storing/reading customer contract expiry as a string. */
    private static final DateTimeFormatter STORED_DATE_FMT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss");

    /** Format used by the HTML date-input element (yyyy-MM-dd). */
    private static final DateTimeFormatter HTML_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** Time units accepted for the "expiring within N <unit>" filter. */
    private static final List<String> VALID_EXPIRY_UNITS =
            Arrays.asList("DAY", "WEEK", "MONTH", "QUARTER", "YEAR");

    @Autowired
    private EntityManager entityManager;

    private final CustomersRepository customersRepo;
    private final WarehousesRepository warehousesRepo;
    private final CustomerWarehousesRepository customerWarehousesRepo;

    public CustomersService(CustomersRepository customersRepo, WarehousesRepository warehousesRepo,
            CustomerWarehousesRepository customerWarehousesRepo) {
        this.customersRepo = customersRepo;
        this.warehousesRepo = warehousesRepo;
        this.customerWarehousesRepo = customerWarehousesRepo;
    }

    // ─────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────

    /** Returns all customers with no ordering guarantee. */
    public List<Customers> getAllCustomers() {
        return customersRepo.findAll();
    }

    /** Returns true if a customer record exists for the given ID. */
    public boolean customerExists(String customerId) {
        return customersRepo.findById(customerId).isPresent();
    }

    /** Returns all warehouses whose status is "Active". */
    public List<Warehouses> getActiveWarehouses() {
        return warehousesRepo.findByWarehousesByStatusActive("Active");
    }

    /**
     * Populates all model attributes required by the edit-customer page.
     *
     * Attributes set:
     *  - customer            – the customer entity
     *  - options             – status dropdown values (Active / Disabled)
     *  - selectedStatus      – pre-selects the customer's current status
     *  - assignedWarehouses  – warehouses already linked to this customer
     *  - unassignedWarehouses– warehouses not yet linked (available to add)
     *  - validUptoJustDate   – contract expiry reformatted as yyyy-MM-dd for the HTML date input
     *  - bgColorForValidUpto / textColorForValidUpto / isExpired – set only when contract is expired
     *
     * Throws RuntimeException if the customer ID does not exist (caller must verify first).
     */
    public void populateEditCustomerModel(String customerId, Model model) {

        log.info("populateEditCustomerModel() → customerId={}", customerId);

        Customers customer = customersRepo.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found: " + customerId));

        model.addAttribute("customer", customer);
        model.addAttribute("options", List.of("Active", "Disabled"));
        model.addAttribute("selectedStatus", customer.getCustomerStatus());
        model.addAttribute("assignedWarehouses",
                customerWarehousesRepo.findAllocatedWarehousesByCustomerId(customerId));
        model.addAttribute("unassignedWarehouses",
                customerWarehousesRepo.findWarehousesNotAllocatedToCustomer(customerId));

        // Convert the stored datetime string (e.g. "01-Jun-2026 00:00:00") to a plain
        // date string (e.g. "2026-06-01") for the HTML <input type="date"> element
        LocalDateTime dateTime = LocalDateTime.parse(customer.getValidUpto(), STORED_DATE_FMT);
        String validUptoJustDate = dateTime.format(HTML_DATE_FMT);
        model.addAttribute("validUptoJustDate", validUptoJustDate);

        // Highlight the expiry date field in red if the contract has already expired
        boolean isExpired = dateTime.toLocalDate().isBefore(LocalDate.now());
        if (isExpired) {
            log.warn("populateEditCustomerModel() → contract is expired for customerId={}, validUpto={}",
                    customerId, customer.getValidUpto());
            model.addAttribute("bgColorForValidUpto", "red");
            model.addAttribute("textColorForValidUpto", "yellow");
            model.addAttribute("isExpired", true);
        }
    }

    // ─────────────────────────────────────────────
    // VALIDATION
    // ─────────────────────────────────────────────

    /**
     * Validates all fields for a brand-new customer.
     *
     * Rules enforced:
     *  - Customer ID: alphanumeric + hyphens only, 3–5 characters
     *  - Customer Name: 5–30 characters (trimmed)
     *  - Valid Upto: must be a parseable date AND at least tomorrow
     *
     * @return list of human-readable error messages; empty list means all fields are valid.
     */
    public List<String> validateNewCustomer(Customers customer) {

        List<String> errors = new ArrayList<>();
        String customerId   = customer.getCustomerId();
        String customerName = customer.getCustomerName();
        String validUpto    = customer.getValidUpto();

        log.debug("validateNewCustomer() → customerId={}, customerName={}, validUpto={}",
                customerId, customerName, validUpto);

        // ── Customer ID checks ────────────────────────────────────────────────
        if (!customerId.matches("^[a-zA-Z0-9-]+$")) {
            errors.add("Customer ID can contain only alphabets, numbers, and hyphens "
                    + "(spaces, symbols, and special characters are not allowed).");
        } else {
            if (customerId.length() < 3) {
                errors.add("Customer ID must be at least 3 characters long.");
            } else if (customerId.length() > 5) {
                errors.add("Customer ID must be a maximum of 5 characters.");
            }
        }

        // ── Customer Name checks ──────────────────────────────────────────────
        int nameLen = customerName.trim().length();
        if (nameLen < 5) {
            errors.add("Customer Name \"" + customerName + "\" must be at least 5 characters long.");
        } else if (nameLen > 30) {
            errors.add("Customer Name \"" + customerName + "\" must be a maximum of 30 characters.");
        }

        // ── Valid Upto date check ─────────────────────────────────────────────
        if (validUpto != null && !validUpto.isEmpty()) {
            try {
                // Parsing with the stored format validates calendar correctness
                // (e.g. April 31 or February 30 will throw DateTimeParseException)
                LocalDate selectedDate = LocalDate.parse(validUpto, STORED_DATE_FMT);
                LocalDate tomorrow = LocalDate.now().plusDays(1);

                if (selectedDate.isBefore(tomorrow)) {
                    errors.add("Valid Upto date must be at least tomorrow.");
                }
            } catch (DateTimeParseException e) {
                log.warn("validateNewCustomer() → invalid date string: '{}' → {}", validUpto, e.getMessage());
                errors.add("Invalid date. Please enter a valid date.");
            }
        }

        if (!errors.isEmpty()) {
            log.warn("validateNewCustomer() → {} validation error(s) for customerId={}: {}",
                    errors.size(), customerId, errors);
        }

        return errors;
    }

    /**
     * Validates fields when updating an existing customer.
     * Customer ID is not re-validated here since it cannot be changed on update.
     *
     * Rules enforced:
     *  - Customer Name: 5–30 characters (trimmed)
     *  - Valid Upto: must be present, a parseable yyyy-MM-dd date, and at least tomorrow
     *
     * @return list of human-readable error messages; empty list means all fields are valid.
     */
    public List<String> validateCustomerUpdate(String customerName, String validUpto) {

        List<String> errors = new ArrayList<>();

        log.debug("validateCustomerUpdate() → customerName={}, validUpto={}", customerName, validUpto);

        // ── Customer Name checks ──────────────────────────────────────────────
        int nameLen = customerName.trim().length();
        if (nameLen < 5) {
            errors.add("Cannot update Customer Name to \"" + customerName
                    + "\". Name must be at least 5 characters long.");
        } else if (nameLen > 30) {
            errors.add("Cannot update Customer Name to \"" + customerName
                    + "\". Name must be a maximum of 30 characters.");
        }

        // ── Valid Upto date check ─────────────────────────────────────────────
        // The update form sends the date in yyyy-MM-dd format (HTML date input)
        if (validUpto != null && !validUpto.isEmpty()) {
            try {
                LocalDate selectedDate = LocalDate.parse(validUpto, HTML_DATE_FMT);
                LocalDate tomorrow = LocalDate.now().plusDays(1);

                if (selectedDate.isBefore(tomorrow)) {
                    errors.add("Valid Upto date must be at least tomorrow.");
                }
            } catch (DateTimeParseException e) {
                log.warn("validateCustomerUpdate() → invalid date string: '{}' → {}", validUpto, e.getMessage());
                errors.add("Invalid date. Please select or enter a valid date.");
            }
        } else {
            // A blank or null date is always invalid on an update
            log.warn("validateCustomerUpdate() → validUpto is blank or null");
            errors.add("Valid Upto date is required. Please select or enter a valid date.");
        }

        if (!errors.isEmpty()) {
            log.warn("validateCustomerUpdate() → {} validation error(s): {}", errors.size(), errors);
        }

        return errors;
    }

    // ─────────────────────────────────────────────
    // WRITE
    // ─────────────────────────────────────────────

    /**
     * Persists a new customer and creates customer-warehouse link records for any
     * warehouses selected during creation.
     *
     * Both the customer row and all warehouse links are written in a single
     * transaction — if any insert fails, everything is rolled back.
     */
    @Transactional
    public void createCustomer(Customers customer, List<String> selectedWarehouses) {

        log.info("createCustomer() → customerId={}, warehouseCount={}",
                customer.getCustomerId(), selectedWarehouses.size());

        customersRepo.save(customer);

        // Link the customer to each selected warehouse
        for (String warehouseId : selectedWarehouses) {
            CustomerWarehouses cw = new CustomerWarehouses();
            cw.setCustomerId(customer.getCustomerId());
            cw.setWarehouseId(warehouseId);
            customerWarehousesRepo.save(cw);
            log.debug("createCustomer() → linked warehouseId={} to customerId={}",
                    warehouseId, customer.getCustomerId());
        }

        log.info("createCustomer() completed → customerId={}", customer.getCustomerId());
    }

    /**
     * Updates an existing customer's editable fields (name, status, contract expiry)
     * and syncs their warehouse assignments:
     *  - Inserts link records only for newly added warehouses (not already in DB)
     *  - Deletes link records only for warehouses that were unchecked (removed)
     *  - Leaves unchanged assignments untouched (no redundant deletes/inserts)
     *
     * All changes are wrapped in a single transaction.
     */
    @Transactional
    public void updateCustomer(String customerId, String customerName, String customerStatus,
            String validUpto, List<String> selectedWarehouses) {

        log.info("updateCustomer() → customerId={}, customerName={}, status={}, validUpto={}, warehouseCount={}",
                customerId, customerName, customerStatus, validUpto, selectedWarehouses.size());

        // ── Update customer fields ─────────────────────────────────────────────
        Customers customer = customersRepo.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found: " + customerId));

        customer.setCustomerName(customerName);
        customer.setValidUpto(validUpto);
        customer.setCustomerStatus(customerStatus);
        customersRepo.save(customer);

        // ── Sync warehouse assignments ─────────────────────────────────────────
        // Fetch what is currently stored in the DB for this customer
        List<String> currentIds = customerWarehousesRepo.findAllWarehousesByCustomerId(customerId);

        // Warehouses in the new selection but NOT in the current DB state → insert
        List<String> toInsert = selectedWarehouses.stream()
                .filter(id -> !currentIds.contains(id))
                .collect(Collectors.toList());

        // Warehouses in the current DB state but NOT in the new selection → delete
        List<String> toDelete = currentIds.stream()
                .filter(id -> !selectedWarehouses.contains(id))
                .collect(Collectors.toList());

        log.debug("updateCustomer() warehouse sync → toInsert={}, toDelete={}", toInsert, toDelete);

        toInsert.forEach(wId -> {
            CustomerWarehouses cw = new CustomerWarehouses();
            cw.setCustomerId(customerId);
            cw.setWarehouseId(wId);
            customerWarehousesRepo.save(cw);
        });

        if (!toDelete.isEmpty()) {
            customerWarehousesRepo.deleteByCustomerIdAndWarehouseIdIn(customerId, toDelete);
        }

        log.info("updateCustomer() completed → customerId={}, inserted={}, deleted={}",
                customerId, toInsert.size(), toDelete.size());
    }

    /**
     * Deletes the customer record and all their warehouse assignment links.
     * Both deletes happen in the same transaction.
     */
    @Transactional
    public void deleteCustomer(String customerId) {
        log.info("deleteCustomer() → customerId={}", customerId);
        customersRepo.deleteById(customerId);
        customerWarehousesRepo.deleteAllWarehousesByCustomerId(customerId);
        log.info("deleteCustomer() completed → customerId={}", customerId);
    }

    // ─────────────────────────────────────────────
    // FILTER QUERIES
    // ─────────────────────────────────────────────

    /**
     * Builds and executes a dynamic native SQL query for the customer list/filter view.
     *
     * Filters applied when NOT "ALL" / zero:
     *  - customerId      – exact match on customer_id column
     *  - customerStatus  – exact match on customer_status column
     *  - expireNumber + expiringInSelect – contracts expiring within the next N units
     *    (e.g. expireNumber=3, expiringInSelect="MONTH" → expiring within 3 months)
     *
     * The time-unit is validated against a safe allow-list (DAY, WEEK, MONTH, QUARTER, YEAR)
     * before being interpolated into the SQL string, preventing SQL injection.
     *
     * Note: a native query is used here (rather than JPQL) because the
     * INTERVAL syntax for the expiry-window condition is MySQL-specific.
     */
    @Transactional
    public List<Customers> getCustomersList(String customerId, String customerStatus,
            int expireNumber, String expiringInSelect) {

        log.info("getCustomersList() → customerId={}, customerStatus={}, expireNumber={}, expiringInSelect={}",
                customerId, customerStatus, expireNumber, expiringInSelect);

        StringBuilder query = new StringBuilder("SELECT * FROM customers");
        List<String> conditions = new ArrayList<>();
        Map<String, Object> parameters = new HashMap<>();

        // ── Build conditions dynamically ──────────────────────────────────────

        if (customerId != null && !customerId.equalsIgnoreCase("ALL")) {
            conditions.add("customer_id = :customerId");
            parameters.put("customerId", customerId);
        }

        if (customerStatus != null && !customerStatus.equalsIgnoreCase("ALL")) {
            conditions.add("customer_status = :customerStatus");
            parameters.put("customerStatus", customerStatus);
        }

        if (expireNumber > 0 && expiringInSelect != null && !expiringInSelect.equalsIgnoreCase("ALL")) {
            String timeUnit = expiringInSelect.toUpperCase().trim();

            // Validate the time unit against an allow-list before interpolating into SQL.
            // This prevents SQL injection since INTERVAL does not support bind parameters
            // for the unit keyword in MySQL.
            if (VALID_EXPIRY_UNITS.contains(timeUnit)) {
                conditions.add("valid_upto >= NOW(6) AND valid_upto <= NOW(6) + INTERVAL :expireNumber " + timeUnit);
                parameters.put("expireNumber", expireNumber);
                log.debug("getCustomersList() → expiry filter: within {} {}", expireNumber, timeUnit);
            } else {
                log.warn("getCustomersList() → invalid time unit '{}' ignored (not in allow-list)", timeUnit);
            }
        }

        if (!conditions.isEmpty()) {
            query.append(" WHERE ").append(String.join(" AND ", conditions));
        }

        log.debug("getCustomersList() → SQL: {}", query);

        // ── Execute with bound parameters ─────────────────────────────────────
        Query nativeQuery = entityManager.createNativeQuery(query.toString(), Customers.class);

        // Bind only the parameters that were actually added to the query
        parameters.forEach(nativeQuery::setParameter);

        @SuppressWarnings("unchecked")
        List<Customers> resultList = nativeQuery.getResultList();

        log.info("getCustomersList() → returned {} customer(s)", resultList.size());
        return resultList;
    }
}