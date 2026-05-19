package dev.shipping.shipments.controller;

import java.util.List;

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
import dev.shipping.shipments.service.ItemsService;
import dev.shipping.shipments.service.WarehousesService;

@Controller
public class ItemsController {

	private final ItemsService itemsService;

	public ItemsController(ItemsService itemsService) {
		this.itemsService = itemsService;
	}

	@GetMapping("/items/")
	public String showAllItems(Model model) {
		model.addAttribute("items", itemsService.getAllItems());
		return "show-all-items";
	}

	@GetMapping("/items/goToCreateItemPage")
	public String addItemsPage(Model model) {
		model.addAttribute("items", new Items());
		model.addAttribute("customers", itemsService.getActiveAndValidCustomers());
		return "create-item";
	}

	@PostMapping("/items/createItem")
	public String saveItems(@ModelAttribute Items item, RedirectAttributes redirectAttributes) {

		String itemId = item.getItemId();
		String customerId = item.getCustomerId();
		String itemUom = item.getItemUom();
		String itemCustomerUomId1 = item.getItemCustomerUomId();
		String itemCustomerUomId = itemId+"_"+customerId+"_"+itemUom;

		System.out.println("itemCustomerUomId: "+itemCustomerUomId+" --- itemCustomerUomId1: "+itemCustomerUomId1);
		
		if (itemsService.itemExists(itemCustomerUomId)) {
			redirectAttributes.addFlashAttribute("msg", "Item " + itemId + " already exists for this customer: "
					+ customerId + " & uom: " + itemUom + " !!!");
			redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
			redirectAttributes.addFlashAttribute("textColor", "#ffffff");
			return "redirect:/items/goToCreateItemPage";
		}

		List<String> errors = itemsService.validateNewItem(item);

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

		return "redirect:/items/goToCreateItemPage";
	}

	@GetMapping("/items/viewOrEditItem/{itemCustomerUomId}")
	public String viewOrEditItemsPage(@PathVariable String itemCustomerUomId, Model model, RedirectAttributes redirectAttributes) {

		model.addAttribute("item", new Items());
		
		
		String text = "";

		String[] parts = itemCustomerUomId.split("_");

		String itemId =parts[0];
		String customerId = parts[1];
		String itemUom =parts[2];		 
		
		if (!itemsService.itemExists(itemCustomerUomId)) {
			redirectAttributes.addFlashAttribute("msg", "Item " + itemId + " doesnt exists for this customer: "
					+ customerId + " & uom: " + itemUom + " !!!");
			redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
			redirectAttributes.addFlashAttribute("textColor", "#ffffff");
			return "redirect:/items/";
		}

		itemsService.populateEditItemModel(itemCustomerUomId, model);
		return "edit-item";
	}

	@GetMapping("/items/showItemDetails/{itemId}")
	public String showItemDetails(@PathVariable String itemId) {
		return "redirect:/items/viewOrEditItem/" + itemId;
	}

	/*
	 * @PostMapping("/warehouses/deleteWarehouse/{warehouseId}") public String
	 * deleteWarehouse(@PathVariable String warehouseId, RedirectAttributes
	 * redirectAttributes) {
	 * 
	 * if (!warehousesService.warehouseExists(warehouseId)) {
	 * redirectAttributes.addFlashAttribute("msg", "Warehouse " + warehouseId +
	 * " doesnt exists !!!"); redirectAttributes.addFlashAttribute("bgColor",
	 * "#d95f6c"); redirectAttributes.addFlashAttribute("textColor", "#ffffff"); }
	 * else { warehousesService.deleteWarehouse(warehouseId);
	 * redirectAttributes.addFlashAttribute("msg", "Warehouse " + warehouseId +
	 * " is Deleted!!!"); redirectAttributes.addFlashAttribute("bgColor",
	 * "#d1fae5;"); redirectAttributes.addFlashAttribute("textColor", "#45484d"); }
	 * 
	 * return "redirect:/warehouses/"; }
	 * 
	 * @GetMapping("/warehouses/showWarehouseDetails/{warehouseId}") public String
	 * showWarehouseDetails(@PathVariable String warehouseId) { return
	 * "redirect:/warehouses/viewOrEditWarehouse/" + warehouseId; }
	 * 
	 * @GetMapping("/warehouses/viewOrEditWarehouse/{warehouseId}") public String
	 * viewOrEditWarehousesPage(@PathVariable String warehouseId, Model model,
	 * RedirectAttributes redirectAttributes) {
	 * 
	 * model.addAttribute("warehouse", new Warehouses());
	 * 
	 * if (!warehousesService.warehouseExists(warehouseId)) {
	 * redirectAttributes.addFlashAttribute("msg", "Warehouse " + warehouseId +
	 * " doesn't exists !!!"); redirectAttributes.addFlashAttribute("bgColor",
	 * "#d95f6c"); redirectAttributes.addFlashAttribute("textColor", "#ffffff");
	 * return "redirect:/warehouses/"; }
	 * 
	 * warehousesService.populateEditWarehouseModel(warehouseId, model); return
	 * "edit-warehouse"; }
	 * 
	 * @PostMapping("/warehouses/updateWarehouse") public String
	 * updateWarehouses(@RequestParam String warehouseId, @RequestParam String
	 * warehouseName,
	 * 
	 * @RequestParam String warehouseStatus, @RequestParam String warehouseAddress,
	 * RedirectAttributes redirectAttributes) {
	 * 
	 * if (!warehousesService.warehouseExists(warehouseId)) {
	 * redirectAttributes.addFlashAttribute("msg", "Warehouse " + warehouseId +
	 * " doesn't exists !!!"); redirectAttributes.addFlashAttribute("bgColor",
	 * "#d95f6c"); redirectAttributes.addFlashAttribute("textColor", "#ffffff");
	 * return "redirect:/warehouses/viewOrEditWarehouse/" + warehouseId; }
	 * 
	 * List<String> errors =
	 * warehousesService.validateWarehouseUpdate(warehouseName, warehouseAddress);
	 * 
	 * if (!errors.isEmpty()) { redirectAttributes.addFlashAttribute("msg",
	 * String.join("\n", errors));
	 * redirectAttributes.addFlashAttribute("warehouseIdFromController",
	 * warehouseId);
	 * redirectAttributes.addFlashAttribute("warehouseNameFromController",
	 * warehouseName);
	 * redirectAttributes.addFlashAttribute("warehouseAddressFromController",
	 * warehouseAddress); redirectAttributes.addFlashAttribute("bgColor",
	 * "#f03a5b"); redirectAttributes.addFlashAttribute("textColor", "#f5f0f1"); }
	 * else { warehousesService.updateWarehouse(warehouseId, warehouseName,
	 * warehouseStatus, warehouseAddress);
	 * redirectAttributes.addFlashAttribute("msg", "Updated Warehouse: " +
	 * warehouseId + " !!!"); redirectAttributes.addFlashAttribute("bgColor",
	 * "#d1fae5;"); redirectAttributes.addFlashAttribute("textColor", "#45484d"); }
	 * 
	 * return "redirect:/warehouses/viewOrEditWarehouse/" + warehouseId; }
	 */

}