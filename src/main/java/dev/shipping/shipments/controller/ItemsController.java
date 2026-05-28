package dev.shipping.shipments.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import dev.shipping.shipments.model.Items;
import dev.shipping.shipments.model.Warehouses;
import dev.shipping.shipments.service.CustomersService;
import dev.shipping.shipments.service.ItemsService;
import dev.shipping.shipments.service.ShipmentsService;
import dev.shipping.shipments.service.WarehousesService;
import dev.shipping.shipments.utils.MyResourceUtils;

@Controller
public class ItemsController {

	private final ItemsService itemsService;
	private final ShipmentsService shipmentsService;
	private final CustomersService customersService;

	List<String> itemUomsList = Arrays.asList("EACH", "MTR", "CMTR", "PAIR");

	public ItemsController(ItemsService itemsService, ShipmentsService shipmentsService,
			CustomersService customersService) {
		this.itemsService = itemsService;
		this.shipmentsService = shipmentsService;
		this.customersService = customersService;
	}

	@GetMapping("/items/")
	public String showAllItems(Model model) {
		model.addAttribute("items", itemsService.getAllItems());
		model.addAttribute("selectedCustomer", "ALL");
		model.addAttribute("customers", customersService.getAllCustomers());
		model.addAttribute("activePage", "allItems"); // ← this is show which dropdown is active in the navbar
		return "show-all-items";
	}

	@PostMapping("/items/showItemsByCustomerAndItem/{customerId}/{itemId}")
	public String showItemsByCustomer(@PathVariable String customerId, @PathVariable String itemId, Model model) {

		System.out.println("/items/showItemsByCustomerAndItem/{customerId}/{itemId}: " + customerId + " / " + itemId);
		if (customerId.equals("ALL")) {
			return "redirect:/items/";
		} else {
			model.addAttribute("items", itemsService.getItemsList(customerId, itemId));
			model.addAttribute("selectedCustomer", customerId);
			model.addAttribute("selectedItemId", itemId.equals("ALL") ? "" : itemId);
			model.addAttribute("customers", customersService.getAllCustomers());
			return "show-all-items";
		}
	}

	@GetMapping("/items/goToCreateItemPage")
	public String addItemsPage(Model model) {
		model.addAttribute("items", new Items());
		model.addAttribute("customers", itemsService.getActiveAndValidCustomers());
		model.addAttribute("activePage", "createItem"); // ← this is show which dropdown is active in the navbar
		model.addAttribute("itemUomsList", itemUomsList);
		return "create-item";
	}

	@PostMapping("/items/createItem")
	public String saveItems(@ModelAttribute Items item, RedirectAttributes redirectAttributes) {

		String itemId = item.getItemId();
		String customerId = item.getCustomerId();
		String itemUom = item.getItemUom();
		String itemCustomerUomId1 = item.getItemCustomerUomId();
		String itemCustomerUomId = itemId + "_" + customerId + "_" + itemUom;

		System.out
				.println("itemCustomerUomId: " + itemCustomerUomId + " --- itemCustomerUomId1: " + itemCustomerUomId1);

		if (itemsService.itemExists(itemCustomerUomId)) {
			redirectAttributes.addFlashAttribute("msg", "Item " + itemId + " already exists for this customer: "
					+ customerId + " & uom: " + itemUom + " !!!");
			redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
			redirectAttributes.addFlashAttribute("textColor", "#ffffff");
			return "redirect:/items/goToCreateItemPage";
		}

		List<String> errors = itemsService.validateNewItem(item, itemUomsList);

		if (!errors.isEmpty()) {
			redirectAttributes.addFlashAttribute("msg", String.join("\n", errors));
			redirectAttributes.addFlashAttribute("itemIdFromController", itemId);
			redirectAttributes.addFlashAttribute("itemDescriptionFromController", item.getItemDescription());
			redirectAttributes.addFlashAttribute("itemUomFromController", item.getItemUom());
			redirectAttributes.addFlashAttribute("bgColor", "#f03a5b");
			redirectAttributes.addFlashAttribute("textColor", "#f5f0f1");
		} else {
			itemsService.createItem(item);
			redirectAttributes.addFlashAttribute("msg", "Created Item: " + itemId + " !!!");
			redirectAttributes.addFlashAttribute("itemCustomerUomId", itemCustomerUomId);
			redirectAttributes.addFlashAttribute("bgColor", "#d1fae5;");
			redirectAttributes.addFlashAttribute("textColor", "#45484d");
		}

		redirectAttributes.addFlashAttribute("itemUomsList", itemUomsList);

		return "redirect:/items/goToCreateItemPage";
	}

	@GetMapping("/items/viewOrEditItem/{itemCustomerUomId}")
	public String viewOrEditItemsPage(@PathVariable String itemCustomerUomId, Model model,
			RedirectAttributes redirectAttributes) {

		model.addAttribute("item", new Items());

		String text = "";

		String[] parts = itemCustomerUomId.split("_");

		String itemId = parts[0];
		String customerId = parts[1];
		String itemUom = parts[2];

		Optional<Items> item = itemsService.getItemById(itemCustomerUomId);

		if (item.isEmpty()) {
			redirectAttributes.addFlashAttribute("msg", "Item " + itemId + " doesnt exists for this customer: "
					+ customerId + " & uom: " + itemUom + " !!!");
			redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
			redirectAttributes.addFlashAttribute("textColor", "#ffffff");
			return "redirect:/items/";
		}

		/*
		 * System.out.println("\n------------\nCreated: " + item.get().getCreatedAt() +
		 * "\nModified: " + item.get().getModifiedAt() +
		 * "\n------------\nformattedCreatedAt: " +
		 * MyResourceUtils.getFormattedDateTime(item.get().getCreatedAt()) +
		 * "\nformattedModifiedAt: " +
		 * MyResourceUtils.getFormattedDateTime(item.get().getModifiedAt()));
		 */

		itemsService.populateEditItemModel(itemCustomerUomId, model);

		model.addAttribute("itemUomsList", itemUomsList);

		return "edit-item";
	}

	@GetMapping("/items/showItemDetails/{itemCustomerUomId}")
	public String showItemDetails(@PathVariable String itemCustomerUomId) {
		return "redirect:/items/viewOrEditItem/" + itemCustomerUomId;
	}

	@PostMapping("/items/deleteItem/{itemCustomerUomId}")
	public String deleteWarehouse(@PathVariable String itemCustomerUomId, RedirectAttributes redirectAttributes) {

		String[] parts = itemCustomerUomId.split("_");

		String itemId = parts[0];
		String customerId = parts[1];
		String itemUom = parts[2];

		if (!itemsService.itemExists(itemCustomerUomId)) {
			redirectAttributes.addFlashAttribute("msg", "Item " + itemId + " doesnt exists for this customer: "
					+ customerId + " & uom: " + itemUom + " !!!");
			redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
			redirectAttributes.addFlashAttribute("textColor", "#ffffff");
		} else {
			itemsService.deleteItem(itemCustomerUomId);
			redirectAttributes.addFlashAttribute("msg",
					"Item " + itemId + " Deleted for the customer: " + customerId + " & uom: " + itemUom + " !!!");
			redirectAttributes.addFlashAttribute("bgColor", "#d1fae5;");
			redirectAttributes.addFlashAttribute("textColor", "#45484d");
		}

		return "redirect:/items/";
	}

	@PostMapping("/items/updateItem")
	public String updateItem(@RequestParam String itemCustomerUomId, @RequestParam String itemStatus,
			@RequestParam String itemUom, @RequestParam String itemDescription, RedirectAttributes redirectAttributes) {

		String[] parts = itemCustomerUomId.split("_");

		String itemId = parts[0];
		String customerId = parts[1];
		String itemUom_ = parts[2];

		if (!itemsService.itemExists(itemCustomerUomId)) {
			redirectAttributes.addFlashAttribute("msg", "Item " + itemId + " doesnt exists for the customer: "
					+ customerId + " & uom: " + itemUom + " !!!");
			redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
			redirectAttributes.addFlashAttribute("textColor", "#ffffff");
			return "redirect:/items/viewOrEditItem/" + itemCustomerUomId;
		}

		List<String> errors = itemsService.validateItemUpdate(itemCustomerUomId, itemStatus);

		if (!errors.isEmpty()) {
			redirectAttributes.addFlashAttribute("msg", String.join("\n", errors));
			redirectAttributes.addFlashAttribute("itemDescriptionFromController", itemDescription);
			redirectAttributes.addFlashAttribute("selectedItemStatus", itemStatus);
			redirectAttributes.addFlashAttribute("bgColor", "#f03a5b");
			redirectAttributes.addFlashAttribute("textColor", "#f5f0f1");
		} else {
			itemsService.updateItem(itemCustomerUomId, itemDescription, itemStatus, itemUom);
			redirectAttributes.addFlashAttribute("msg",
					"Updated Item " + itemId + " for the customer: " + customerId + " & uom: " + itemUom + " !!!");
			redirectAttributes.addFlashAttribute("bgColor", "#d1fae5;");
			redirectAttributes.addFlashAttribute("textColor", "#45484d");
		}

		return "redirect:/items/viewOrEditItem/" + itemCustomerUomId;
	}

}