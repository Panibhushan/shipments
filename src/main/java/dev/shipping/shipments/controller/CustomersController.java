package dev.shipping.shipments.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import dev.shipping.shipments.model.Customers;
import dev.shipping.shipments.model.Shipments;
import dev.shipping.shipments.repo.CustomersRepository;
import dev.shipping.shipments.repo.ShipmentsRepository;

@Controller
public class CustomersController {

	private final CustomersRepository customersRepo;

	public CustomersController(CustomersRepository customersRepo) {
		this.customersRepo = customersRepo;
	}

	@GetMapping("/customers/")
	public String showAllCustomers(Model model) {
		model.addAttribute("customers", customersRepo.findAll());
		return "show-all-customers";
	}

	@GetMapping("/customers/goToCreateCustomerPage")
	public String addCustomersPage(Model model) {
		model.addAttribute("customer", new Customers());
		return "create-customer";
	}

	@GetMapping("/customers/editCustomer/{customerId}")
	public String editCustomersPage(@PathVariable String customerId, Model model,
			RedirectAttributes redirectAttributes) {
		model.addAttribute("customer", new Customers());
		System.out.println(
				"hitting /customers/editCustomer - editCustomersPage method:-- customerId: " + customerId);

		Optional<Customers> singleCustomer = customersRepo.findById(customerId);

		System.out.println("editCustomersPage ::: Optional<Customers> c :: " + singleCustomer.toString());

		if (!singleCustomer.isPresent()) {
			System.out.println("inside IF block");
			redirectAttributes.addFlashAttribute("msg", "Customer " + customerId + " doesn't exists !!!");
			redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
			redirectAttributes.addFlashAttribute("textColor", "#ffffff");
			return "redirect:/customers/";
		} else {
			model.addAttribute("customer", singleCustomer.get());

			// Taking valid upto date-time, converting into just date to match the calendar
			// format
			LocalDateTime dateTime = LocalDateTime.parse(singleCustomer.get().getValidUpto(),
					DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss"));

			String validUptoJustDate = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

			model.addAttribute("validUptoJustDate", validUptoJustDate);
			return "edit-customer";
		}
	}

	@PostMapping("/customers/updateCustomer")
	public String updateCustomers(@RequestParam String customerId, @RequestParam String customerName,
			@RequestParam String validUpto, RedirectAttributes redirectAttributes) {
		System.out.println("hitting /customers/updateCustomer - updateCustomers method:-- customer: " + customerId + " -- "+customerName+" -- "+validUpto);
	 

		Optional<Customers> c = customersRepo.findById(customerId);

		System.out.println("updateCustomers ::: Optional<Customers> c :: " + c.toString());

		if (!c.isPresent()) {
			System.out.println("inside IF block");
			redirectAttributes.addFlashAttribute("msg", "Customer " + customerId + " doesn't exists !!!");
			redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
			redirectAttributes.addFlashAttribute("textColor", "#ffffff");
		} else {
			System.out.println("inside ELSE block");
			String messageToDisplay = "" ;

			boolean hasError = false;

			if (customerName.trim().length() < 5) {
				hasError = true;
				messageToDisplay += "Customer Name must be atleast 5 characters long !!\n";
			} else if (customerName.trim().length() > 30) {
				hasError = true;
				messageToDisplay += "Customer Name must be maximum 30 characters only !!";
			}

			// Get today's date
			LocalDate tomorrow = LocalDate.now().plusDays(1);
System.out.println("tomorrow : "+ tomorrow);
			// Get the selected date from model (already converted by setter)
			LocalDate selectedDate = LocalDate.parse(validUpto,
					DateTimeFormatter.ofPattern("yyyy-MM-dd"));
			
			System.out.println("selectedDate : "+ selectedDate);

			// Check if selected date is before today
			if (selectedDate.isBefore(tomorrow)) {
				hasError = true;
				messageToDisplay += "Valid Upto date cannot be older than tomorrow!";
			}

			if (hasError) {
				redirectAttributes.addFlashAttribute("msg", messageToDisplay);
				redirectAttributes.addFlashAttribute("customerIdFromController", customerId);
				redirectAttributes.addFlashAttribute("customerNameFromController", customerName);
				redirectAttributes.addFlashAttribute("validUptoFromController", validUpto);
				redirectAttributes.addFlashAttribute("bgColor", "#f03a5b");
				redirectAttributes.addFlashAttribute("textColor", "#f5f0f1");
			} else {
				

				Customers customer = customersRepo.findById(customerId)
						.orElseThrow(() -> new RuntimeException("Customer not found"));
				
				customer.setCustomerName(customerName);
				customer.setValidUpto(validUpto);
				
				
				customersRepo.save(customer);
				redirectAttributes.addFlashAttribute("msg", "Updated Customer: " + customerId + " !!!");
				redirectAttributes.addFlashAttribute("bgColor", "#d1fae5;");
				redirectAttributes.addFlashAttribute("textColor", "#45484d");
			}
		}

		return "redirect:/customers/editCustomer/"+customerId;
	}

	@PostMapping("/customers/createCustomer")
	public String saveCustomers(@ModelAttribute Customers customer, RedirectAttributes redirectAttributes) {
		System.out.println("hitting /customers/createCustomer - saveCustomers method: " + customer);
		String customerId = customer.getCustomerId();

		System.out.println("customer: " + customer.toString());

		System.out.println(
				"customersRepo.findById(customerId).isPresent(): " + customersRepo.findById(customerId).isPresent());

		Optional<Customers> c = customersRepo.findById(customerId);
		if (c.isPresent()) {
			System.out.println("inside IF block");
			redirectAttributes.addFlashAttribute("msg", "Customer " + customerId + " already exists !!!");
			redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
			redirectAttributes.addFlashAttribute("textColor", "#ffffff");
		} else {
			System.out.println("inside ELSE block");
			String messageToDisplay = "", customerName = customer.getCustomerName();

			boolean doesCustomerIdHasWhitespace = customerId.matches(".*\\s.*"), hasError = false;

			if (doesCustomerIdHasWhitespace) {
				hasError = true;
				messageToDisplay = "Customer ID cannot contain any whitespaces (spaces, tabs, next-line characters)!!\n";
			} else {

				if (customerId.length() < 3) {
					hasError = true;
					messageToDisplay = "Customer ID must be atleast 3 characters long !!\n";
				} else if (customerId.length() > 5) {
					hasError = true;
					messageToDisplay += "Customer ID must be maximum 5 characters only !!\n";
				}
			}

			if (customerName.trim().length() < 5) {
				hasError = true;
				messageToDisplay += "Customer Name must be atleast 5 characters long !!\n";
			} else if (customerName.trim().length() > 30) {
				hasError = true;
				messageToDisplay += "Customer Name must be maximum 30 characters only !!";
			}

			if (hasError) {
				redirectAttributes.addFlashAttribute("msg", messageToDisplay);
				redirectAttributes.addFlashAttribute("customerIdFromController", customerId);
				redirectAttributes.addFlashAttribute("customerNameFromController", customerName);
				redirectAttributes.addFlashAttribute("bgColor", "#f03a5b");
				redirectAttributes.addFlashAttribute("textColor", "#f5f0f1");
			} else {

				customersRepo.save(customer);
				redirectAttributes.addFlashAttribute("msg", "Created Customer: " + customerId + " !!!");
				redirectAttributes.addFlashAttribute("bgColor", "#d1fae5;");
				redirectAttributes.addFlashAttribute("textColor", "#45484d");
			}
		}

		return "redirect:/customers/goToCreateCustomerPage";
	}
	
	@PostMapping("/customers/deleteCustomer/{customerId}")
	public String deleteCustomer(@PathVariable String customerId, RedirectAttributes redirectAttributes) {
		System.out.println("inside /customers/deleteCustomer/{customerId} :: deleteCustomer method :: customerId: "+customerId);
		Optional<Customers> c = customersRepo.findById(customerId);
		if (!c.isPresent()) {
			System.out.println("inside IF block");
			redirectAttributes.addFlashAttribute("msg", "Customer " + customerId + " doesnt exists !!!");
			redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
			redirectAttributes.addFlashAttribute("textColor", "#ffffff");
		} else {
			System.out.println("customersRepo.deleteById("+customerId+")");
			customersRepo.deleteById(customerId);
			redirectAttributes.addFlashAttribute("msg", "Customer " + customerId + " is Deleted!!!");
			redirectAttributes.addFlashAttribute("bgColor", "#d1fae5;");
			redirectAttributes.addFlashAttribute("textColor", "#45484d");
		}
		return "redirect:/customers/";
	}

}
