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
import dev.shipping.shipments.model.Warehouses;
import dev.shipping.shipments.repo.CustomersRepository;
import dev.shipping.shipments.repo.ShipmentsRepository;
import dev.shipping.shipments.repo.WarehousesRepository;

@Controller
public class WarehousesController {

	private final ShipmentsRepository shipmentsRepo;
	private final CustomersRepository customersRepo;
	private final WarehousesRepository warehousesRepo;

	public WarehousesController(ShipmentsRepository shipmentsRepo, CustomersRepository customersRepo,
			WarehousesRepository warehousesRepo) {
		this.warehousesRepo = warehousesRepo;
		this.shipmentsRepo = shipmentsRepo;
		this.customersRepo = customersRepo;
	}

	@GetMapping("/warehouses/")
	public String showAllWarehouses(Model model) {
		model.addAttribute("warehouses", warehousesRepo.findAll());
		return "show-all-warehouses";
	}

	@GetMapping("/warehouses/goToCreateWarehousePage")
	public String addWarehousesPage(Model model) {
		model.addAttribute("warehouse", new Warehouses());
		return "create-warehouse";
	}

	@PostMapping("/warehouses/createWarehouse")
	public String saveWarehouses(@ModelAttribute Warehouses warehouse, RedirectAttributes redirectAttributes) {
		System.out.println("hitting /warehouses/createWarehouse - saveWarehouses method: " + warehouse);
		String warehouseId = warehouse.getWarehouseId();

		System.out.println("warehouse: " + warehouse.toString());

		System.out.println("warehousesRepo.findById(warehouseId).isPresent(): "
				+ warehousesRepo.findById(warehouseId).isPresent());

		Optional<Warehouses> c = warehousesRepo.findById(warehouseId);
		if (c.isPresent()) {
			System.out.println("inside IF block");
			redirectAttributes.addFlashAttribute("msg", "Warehouse " + warehouseId + " already exists !!!");
			redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
			redirectAttributes.addFlashAttribute("textColor", "#ffffff");
		} else {
			System.out.println("inside ELSE block");
			String messageToDisplay = "", warehouseName = warehouse.getWarehouseName(),
					warehouseAddress = warehouse.getWarehouseAddress();

			boolean doesWarehouseIdHasWhitespace = warehouseId.matches(".*\\s.*"), hasError = false;

			if (doesWarehouseIdHasWhitespace) {
				hasError = true;
				messageToDisplay = "Warehouse ID cannot contain any whitespaces (spaces, tabs, next-line characters)!!\n";
			} else {

				if (warehouseId.length() < 3) {
					hasError = true;
					messageToDisplay = "Warehouse ID must be atleast 3 characters long !!\n";
				} else if (warehouseId.length() > 5) {
					hasError = true;
					messageToDisplay += "Warehouse ID must be maximum 5 characters only !!\n";
				}
			}

			if (warehouseName.trim().length() < 5) {
				hasError = true;
				messageToDisplay += "Warehouse Name must be atleast 5 characters long !!\n";
			} else if (warehouseName.trim().length() > 15) {
				hasError = true;
				messageToDisplay += "Warehouse Name must be maximum 15 characters only !!";
			}

			if (warehouseAddress.trim().length() < 10) {
				hasError = true;
				messageToDisplay += "Warehouse Address must be atleast 10 characters long !!\n";
			} else if (warehouseAddress.trim().length() > 50) {
				hasError = true;
				messageToDisplay += "Warehouse Address must be maximum 50 characters only !!";
			}

			if (hasError) {
				redirectAttributes.addFlashAttribute("msg", messageToDisplay);
				redirectAttributes.addFlashAttribute("warehouseIdFromController", warehouseId);
				redirectAttributes.addFlashAttribute("warehouseNameFromController", warehouseName);
				redirectAttributes.addFlashAttribute("bgColor", "#f03a5b");
				redirectAttributes.addFlashAttribute("textColor", "#f5f0f1");
			} else {
				warehousesRepo.save(warehouse);
				redirectAttributes.addFlashAttribute("msg", "Created Warehouse: " + warehouseId + " !!!");
				redirectAttributes.addFlashAttribute("bgColor", "#d1fae5;");
				redirectAttributes.addFlashAttribute("textColor", "#45484d");
			}
		}

		return "redirect:/warehouses/goToCreateWarehousePage";
	}

	@PostMapping("/warehouses/deleteWarehouse/{warehouseId}")
	public String deleteWarehouse(@PathVariable String warehouseId, RedirectAttributes redirectAttributes) {
		System.out.println("inside /warehouses/deleteWarehouse/{warehouseId} :: deleteWarehouse method :: warehouseId: "
				+ warehouseId);
		Optional<Warehouses> c = warehousesRepo.findById(warehouseId);
		if (!c.isPresent()) {
			System.out.println("inside IF block");
			redirectAttributes.addFlashAttribute("msg", "Warehouse " + warehouseId + " doesnt exists !!!");
			redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
			redirectAttributes.addFlashAttribute("textColor", "#ffffff");
		} else {
			System.out.println("warehousesRepo.deleteById(" + warehouseId + ")");
			warehousesRepo.deleteById(warehouseId);
			redirectAttributes.addFlashAttribute("msg", "Warehouse " + warehouseId + " is Deleted!!!");
			redirectAttributes.addFlashAttribute("bgColor", "#d1fae5;");
			redirectAttributes.addFlashAttribute("textColor", "#45484d");
		}
		return "redirect:/warehouses/";
	}

	@GetMapping("/warehouses/showWarehouseDetails/{warehouseId}")
	public String showWarehouseDetails(@PathVariable String warehouseId, Model model,
			RedirectAttributes redirectAttributes) {
		return "redirect:/warehouses/viewOrEditWarehouse/" + warehouseId;
	}

	@GetMapping("/warehouses/viewOrEditWarehouse/{warehouseId}")
	public String viewOrEditWarehousesPage(@PathVariable String warehouseId, Model model,
			RedirectAttributes redirectAttributes) {
		model.addAttribute("warehouse", new Warehouses());
		System.out.println(
				"hitting /warehouses/viewOrEditWarehouse - viewOrEditWarehousesPage method:-- warehouseId: " + warehouseId);

		Optional<Warehouses> singleWarehouse = warehousesRepo.findById(warehouseId);

		System.out.println("viewOrEditWarehousesPage ::: Optional<Warehouses> c :: " + singleWarehouse.toString());

		if (!singleWarehouse.isPresent()) {
			System.out.println("inside IF block");
			redirectAttributes.addFlashAttribute("msg", "Warehouse " + warehouseId + " doesn't exists !!!");
			redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
			redirectAttributes.addFlashAttribute("textColor", "#ffffff");
			return "redirect:/warehouses/";
		} else {
			model.addAttribute("warehouse", singleWarehouse.get());

			// The list of options for the dropdown of warehouse-status
			model.addAttribute("options", List.of("Active", "Disabled"));

			// The currently selected value (for th:selected comparison)
			model.addAttribute("selectedStatus", singleWarehouse.get().getWarehouseStatus());

			return "edit-warehouse";
		}
	}

	@PostMapping("/warehouses/updateWarehouse")
	public String updateWarehouses(@RequestParam String warehouseId, @RequestParam String warehouseName,
			@RequestParam String warehouseStatus, @RequestParam String warehouseAddress, RedirectAttributes redirectAttributes) {
		System.out.println("hitting /warehouses/updateWarehouse - updateWarehouses method:-- warehouse: " + warehouseId
				+ " -- " + warehouseName + " -- " + warehouseAddress + " -- " + warehouseStatus);

		Optional<Warehouses> c = warehousesRepo.findById(warehouseId);

		System.out.println("updateWarehouses ::: Optional<Warehouses> c :: " + c.toString());

		if (!c.isPresent()) {
			System.out.println("inside IF block");
			redirectAttributes.addFlashAttribute("msg", "Warehouse " + warehouseId + " doesn't exists !!!");
			redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
			redirectAttributes.addFlashAttribute("textColor", "#ffffff");
		} else {
			System.out.println("inside ELSE block");
			String messageToDisplay = "";

			boolean hasError = false;

			if (warehouseName.trim().length() < 5) {
				hasError = true;
				messageToDisplay += "Warehouse Name must be atleast 5 characters long !!\n";
			} else if (warehouseName.trim().length() > 15) {
				hasError = true;
				messageToDisplay += "Warehouse Name must be maximum 15 characters only !!";
			}

			if (warehouseAddress.trim().length() < 10) {
				hasError = true;
				messageToDisplay += "Warehouse Address must be atleast 10 characters long !!\n";
			} else if (warehouseAddress.trim().length() > 50) {
				hasError = true;
				messageToDisplay += "Warehouse Address must be maximum 50 characters only !!";
			}

			if (hasError) {
				redirectAttributes.addFlashAttribute("msg", messageToDisplay);
				redirectAttributes.addFlashAttribute("warehouseIdFromController", warehouseId);
				redirectAttributes.addFlashAttribute("warehouseNameFromController", warehouseName);
				redirectAttributes.addFlashAttribute("warehouseAddressFromController", warehouseAddress);

				redirectAttributes.addFlashAttribute("bgColor", "#f03a5b");
				redirectAttributes.addFlashAttribute("textColor", "#f5f0f1");
			} else {

				Warehouses warehouse = warehousesRepo.findById(warehouseId)
						.orElseThrow(() -> new RuntimeException("Warehouse not found"));

				warehouse.setWarehouseName(warehouseName);
				warehouse.setWarehouseAddress(warehouseAddress);
				warehouse.setWarehouseStatus(warehouseStatus);

				warehousesRepo.save(warehouse);
				redirectAttributes.addFlashAttribute("msg", "Updated Warehouse: " + warehouseId + " !!!");
				redirectAttributes.addFlashAttribute("bgColor", "#d1fae5;");
				redirectAttributes.addFlashAttribute("textColor", "#45484d");
			}
		}

		return "redirect:/warehouses/viewOrEditWarehouse/" + warehouseId;
	}

}
