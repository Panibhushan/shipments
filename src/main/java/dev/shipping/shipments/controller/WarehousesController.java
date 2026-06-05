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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import dev.shipping.shipments.model.Address;
import dev.shipping.shipments.model.CreateShipmentRequestWithLinesAndAddress;
import dev.shipping.shipments.model.Warehouses;
import dev.shipping.shipments.service.WarehousesService;
import dev.shipping.shipments.utils.MyResourceUtils;

@Controller
public class WarehousesController {

	private final WarehousesService warehousesService;

	public WarehousesController(WarehousesService warehousesService) {
		this.warehousesService = warehousesService;
	}

	@GetMapping("/warehouses/")
	public String showAllWarehouses(Model model) {

		List<Warehouses> warehouses = warehousesService.getAllWarehouses();

		model.addAttribute("warehouses", warehouses);
		model.addAttribute("warehousesList", warehouses);
		model.addAttribute("activePage", "allWarehouses"); // ← this is show which dropdown is active in the navbar
		model.addAttribute("warehouseStatusList", Arrays.asList("Active", "Disabled"));
		model.addAttribute("filterApplied", false);
		return "show-all-warehouses";
	}

	@GetMapping("/warehouses/showWarehousesByFilter")
	public String showWarehousesByFilter(@RequestParam(required = false) String warehouseId,
			@RequestParam(required = false) String warehouseStatus, Model model) {

		System.out.println("/warehouses/showWarehousesByFilter/{warehouseId}/{warehouseStatus}/{itemUom}: "
				+ warehouseId + " / " + warehouseStatus);
		if (warehouseId.equals("ALL") && warehouseStatus.equals("ALL")) {
			return "redirect:/warehouses/";
		}

		model.addAttribute("warehouses", warehousesService.getAllWarehouses()); // this is to display in filter dropdown
		model.addAttribute("warehousesList", warehousesService.getWarehousesList(warehouseId, warehouseStatus)); // this
																													// is
																													// the
																													// resultant
																													// filtered
																													// warehouses
																													// list
		model.addAttribute("selectedWarehouse", warehouseId);
		model.addAttribute("selectedWarehouseStatus", warehouseStatus);
		model.addAttribute("warehouseStatusList", Arrays.asList("Active", "Disabled"));

		model.addAttribute("filterApplied", true);

		return "show-all-warehouses";

	}

	// Adding this addittional method, just incase I missed to update the URL from
	// goToCreateWarehousePage to createWarehousePage in any pages, this will route
	// correctly instead of giving error
	@GetMapping("/warehouses/goToCreateWarehousePage")
	public String goToCreateWarehousePage(Model model) {
		return "redirect:/warehouses/createWarehousePage";
	}

	@GetMapping("/warehouses/createWarehousePage")
	public String addWarehousesPage(Model model) {
		model.addAttribute("warehouse", new Warehouses());
		model.addAttribute("activePage", "createWarehouse"); // ← this is show which dropdown is active in the navbar
		return "create-warehouse";
	}

	@PostMapping("/warehouses/createWarehouse")
	public String saveWarehouses(@ModelAttribute Warehouses warehouse, RedirectAttributes redirectAttributes) {

		String warehouseId = warehouse.getWarehouseId();

		if (warehousesService.warehouseExists(warehouseId)) {
			redirectAttributes.addFlashAttribute("msg",
					"Warehouse " + warehouseId + " already exists !!!"
							+ "&nbsp;&nbsp;&nbsp;&nbsp;<a style='color:yellow;' href='/warehouses/showWarehouseDetails/"
							+ warehouseId + "'>View " + warehouseId + "</a>");
			redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
			redirectAttributes.addFlashAttribute("textColor", "#ffffff");
			return "redirect:/warehouses/createWarehousePage";
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
			redirectAttributes.addFlashAttribute("msg",
					"Created Customer: " + warehouseId + " !!!"
							+ "&nbsp;&nbsp;&nbsp;&nbsp;<a href='/warehouses/showWarehouseDetails/" + warehouseId
							+ "'>View " + warehouseId + "</a>");
			redirectAttributes.addFlashAttribute("bgColor", "#d1fae5;");
			redirectAttributes.addFlashAttribute("textColor", "#45484d");
		}

		return "redirect:/warehouses/createWarehousePage";
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
		/*
		 * model.addAttribute("warehouse", new Warehouses());
		 */

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
			@RequestParam String warehouseStatus, RedirectAttributes redirectAttributes) {

		if (!warehousesService.warehouseExists(warehouseId)) {
			redirectAttributes.addFlashAttribute("msg", "Warehouse " + warehouseId + " doesn't exists !!!");
			redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
			redirectAttributes.addFlashAttribute("textColor", "#ffffff");
			return "redirect:/warehouses/viewOrEditWarehouse/" + warehouseId;
		}

		List<String> errors = warehousesService.validateWarehouseUpdate(warehouseName, warehouseStatus);

		if (!errors.isEmpty()) {
			redirectAttributes.addFlashAttribute("msg", String.join("\n", errors));
			redirectAttributes.addFlashAttribute("warehouseIdFromController", warehouseId);
			redirectAttributes.addFlashAttribute("warehouseNameFromController", warehouseName);
			redirectAttributes.addFlashAttribute("bgColor", "#f03a5b");
			redirectAttributes.addFlashAttribute("textColor", "#f5f0f1");
		} else {
			warehousesService.updateWarehouse(warehouseId, warehouseName, warehouseStatus);
			redirectAttributes.addFlashAttribute("msg", "Updated Warehouse: " + warehouseId + " !!!");
			redirectAttributes.addFlashAttribute("bgColor", "#d1fae5;");
			redirectAttributes.addFlashAttribute("textColor", "#45484d");
		}

		return "redirect:/warehouses/viewOrEditWarehouse/" + warehouseId;
	}

	@PostMapping("/warehouses/createWarehouseWithAddress")
	@ResponseBody
	public String createWarehouseWithAddress(@ModelAttribute Warehouses warehouse, @RequestParam String warehouseId,
			@RequestParam String warehouseName, @RequestParam String warehouseStatus,  @RequestBody Address request) {

		if (warehousesService.warehouseExists(warehouseId)) {
			return "WAREHOUSE_ALREADY_EXISTS: "+warehouseId;
		}

		List<String> errors = warehousesService.validateNewWarehouse(warehouse);

		if (!errors.isEmpty()) {
			return errors.toString() ; 
		} else {
			System.out.println("/warehouses/createWarehouseWithAddress: " + warehouse.toString());
			System.out.println(warehouseId + " -- " + warehouseName + " -- " + warehouseStatus + " -- " 
					+ " -- " + request.toString());

			String createdWarehouseId= warehousesService.createWarehouse(warehouse) ;

			String warehouseCreationWithAddressStatus = warehousesService.createWarehouseWithAddress(createdWarehouseId,  request);

			return warehouseCreationWithAddressStatus;
		}
 
	}

}