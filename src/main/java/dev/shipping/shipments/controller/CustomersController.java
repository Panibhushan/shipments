package dev.shipping.shipments.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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

	@PostMapping("/customers/createCustomer")
	public String saveCustomers(@ModelAttribute Customers customer, RedirectAttributes redirectAttributes) {
		System.out.println("hitting /customers/createCustomer - saveCustomers method: " + customer);
		String customerId = customer.getCustomerId();

		System.out.println("customer: " + customer);

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

}
