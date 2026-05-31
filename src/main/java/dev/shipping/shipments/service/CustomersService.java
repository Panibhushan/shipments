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

import org.hibernate.internal.build.AllowSysOut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;

import dev.shipping.shipments.model.CustomerWarehouses;
import dev.shipping.shipments.model.Customers;
import dev.shipping.shipments.model.Shipments;
import dev.shipping.shipments.model.Warehouses;
import dev.shipping.shipments.repo.CustomerWarehousesRepository;
import dev.shipping.shipments.repo.CustomersRepository;
import dev.shipping.shipments.repo.WarehousesRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.Query;

@Service
public class CustomersService {

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

	public List<Customers> getAllCustomers() {
		return customersRepo.findAll();
	}

	public boolean customerExists(String customerId) {
		return customersRepo.findById(customerId).isPresent();
	}

	public List<Warehouses> getActiveWarehouses() {
		return warehousesRepo.findByWarehousesByStatusActive("Active");
	}

	/**
	 * Populates all model attributes needed for the edit-customer page. Called only
	 * after confirming the customer exists.
	 */
	public void populateEditCustomerModel(String customerId, Model model) {
		Customers customer = customersRepo.findById(customerId)
				.orElseThrow(() -> new RuntimeException("Customer not found: " + customerId));

		model.addAttribute("customer", customer);
		model.addAttribute("options", List.of("Active", "Disabled"));
		model.addAttribute("selectedStatus", customer.getCustomerStatus());
		model.addAttribute("assignedWarehouses",
				customerWarehousesRepo.findAllocatedWarehousesByCustomerId(customerId));
		model.addAttribute("unassignedWarehouses",
				customerWarehousesRepo.findWarehousesNotAllocatedToCustomer(customerId));

		// Convert stored datetime string → plain date string for the calendar input
		LocalDateTime dateTime = LocalDateTime.parse(customer.getValidUpto(),
				DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss"));

		boolean isExpired = dateTime.toLocalDate().isBefore(LocalDate.now());
		String validUptoJustDate = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
		model.addAttribute("validUptoJustDate", validUptoJustDate);
		if (isExpired) {
			model.addAttribute("bgColorForValidUpto", "red");
			model.addAttribute("textColorForValidUpto", "yellow");
			model.addAttribute("isExpired", isExpired);
		}
	}

	// ─────────────────────────────────────────────
	// VALIDATION
	// ─────────────────────────────────────────────

	/**
	 * Validates fields when creating a brand-new customer. Returns a list of error
	 * messages; empty list means no errors.
	 */
	public List<String> validateNewCustomer(Customers customer) {
		List<String> errors = new ArrayList<>();
		String customerId = customer.getCustomerId();
		String customerName = customer.getCustomerName();
		String validUpto = customer.getValidUpto();

		// Customer ID checks
		if (!customerId.matches("^[a-zA-Z0-9-]+$")) {
			errors.add("Customer ID can contain only alphabets, numbers and hyphens (other symbols, spaces, tabs, next-line characters are not allowed)!!");
 		} else {
			if (customerId.length() < 3) {
				errors.add("Cannot useCustomer ID \"" + customerName
						+ "\"\nCustomer ID must be atleast 3 characters long !!");
			} else if (customerId.length() > 5) {
				errors.add("Cannot useCustomer ID \"" + customerName
						+ "\"\nCustomer ID must be maximum 5 characters only !!");
			}
		}

		// Customer Name checks
		if (customerName.trim().length() < 5) {
			errors.add("Cannot update Customer Name to \"" + customerName
					+ "\"\nCustomer Name must be atleast 5 characters long !!");
		} else if (customerName.trim().length() > 30) {
			errors.add("Cannot update Customer Name to \"" + customerName
					+ "\"\nCustomer Name must be maximum 30 characters only !!");
		}

		/*
		 * // Valid Upto date check — must be at least tomorrow LocalDate tomorrow =
		 * LocalDate.now().plusDays(1); LocalDate selectedDate =
		 * LocalDate.parse(validUpto, DateTimeFormatter.ofPattern("yyyy-MM-dd")); if
		 * (selectedDate.isBefore(tomorrow)) {
		 * errors.add("Valid Upto date cannot be older than tomorrow!"); }
		 */

		System.out.println("validateNewCustomer: " + validUpto);
		if (validUpto != null && !validUpto.isEmpty()) {
			try {
				// Parse — throws exception if date is invalid (e.g. April 31)
				LocalDate selectedDate = LocalDate.parse(validUpto,
						DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss"));

				// Valid Upto date check — must be at least tomorrow
				LocalDate tomorrow = LocalDate.now().plusDays(1);

				if (selectedDate.isBefore(tomorrow)) {
					errors.add("Valid Upto date must be at least tomorrow!");
				}

			} catch (DateTimeParseException e) {
				// Catches invalid dates like April 31, February 30 etc.
				errors.add("Invalid date! Please enter a valid date.");
				System.out.println("DateTimeParseException: " + e.getMessage());
			}
		}

		return errors;
	}

	/**
	 * Validates fields when updating an existing customer. Returns a list of error
	 * messages; empty list means no errors.
	 */
	public List<String> validateCustomerUpdate(String customerName, String validUpto) {
		List<String> errors = new ArrayList<>();

		System.out.println("CustomerService:: validateCustomerUpdate(): " + customerName + " -- " + validUpto);

		// Customer Name checks
		if (customerName.trim().length() < 5) {
			errors.add("Cannot update Customer Name to \"" + customerName
					+ "\"\nCustomer Name must be atleast 5 characters long !!");
		} else if (customerName.trim().length() > 30) {
			errors.add("Cannot update Customer Name to \"" + customerName
					+ "\"\nCustomer Name must be maximum 30 characters only !!");
		}

		// Check if the selected date is valid & if that date is of future (must be at
		// least tomorrow)
		if (validUpto != null && !validUpto.isEmpty()) {
			try {
				System.out.println(" Try inside SERVICE validUpto :: " + validUpto);
				// Parse — throws exception if date is invalid (e.g. April 31)
				LocalDate selectedDate = LocalDate.parse(validUpto, DateTimeFormatter.ofPattern("yyyy-MM-dd"));

				// Valid Upto date check — must be at least tomorrow
				LocalDate tomorrow = LocalDate.now().plusDays(1);

				if (selectedDate.isBefore(tomorrow)) {
					errors.add("Valid Upto date must be at least tomorrow!");
				}

			} catch (DateTimeParseException e) {
				System.out.println("Hitting Exception in SERVICE");
				System.out.println("Exception " + e.toString());
				// Catches invalid dates like April 31, February 30 etc.
				errors.add("You have selected an Invalid date!! Please select/enter a valid date.");
			}
		} else {
			errors.add("You have selected an Invalid date!! Please select/enter a valid date.");
		}

		return errors;
	}

	// ─────────────────────────────────────────────
	// WRITE
	// ─────────────────────────────────────────────

	/**
	 * Creates a new customer and saves warehouse assignments.
	 */
	@Transactional
	public void createCustomer(Customers customer, List<String> selectedWarehouses) {
		customersRepo.save(customer);

		if (!selectedWarehouses.isEmpty()) {
			for (String warehouseId : selectedWarehouses) {
				CustomerWarehouses cw = new CustomerWarehouses();
				cw.setCustomerId(customer.getCustomerId());
				cw.setWarehouseId(warehouseId);
				customerWarehousesRepo.save(cw);
			}
		}
	}

	/**
	 * Updates an existing customer's fields and syncs warehouse assignments: -
	 * Inserts only newly added warehouses - Deletes only removed warehouses
	 */
	@Transactional
	public void updateCustomer(String customerId, String customerName, String customerStatus, String validUpto,
			List<String> selectedWarehouses) {

		// Update customer fields
		Customers customer = customersRepo.findById(customerId)
				.orElseThrow(() -> new RuntimeException("Customer not found: " + customerId));

		System.out.println("CustomerService:: updateCustomer:: " + customerId + " -- " + customerName + " -- "
				+ customerStatus + " -- " + validUpto + " -- " + selectedWarehouses.toString());

		customer.setCustomerName(customerName);
		customer.setValidUpto(validUpto);
		customer.setCustomerStatus(customerStatus);
		customersRepo.save(customer);

		// warehouses that are currently in DB for this customer
		List<String> currentIds = customerWarehousesRepo.findAllWarehousesByCustomerId(customerId);

		// filter out the warehouses to insert only NEW warehouses based on checkbox
		// selections
		List<String> toInsert = selectedWarehouses.stream().filter(id -> !currentIds.contains(id))
				.collect(Collectors.toList());

		// filter out the warehouses to delete only the checkbox unchecked/REMOVED ones
		List<String> toDelete = currentIds.stream().filter(id -> !selectedWarehouses.contains(id))
				.collect(Collectors.toList());

		// Insert the new warehouses to customer_warehouses table
		toInsert.forEach(wId -> {
			CustomerWarehouses cw = new CustomerWarehouses();
			cw.setCustomerId(customerId);
			cw.setWarehouseId(wId);
			customerWarehousesRepo.save(cw);
		});

		// Delete the unchecked warehouses from customer_warehouses table
		if (!toDelete.isEmpty()) {
			customerWarehousesRepo.deleteByCustomerIdAndWarehouseIdIn(customerId, toDelete);
		}

	}

	/**
	 * Deletes a customer and all their warehouse assignments.
	 */
	@Transactional
	public void deleteCustomer(String customerId) {
		customersRepo.deleteById(customerId);
		customerWarehousesRepo.deleteAllWarehousesByCustomerId(customerId);
	}

	// Dynamically setting the conditions and running a custom query in service
	// instead of calling individual methods in Repo
	@Transactional
	public List<Customers> getCustomersListOld(String customerId, String customerStatus, int expireNumber,
			String expiringInSelect) {
		StringBuilder query = new StringBuilder("SELECT * FROM Customers c");

		// Dynamically build WHERE clause
		List<String> conditions = new ArrayList<>();

		if (!customerId.equals("ALL"))
			conditions.add("c.customerId = :customerId");
		if (!customerStatus.equals("ALL"))
			conditions.add("c.customerStatus = :customerStatus");
		if (!(expireNumber == 0) && !expiringInSelect.equals("ALL"))
			conditions.add("c.validUpto >= CURDATE() AND c.validUpto <= (CURDATE() + INTERVAL " + expireNumber + " "
					+ expiringInSelect + ")");

		// Append WHERE + AND automatically
		if (!conditions.isEmpty()) {
			query.append(" WHERE ").append(String.join(" AND ", conditions));
		}

		// Create query
		TypedQuery<Customers> typedQuery = (TypedQuery<Customers>) entityManager.createNativeQuery(query.toString(),
				Customers.class);

		// Bind only non-null parameters
		if (!customerId.equals("ALL"))
			typedQuery.setParameter("customerId", customerId);
		if (!customerStatus.equals("ALL"))
			typedQuery.setParameter("customerStatus", customerStatus);

		/*
		 * if (!(expireNumber == 0) && !expiringInSelect.equals("ALL")) {
		 * typedQuery.setParameter("expireNumber", expireNumber);
		 * typedQuery.setParameter("expiringInSelect", expiringInSelect); }
		 */
		List<Customers> resultList = typedQuery.getResultList();

		System.out.println("final Query: " + query.toString() + "\nresultList: " + resultList.toString());

		return resultList;
	}

	@Transactional
	public List<Customers> getCustomersList(String customerId, String customerStatus, int expireNumber,
			String expiringInSelect) {
		// 1. Base native SQL query (Targeting the physical database table)
		StringBuilder query = new StringBuilder("SELECT * FROM customers");

		List<String> conditions = new ArrayList<>();
		// Use Map interface with HashMap implementation
		Map<String, Object> parameters = new HashMap<>();

		// 2. Build conditions safely using parameterized binding (.put() for Map)
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
			List<String> validUnits = Arrays.asList("DAY", "WEEK", "MONTH", "QUARTER", "YEAR");

			if (validUnits.contains(timeUnit)) {
				// 1. The string closes properly right here with a double quote and semicolon
				conditions.add("valid_upto >= NOW(6) AND valid_upto <= NOW(6) + INTERVAL :expireNumber " + timeUnit);

				// 2. Put the numeric value into your parameters map
				parameters.put("expireNumber", expireNumber);
			} // <--- The IF statement ends cleanly right here!

		}

		// Append WHERE + AND automatically
		if (!conditions.isEmpty()) {
			query.append(" WHERE ").append(String.join(" AND ", conditions));
		}

		Query nativeQuery = entityManager.createNativeQuery(query.toString(), Customers.class);

		if (!customerId.equals("ALL"))
			nativeQuery.setParameter("customerId", customerId);
		if (!customerStatus.equals("ALL"))
			nativeQuery.setParameter("customerStatus", customerStatus);

		if (!(expireNumber == 0) && !expiringInSelect.equals("ALL")) {
			nativeQuery.setParameter("expireNumber", expireNumber);
		//	nativeQuery.setParameter("expiringInSelect", expiringInSelect);
		}

		@SuppressWarnings("unchecked")
		List<Customers> resultList = nativeQuery.getResultList();

		System.out.println("Final Query: " + query.toString() + "\nResult List Size: " + resultList.size());

		return resultList;
	}

}
