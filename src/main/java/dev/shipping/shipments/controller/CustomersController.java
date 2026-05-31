package dev.shipping.shipments.controller;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import dev.shipping.shipments.model.Customers;
import dev.shipping.shipments.model.Shipments;
import dev.shipping.shipments.service.CustomersService;

@Controller
public class CustomersController {

	private final CustomersService customersService;

	public CustomersController(CustomersService customersService) {
		this.customersService = customersService;
	}

	@GetMapping("/customers/")
	public String showAllCustomers(Model model) {
		model.addAttribute("customers", customersService.getAllCustomers()); // for filter dropdown
		model.addAttribute("customersList", customersService.getAllCustomers()); // for data to display dropdown
		model.addAttribute("activePage", "allCustomers"); // ← this is show which dropdown is active in the navbar
		model.addAttribute("statusOptions", Arrays.asList("Active", "Disabled"));
		model.addAttribute("expiringCriteriaOptions", Arrays.asList("DAY", "WEEK", "MONTH", "QUARTER", "YEAR"));
		model.addAttribute("filterApplied", false);
		return "show-all-customers";
	}

	// Adding this addittional method, just incase I missed to update the URL from
	// goToCreateCustomerPage to createCustomerPage in any pages, this will route
	// correctly instead of giving error
	@GetMapping("/customers/goToCreateCustomerPage")
	public String goToCreateCustomerPage(Model model) {
		return "redirect:/customers/createCustomerPage";
	}

	@GetMapping("/customers/createCustomerPage")
	public String addCustomersPage(Model model) {
		model.addAttribute("customer", new Customers());
		model.addAttribute("activePage", "createCustomer"); // ← this is show which dropdown is active in the navbar
		model.addAttribute("warehouses", customersService.getActiveWarehouses());
		return "create-customer";
	}

	@GetMapping("/customers/showCustomersByFilter")
	public String showCustomersByFilter(@RequestParam(required = false) String customerId,
			@RequestParam(required = false) String customerStatus,
			@RequestParam(value = "expireNumber", required = false, defaultValue = "0") Integer expireNumber,
			@RequestParam(required = false) String expiringInSelect, Model model) {

		System.out.println(
				"/customers/showCustomersByFilter::Get()/{customerId}/{customerStatus}/{expireNumber}/{expiringInSelect}: "
						+ customerId + " / " + customerStatus + " / " + expireNumber + " / " + expiringInSelect);

		/*
		 * if (Integer.toString(expireNumber).trim() == "") { expireNumber = 0; }
		 */

		List<Customers> customersList = customersService.getCustomersList(customerId, customerStatus, expireNumber,
				expiringInSelect);

		model.addAttribute("customers", customersService.getAllCustomers());
		model.addAttribute("customersList", customersList);
		model.addAttribute("selectedCustomer", customerId);		
		model.addAttribute("selectedCustomerStatus", customerStatus);
		
		if(expireNumber!=0 ) {
		model.addAttribute("selectedExpiringCriteria", expiringInSelect);
		model.addAttribute("enteredExpireNumber", expireNumber);
		}else {
			model.addAttribute("enteredExpireNumber", "");
			model.addAttribute("selectedExpiringCriteria", "");
		}
		
		model.addAttribute("filterApplied", true);
		model.addAttribute("statusOptions", Arrays.asList("Active", "Disabled"));
		model.addAttribute("expiringCriteriaOptions", Arrays.asList("DAY", "WEEK", "MONTH", "QUARTER", "YEAR"));

		return "show-all-customers";
	}

	@GetMapping("/customers/showCustomerDetails/{customerId}")
	public String showCustomerDetails(@PathVariable String customerId) {
		return "redirect:/customers/viewOrEditCustomer/" + customerId;
	}
	
	// Adding this method just incase if I missed to change the url from editCustomer to viewOrEditCustomer in any pages, this will reroute correctly
	@GetMapping("/customers/editCustomer/{customerId}")
	public String viewOrEditCustomerPage(@PathVariable String customerId) {
		return "redirect:/customers/viewOrEditCustomer/" + customerId;
	}

	@GetMapping("/customers/viewOrEditCustomer/{customerId}")
	public String viewOrEditCustomers(@PathVariable String customerId, Model model,
			RedirectAttributes redirectAttributes) {

		model.addAttribute("customer", new Customers());

		if (!customersService.customerExists(customerId)) {
			redirectAttributes.addFlashAttribute("msg", "Customer " + customerId + " doesn't exists !!!");
			redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
			redirectAttributes.addFlashAttribute("textColor", "#ffffff");
			return "redirect:/customers/";
		}

		customersService.populateEditCustomerModel(customerId, model);
		return "edit-customer";
	}

	@PostMapping("/customers/updateCustomer")
	public String updateCustomers(@RequestParam String customerId, @RequestParam String customerName,
			@RequestParam String customerStatus, @RequestParam String validUpto,
			@RequestParam(value = "selectedWarehouses", required = false, defaultValue = "") List<String> selectedWarehouses,
			RedirectAttributes redirectAttributes) {

		System.out.println("CustomerController:: updateCustomer:: " + customerId + " -- " + customerName + " -- "
				+ customerStatus + " -- " + validUpto + " -- " + selectedWarehouses.toString());

		if (!customersService.customerExists(customerId)) {
			redirectAttributes.addFlashAttribute("msg", "Customer " + customerId + " doesn't exists !!!");
			redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
			redirectAttributes.addFlashAttribute("textColor", "#ffffff");
			return "redirect:/customers/viewOrEditCustomer/" + customerId;
		}

		List<String> errors = null;

		try {
			System.out.println("Hitting Try in Controller");
			errors = customersService.validateCustomerUpdate(customerName, validUpto);

			System.out.println("Hitting Try in Controller:: errors:  " + errors.toString());

			if (!errors.isEmpty()) {
				redirectAttributes.addFlashAttribute("msg", String.join("\n", errors));
				/*
				 * redirectAttributes.addFlashAttribute("customerIdFromController", customerId);
				 * redirectAttributes.addFlashAttribute("customerNameFromController",
				 * customerName);
				 * redirectAttributes.addFlashAttribute("validUptoFromController", validUpto);
				 */
				redirectAttributes.addFlashAttribute("bgColor", "#f03a5b");
				redirectAttributes.addFlashAttribute("textColor", "#f5f0f1");
			} else {

				System.out.println("CustomerController:: updateCustomer:: inside Else::  " + customerId + " -- "
						+ customerName + " -- " + customerStatus + " -- " + validUpto + " -- "
						+ selectedWarehouses.toString());

				customersService.updateCustomer(customerId, customerName, customerStatus, validUpto,
						selectedWarehouses);
				redirectAttributes.addFlashAttribute("msg", "Updated Customer: " + customerId + " !!!");
				redirectAttributes.addFlashAttribute("bgColor", "#d1fae5;");
				redirectAttributes.addFlashAttribute("textColor", "#45484d");
			}
		} catch (Exception e) {
			System.out.println("CustomerController:: updateCustomer:: Hitting Exception in Controller:: " + customerId
					+ " -- " + customerName + " -- " + customerStatus + " -- " + validUpto + " -- "
					+ selectedWarehouses.toString());
			System.out.println("Exception " + e.toString());
			redirectAttributes.addFlashAttribute("msg",
					"You have selected an Invalid date!! Please select/enter a valid date.");
			/*
			 * redirectAttributes.addFlashAttribute("customerIdFromController", customerId);
			 * redirectAttributes.addFlashAttribute("customerNameFromController",
			 * customerName);
			 * redirectAttributes.addFlashAttribute("validUptoFromController", validUpto);
			 */
			redirectAttributes.addFlashAttribute("bgColor", "#f03a5b");
			redirectAttributes.addFlashAttribute("textColor", "#f5f0f1");
		}
		return "redirect:/customers/viewOrEditCustomer/" + customerId;
	}

	@PostMapping("/customers/createCustomer")
	public String saveCustomers(@ModelAttribute Customers customer,
			@RequestParam(value = "selectedWarehouses", required = false, defaultValue = "") List<String> selectedWarehouses,
			RedirectAttributes redirectAttributes) {

		String customerId = customer.getCustomerId();

		if (customersService.customerExists(customerId)) {
			redirectAttributes.addFlashAttribute("msg", "Customer " + customerId + " already exists !!!");
			redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
			redirectAttributes.addFlashAttribute("textColor", "#ffffff");
			return "redirect:/customers/createCustomerPage";
		}

		List<String> errors = customersService.validateNewCustomer(customer);

		if (!errors.isEmpty()) {
			redirectAttributes.addFlashAttribute("msg", String.join("\n", errors));
			redirectAttributes.addFlashAttribute("customerIdFromController", customerId);
			redirectAttributes.addFlashAttribute("customerNameFromController", customer.getCustomerName());
			redirectAttributes.addFlashAttribute("bgColor", "#f03a5b");
			redirectAttributes.addFlashAttribute("textColor", "#f5f0f1");
		} else {
			customersService.createCustomer(customer, selectedWarehouses);
			redirectAttributes.addFlashAttribute("msg",
					"Created Customer: " + customerId + " !!!"
							+ "&nbsp;&nbsp;&nbsp;&nbsp;<a href='/customers/showCustomerDetails/" + customerId
							+ "'>View " + customerId + "</a>");
			redirectAttributes.addFlashAttribute("bgColor", "#d1fae5;");
			redirectAttributes.addFlashAttribute("textColor", "#45484d");
		}

		return "redirect:/customers/createCustomerPage";
	}

	@PostMapping("/customers/deleteCustomer/{customerId}")
	public String deleteCustomer(@PathVariable String customerId, RedirectAttributes redirectAttributes) {

		if (!customersService.customerExists(customerId)) {
			redirectAttributes.addFlashAttribute("msg", "Customer " + customerId + " doesnt exists !!!");
			redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
			redirectAttributes.addFlashAttribute("textColor", "#ffffff");
		} else {
			customersService.deleteCustomer(customerId);
			redirectAttributes.addFlashAttribute("msg", "Customer " + customerId + " is Deleted!!!");
			redirectAttributes.addFlashAttribute("bgColor", "#d1fae5;");
			redirectAttributes.addFlashAttribute("textColor", "#45484d");
		}

		return "redirect:/customers/";
	}

}