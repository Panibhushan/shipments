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

import dev.shipping.shipments.model.Warehouses;
import dev.shipping.shipments.service.WarehousesService;

@Controller
public class WarehousesController {

	private final WarehousesService warehousesService;

	public WarehousesController(WarehousesService warehousesService) {
		this.warehousesService = warehousesService;
	}

	@GetMapping("/warehouses/")
	public String showAllWarehouses(Model model) {
		model.addAttribute("warehouses", warehousesService.getAllWarehouses());
		model.addAttribute("activePage", "allWarehouses");  // ←  this is show which dropdown is active in the navbar
		return "show-all-warehouses";
	}

	@GetMapping("/warehouses/goToCreateWarehousePage")
	public String addWarehousesPage(Model model) {
		model.addAttribute("warehouse", new Warehouses());
		model.addAttribute("activePage", "createWarehouse");  // ←  this is show which dropdown is active in the navbar
		return "create-warehouse";
	}

	@PostMapping("/warehouses/createWarehouse")
	public String saveWarehouses(@ModelAttribute Warehouses warehouse, RedirectAttributes redirectAttributes) {

		String warehouseId = warehouse.getWarehouseId();

		if (warehousesService.warehouseExists(warehouseId)) {
			redirectAttributes.addFlashAttribute("msg", "Warehouse " + warehouseId + " already exists !!!");
			redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
			redirectAttributes.addFlashAttribute("textColor", "#ffffff");
			return "redirect:/warehouses/goToCreateWarehousePage";
		}

		List<String> errors = warehousesService.validateNewWarehouse(warehouse);

		if (!errors.isEmpty()) {
			redirectAttributes.addFlashAttribute("msg", String.join("\n", errors));
			redirectAttributes.addFlashAttribute("warehouseIdFromController", warehouseId);
			redirectAttributes.addFlashAttribute("warehouseNameFromController", warehouse.getWarehouseName());
			redirectAttributes.addFlashAttribute("bgColor", "#f03a5b");
			redirectAttributes.addFlashAttribute("textColor", "#f5f0f1");
		} else {
			warehousesService.createWarehouse(warehouse);
			redirectAttributes.addFlashAttribute("msg", "Created Warehouse: " + warehouseId + " !!!");
			redirectAttributes.addFlashAttribute("bgColor", "#d1fae5;");
			redirectAttributes.addFlashAttribute("textColor", "#45484d");
		}

		return "redirect:/warehouses/goToCreateWarehousePage";
	}

	@PostMapping("/warehouses/deleteWarehouse/{warehouseId}")
	public String deleteWarehouse(@PathVariable String warehouseId, RedirectAttributes redirectAttributes) {

		if (!warehousesService.warehouseExists(warehouseId)) {
			redirectAttributes.addFlashAttribute("msg", "Warehouse " + warehouseId + " doesnt exists !!!");
			redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
			redirectAttributes.addFlashAttribute("textColor", "#ffffff");
		} else {
			warehousesService.deleteWarehouse(warehouseId);
			redirectAttributes.addFlashAttribute("msg", "Warehouse " + warehouseId + " is Deleted!!!");
			redirectAttributes.addFlashAttribute("bgColor", "#d1fae5;");
			redirectAttributes.addFlashAttribute("textColor", "#45484d");
		}

		return "redirect:/warehouses/";
	}

	@GetMapping("/warehouses/showWarehouseDetails/{warehouseId}")
	public String showWarehouseDetails(@PathVariable String warehouseId) {
		return "redirect:/warehouses/viewOrEditWarehouse/" + warehouseId;
	}

	@GetMapping("/warehouses/viewOrEditWarehouse/{warehouseId}")
	public String viewOrEditWarehousesPage(@PathVariable String warehouseId, Model model,
			RedirectAttributes redirectAttributes) {

		model.addAttribute("warehouse", new Warehouses());

		if (!warehousesService.warehouseExists(warehouseId)) {
			redirectAttributes.addFlashAttribute("msg", "Warehouse " + warehouseId + " doesn't exists !!!");
			redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
			redirectAttributes.addFlashAttribute("textColor", "#ffffff");
			return "redirect:/warehouses/";
		}

		warehousesService.populateEditWarehouseModel(warehouseId, model);
		return "edit-warehouse";
	}

	@PostMapping("/warehouses/updateWarehouse")
	public String updateWarehouses(@RequestParam String warehouseId, @RequestParam String warehouseName,
			@RequestParam String warehouseStatus, @RequestParam String warehouseAddress,
			RedirectAttributes redirectAttributes) {

		if (!warehousesService.warehouseExists(warehouseId)) {
			redirectAttributes.addFlashAttribute("msg", "Warehouse " + warehouseId + " doesn't exists !!!");
			redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
			redirectAttributes.addFlashAttribute("textColor", "#ffffff");
			return "redirect:/warehouses/viewOrEditWarehouse/" + warehouseId;
		}

		List<String> errors = warehousesService.validateWarehouseUpdate(warehouseName, warehouseAddress);

		if (!errors.isEmpty()) {
			redirectAttributes.addFlashAttribute("msg", String.join("\n", errors));
			redirectAttributes.addFlashAttribute("warehouseIdFromController", warehouseId);
			redirectAttributes.addFlashAttribute("warehouseNameFromController", warehouseName);
			redirectAttributes.addFlashAttribute("warehouseAddressFromController", warehouseAddress);
			redirectAttributes.addFlashAttribute("bgColor", "#f03a5b");
			redirectAttributes.addFlashAttribute("textColor", "#f5f0f1");
		} else {
			warehousesService.updateWarehouse(warehouseId, warehouseName, warehouseStatus, warehouseAddress);
			redirectAttributes.addFlashAttribute("msg", "Updated Warehouse: " + warehouseId + " !!!");
			redirectAttributes.addFlashAttribute("bgColor", "#d1fae5;");
			redirectAttributes.addFlashAttribute("textColor", "#45484d");
		}

		return "redirect:/warehouses/viewOrEditWarehouse/" + warehouseId;
	}

}