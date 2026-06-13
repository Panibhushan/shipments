package dev.shipping.shipments.controller;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import dev.shipping.shipments.model.Audits;
import dev.shipping.shipments.model.Customers;
import dev.shipping.shipments.service.AuditsService;
import dev.shipping.shipments.service.CustomersService;

/**
 * Handles all HTTP requests related to customers.
 *
 * Responsibilities: - Parse and validate incoming HTTP parameters - Delegate
 * all business logic to CustomersService - Populate the view model or set flash
 * attributes for redirects
 *
 * This controller intentionally contains NO business logic. Validation,
 * persistence, and domain rules all live in CustomersService.
 */
@Controller
public class CustomersController {

	private static final Logger log = LoggerFactory.getLogger(CustomersController.class);

	/**
	 * Status options shown in the customer status dropdown. Defined once here to
	 * avoid repeating the literal list in every handler.
	 */
	private static final List<String> STATUS_OPTIONS = Arrays.asList("Active", "Disabled");

	/**
	 * Time-unit options shown in the "expiring within" filter dropdown.
	 */
	private static final List<String> EXPIRY_UNIT_OPTIONS = Arrays.asList("DAY", "WEEK", "MONTH", "QUARTER", "YEAR");

	private final CustomersService customersService;
	private final AuditsService auditsService;

	public CustomersController(CustomersService customersService, AuditsService auditsService) {
		this.customersService = customersService;
		this.auditsService = auditsService;
	}

	// ─────────────────────────────────────────────
	// LIST / FILTER
	// ─────────────────────────────────────────────

	/**
	 * Renders the customer list page with no filter applied. Loads all customers
	 * for both the filter dropdown and the data table.
	 */
	@GetMapping("/customers/")
	public String showAllCustomers(Model model) {
		log.info("GET /customers/ → loading all customers (no filter)");
		List<Customers> allCustomers = customersService.getAllCustomers();
		model.addAttribute("customers", allCustomers); // for the filter dropdown
		model.addAttribute("customersList", allCustomers); // for the data table
		model.addAttribute("activePage", "allCustomers");
		model.addAttribute("statusOptions", STATUS_OPTIONS);
		model.addAttribute("expiringCriteriaOptions", EXPIRY_UNIT_OPTIONS);
		model.addAttribute("filterApplied", false);
		return "show-all-customers";
	}

	/**
	 * Filters the customer list using the submitted form values.
	 *
	 * All parameters are optional; "ALL" is the sentinel meaning "no filter on this
	 * field". expireNumber=0 means the expiry-window filter is not active.
	 *
	 * The expiry-window filter (expireNumber + expiringInSelect) finds customers
	 * whose contract expires within the next N time units, e.g. within 3 months.
	 */
	@GetMapping("/customers/showCustomersByFilter")
	public String showCustomersByFilter(@RequestParam(required = false, defaultValue = "ALL") String customerId,
			@RequestParam(required = false, defaultValue = "ALL") String customerStatus,
			@RequestParam(value = "expireNumber", required = false, defaultValue = "0") Integer expireNumber,
			@RequestParam(required = false, defaultValue = "ALL") String expiringInSelect, Model model) {

		log.info(
				"GET /customers/showCustomersByFilter → customerId={}, customerStatus={}, expireNumber={}, expiringInSelect={}",
				customerId, customerStatus, expireNumber, expiringInSelect);

		List<Customers> customersList = customersService.getCustomersList(customerId, customerStatus, expireNumber,
				expiringInSelect);

		log.info("Customer filter returned {} customer(s)", customersList.size());

		model.addAttribute("customers", customersService.getAllCustomers()); // filter dropdown always shows all
		model.addAttribute("customersList", customersList);
		model.addAttribute("selectedCustomer", customerId);
		model.addAttribute("selectedCustomerStatus", customerStatus);
		model.addAttribute("filterApplied", true);
		model.addAttribute("statusOptions", STATUS_OPTIONS);
		model.addAttribute("expiringCriteriaOptions", EXPIRY_UNIT_OPTIONS);

		// Only re-populate the expiry fields if a real expiry filter was actually
		// applied
		if (expireNumber != 0) {
			model.addAttribute("enteredExpireNumber", expireNumber);
			model.addAttribute("selectedExpiringCriteria", expiringInSelect);
		} else {
			// Clear the expiry fields so the form resets cleanly
			model.addAttribute("enteredExpireNumber", "");
			model.addAttribute("selectedExpiringCriteria", "");
		}

		return "show-all-customers";
	}

	// ─────────────────────────────────────────────
	// CREATE CUSTOMER
	// ─────────────────────────────────────────────

	/**
	 * Backward-compatibility redirect for any page still using the old URL.
	 */
	@GetMapping("/customers/goToCreateCustomerPage")
	public String goToCreateCustomerPage() {
		log.info("GET /customers/goToCreateCustomerPage → redirecting to /customers/createCustomerPage");
		return "redirect:/customers/createCustomerPage";
	}

	/**
	 * Renders the create-customer form, pre-loading the list of active warehouses.
	 */
	@GetMapping("/customers/createCustomerPage")
	public String addCustomersPage(Model model) {
		log.info("GET /customers/createCustomerPage → rendering create customer form");
		model.addAttribute("customer", new Customers());
		model.addAttribute("activePage", "createCustomer");
		model.addAttribute("warehouses", customersService.getActiveWarehouses());
		return "create-customer";
	}

	/**
	 * Handles the create-customer form POST.
	 *
	 * Flow: 1. Reject immediately if the customer ID already exists. 2. Run
	 * field-level validation via the service. 3. On errors → redirect back to the
	 * form with error flash attributes. 4. On success → create the customer and
	 * redirect with a success message.
	 */
	@PostMapping("/customers/createCustomer")
	public String saveCustomer(@ModelAttribute Customers customer,
			@RequestParam(value = "selectedWarehouses", required = false, defaultValue = "") List<String> selectedWarehouses,
			RedirectAttributes redirectAttributes) {

		String customerId = customer.getCustomerId();
		log.info("POST /customers/createCustomer → customerId={}, warehouseCount={}", customerId,
				selectedWarehouses.size());

		// ── 1. Duplicate check ────────────────────────────────────────────────
		if (customersService.customerExists(customerId)) {
			log.warn("saveCustomer() → customer already exists: customerId={}", customerId);
			redirectAttributes.addFlashAttribute("msg", "Customer " + customerId + " already exists!");
			redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
			redirectAttributes.addFlashAttribute("textColor", "#ffffff");
			return "redirect:/customers/createCustomerPage";
		}

		// ── 2. Validation ─────────────────────────────────────────────────────
		List<String> errors = customersService.validateNewCustomer(customer);

		if (!errors.isEmpty()) {
			log.warn("saveCustomer() → validation failed for customerId={}: {}", customerId, errors);
			redirectAttributes.addFlashAttribute("msg", String.join("\n", errors));
			// Re-populate the form fields so the user doesn't have to retype them
			redirectAttributes.addFlashAttribute("customerIdFromController", customerId);
			redirectAttributes.addFlashAttribute("customerNameFromController", customer.getCustomerName());
			redirectAttributes.addFlashAttribute("bgColor", "#f03a5b");
			redirectAttributes.addFlashAttribute("textColor", "#f5f0f1");
		} else {
			// ── 3. Persist ────────────────────────────────────────────────────
			customersService.createCustomer(customer, selectedWarehouses);
			log.info("saveCustomer() → customer created: customerId={}", customerId);
			redirectAttributes.addFlashAttribute("msg",
					"Created Customer: " + customerId + "!"
							+ "&nbsp;&nbsp;&nbsp;&nbsp;<a href='/customers/showCustomerDetails/" + customerId
							+ "'>View " + customerId + "</a>");
			redirectAttributes.addFlashAttribute("bgColor", "#d1fae5");
			redirectAttributes.addFlashAttribute("textColor", "#45484d");
		}

		return "redirect:/customers/createCustomerPage";
	}

	// ─────────────────────────────────────────────
	// VIEW / EDIT CUSTOMER
	// ─────────────────────────────────────────────

	/**
	 * Redirects /showCustomerDetails/{id} to the canonical view/edit URL. Keeps old
	 * links working without duplicating handler logic.
	 */
	@GetMapping("/customers/showCustomerDetails/{customerId}")
	public String showCustomerDetails(@PathVariable String customerId) {
		log.info("GET /customers/showCustomerDetails/{} → redirecting to viewOrEditCustomer", customerId);
		return "redirect:/customers/viewOrEditCustomer/" + customerId;
	}

	/**
	 * Backward-compatibility redirect: any page still using /editCustomer/{id} is
	 * transparently forwarded to the canonical /viewOrEditCustomer/{id} URL.
	 */
	@GetMapping("/customers/editCustomer/{customerId}")
	public String editCustomerRedirect(@PathVariable String customerId) {
		log.info("GET /customers/editCustomer/{} → redirecting to viewOrEditCustomer", customerId);
		return "redirect:/customers/viewOrEditCustomer/" + customerId;
	}

	/**
	 * Renders the view/edit page for a specific customer. Redirects to the customer
	 * list with an error message if the ID does not exist.
	 */
	@GetMapping("/customers/viewOrEditCustomer/{customerId}")
	public String viewOrEditCustomer(@PathVariable String customerId, Model model,
			RedirectAttributes redirectAttributes) {

		log.info("GET /customers/viewOrEditCustomer/{}", customerId);

		if (!customersService.customerExists(customerId)) {
			log.warn("viewOrEditCustomer() → customer not found: customerId={}", customerId);
			redirectAttributes.addFlashAttribute("msg", "Customer " + customerId + " doesn't exist!");
			redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
			redirectAttributes.addFlashAttribute("textColor", "#ffffff");
			return "redirect:/customers/";
		}

		// Bind an empty Customers object so Thymeleaf form bindings don't NPE
		model.addAttribute("customer", new Customers());
		customersService.populateEditCustomerModel(customerId, model);
		return "edit-customer";
	}

	// ─────────────────────────────────────────────
	// UPDATE CUSTOMER
	// ─────────────────────────────────────────────

	/**
	 * Handles the update-customer form POST.
	 *
	 * Flow: 1. Reject immediately if the customer ID no longer exists (defensive
	 * check). 2. Run field-level validation via the service. 3. On errors →
	 * redirect back to the edit page with error flash attributes. 4. On success →
	 * update the customer and redirect with a success message. 5. Catch any
	 * unexpected exception and surface it as a user-friendly error.
	 */
	@PostMapping("/customers/updateCustomer")
	public String updateCustomer(@RequestParam String customerId, @RequestParam String customerName,
			@RequestParam String customerStatus, @RequestParam String validUpto, @RequestParam String customerEmail,
			@RequestParam(value = "selectedWarehouses", required = false, defaultValue = "") List<String> selectedWarehouses,
			RedirectAttributes redirectAttributes) {

		log.info(
				"POST /customers/updateCustomer → customerId={}, customerName={}, status={}, validUpto={}, customerEmail={}, warehouseCount={}",
				customerId, customerName, customerStatus, validUpto, customerEmail, selectedWarehouses.size());

		String bgColor = "#f8d7da", textColor = "#721c24", msg = "", resultMessage="";

		try {

			// ── 1. Existence check ────────────────────────────────────────────────
			if (!customersService.customerExists(customerId)) {
				log.warn("updateCustomer() → customer not found: customerId={}", customerId);
				msg = "Customer " + customerId + " doesn't exist!";
				// error colors are already set while declaring & initializing the variables

				return "redirect:/customers/viewOrEditCustomer/" + customerId;
			}

			// ── 2. Validation ─────────────────────────────────────────────────
			List<String> errors = customersService.validateCustomerUpdate(customerName, validUpto);

			if (!errors.isEmpty()) {
				log.warn("updateCustomer() → validation failed for customerId={}: {}", customerId, errors);
				msg = String.join("\n", errors);
				// error colors are already set while declaring & initializing the variables
			} else {
				// ── 3. Persist ────────────────────────────────────────────────
				resultMessage = customersService.updateCustomer(customerId, customerName, customerStatus,
						validUpto, customerEmail, selectedWarehouses);
				
				log.info("updateCustomer() → customer update customerId={} resultMessage={}, isSuccess={}", customerId, resultMessage, resultMessage.equals("SUCCESS"));

				if (resultMessage.equals("SUCCESS")) { // if result is not success then only error message
					
					log.info("updateCustomer() → setting message to {} :: {}", resultMessage, "Updated Customer: " + customerId  );
					msg = "Updated Customer: " + customerId + " !!";
					bgColor = "#d1fae5";
					textColor = "#45484d";
				}
				else {
					log.info("updateCustomer() → setting message to errormessage ={} ", resultMessage );
					msg = resultMessage;					
				}
				
			}

		} catch (Exception e) {
			// if any unexpected errors (e.g. DB constraint violations) as a user-friendly
			// message
			log.error("updateCustomer() → unexpected error for customerId={}: {}", customerId, e.getMessage(), e);
			msg = "An unexpected error occurred while updating the customer. Please try again.";
			// error colors are already set while declaring & initializing the variables
		}

		redirectAttributes.addFlashAttribute("msg", msg);
		redirectAttributes.addFlashAttribute("bgColor", bgColor);
		redirectAttributes.addFlashAttribute("textColor", textColor);

		return "redirect:/customers/viewOrEditCustomer/" + customerId;
	}

	// ─────────────────────────────────────────────
	// DELETE CUSTOMER
	// ─────────────────────────────────────────────

	/**
	 * Deletes a customer and all their warehouse assignments. Redirects to the
	 * customer list with a success or error message.
	 */
	@PostMapping("/customers/deleteCustomer/{customerId}")
	public String deleteCustomer(@PathVariable String customerId, RedirectAttributes redirectAttributes) {

		log.info("POST /customers/deleteCustomer/{}", customerId);

		String msg, bgColor = "#d95f6c", textColor = "#ffffff";

		if (!customersService.customerExists(customerId)) {
			log.warn("deleteCustomer() → customer not found: customerId={}", customerId);
			msg = "Customer " + customerId + " doesn't exist!";
		} else {
			String deletionResult = customersService.deleteCustomer(customerId);
			log.info("deleteCustomer() → customerId={} → result={} ", customerId, deletionResult);

			if (deletionResult.contains("SUCCESS")) {
				msg = "Customer " + customerId + " has been deleted.";
				bgColor = "#d1fae5";
				textColor = "#45484d";
			} else {
				msg = deletionResult;
				redirectAttributes.addFlashAttribute("msg", msg);
				redirectAttributes.addFlashAttribute("bgColor", bgColor);
				redirectAttributes.addFlashAttribute("textColor", textColor);
				return "redirect:/customers/viewOrEditCustomer/" + customerId;
			}
		}

		redirectAttributes.addFlashAttribute("msg", msg);
		redirectAttributes.addFlashAttribute("bgColor", bgColor);
		redirectAttributes.addFlashAttribute("textColor", textColor);
		return "redirect:/customers/";
	}
	
	
	@GetMapping("/customers/viewCustomerAudit/{customerId}")
	public String viewCustomerAudit(@PathVariable String customerId, Model model) {

		List<Audits> auditDetails = auditsService.getAuditDetailsList(customerId); 
		
		log.info("/customers/viewCustomerAudit/customerId={}  → audit={}", customerId, auditDetails);

		if (auditDetails.isEmpty() || auditDetails == null)
			model.addAttribute("audit", "");
		else {
			model.addAttribute("auditDetails", auditDetails);
			model.addAttribute("auditForEntity", customerId);
		}

		return "show-audit";
	}
	
	
}