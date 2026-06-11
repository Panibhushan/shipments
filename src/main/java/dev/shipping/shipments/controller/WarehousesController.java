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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import dev.shipping.shipments.model.Address;
import dev.shipping.shipments.model.Warehouses;
import dev.shipping.shipments.service.AddressService;
import dev.shipping.shipments.service.WarehousesService;

/**
 * Handles all HTTP requests related to warehouses.
 *
 * Responsibilities:
 *  - Parse and validate incoming HTTP parameters
 *  - Delegate all business logic to WarehousesService
 *  - Populate the view model and set flash attributes for redirects
 *  - Handle address creation and update as REST-style @ResponseBody endpoints
 *
 * This controller intentionally contains NO business logic.
 * Validation, persistence, and domain rules all live in WarehousesService.
 */
@Controller
public class WarehousesController {

    private static final Logger log = LoggerFactory.getLogger(WarehousesController.class);

    /** Valid status values for the warehouse status dropdown. */
    private static final List<String> WAREHOUSE_STATUS_LIST = Arrays.asList("Active", "Disabled");

    private final WarehousesService warehousesService;
    private final AddressService addressService;

    public WarehousesController(WarehousesService warehousesService, AddressService addressService) {
        this.warehousesService = warehousesService;
        this.addressService = addressService;
    }

    // ─────────────────────────────────────────────
    // LIST / FILTER
    // ─────────────────────────────────────────────

    /**
     * Renders the full warehouse list with no filter applied.
     * The same list is passed twice — once for the filter dropdown (all warehouses)
     * and once for the data table — so the dropdown never loses options when a
     * filter is active.
     */
    @GetMapping("/warehouses/")
    public String showAllWarehouses(Model model) {
        log.info("GET /warehouses/ → loading all warehouses (no filter)");
        List<Warehouses> warehouses = warehousesService.getAllWarehouses();
        model.addAttribute("warehouses", warehouses);      // filter dropdown
        model.addAttribute("warehousesList", warehouses);  // data table
        model.addAttribute("activePage", "allWarehouses");
        model.addAttribute("warehouseStatusList", WAREHOUSE_STATUS_LIST);
        model.addAttribute("filterApplied", false);
        return "show-all-warehouses";
    }

    /**
     * Filters the warehouse list using the submitted form values.
     * If both filters are "ALL", redirects to the unfiltered list
     * to avoid a redundant query.
     */
    @GetMapping("/warehouses/showWarehousesByFilter")
    public String showWarehousesByFilter(
            @RequestParam(required = false, defaultValue = "ALL") String warehouseId,
            @RequestParam(required = false, defaultValue = "ALL") String warehouseStatus,
            Model model) {

        log.info("GET /warehouses/showWarehousesByFilter → warehouseId={}, warehouseStatus={}",
                warehouseId, warehouseStatus);

        // No filter applied at all → redirect to the unfiltered list
        if (warehouseId.equals("ALL") && warehouseStatus.equals("ALL")) {
            log.info("showWarehousesByFilter() → all filters are ALL, redirecting to /warehouses/");
            return "redirect:/warehouses/";
        }

        List<Warehouses> filtered = warehousesService.getWarehousesList(warehouseId, warehouseStatus);
        log.info("showWarehousesByFilter() → returned {} warehouse(s)", filtered.size());

        model.addAttribute("warehouses", warehousesService.getAllWarehouses()); // dropdown always shows all
        model.addAttribute("warehousesList", filtered);
        model.addAttribute("selectedWarehouse", warehouseId);
        model.addAttribute("selectedWarehouseStatus", warehouseStatus);
        model.addAttribute("warehouseStatusList", WAREHOUSE_STATUS_LIST);
        model.addAttribute("filterApplied", true);
        return "show-all-warehouses";
    }

    // ─────────────────────────────────────────────
    // CREATE WAREHOUSE
    // ─────────────────────────────────────────────

    /**
     * Backward-compatibility redirect for any page still using the old URL.
     */
    @GetMapping("/warehouses/goToCreateWarehousePage")
    public String goToCreateWarehousePage() {
        log.info("GET /warehouses/goToCreateWarehousePage → redirecting to /warehouses/createWarehousePage");
        return "redirect:/warehouses/createWarehousePage";
    }

    /** Renders the create-warehouse form with an empty Warehouses binding object. */
    @GetMapping("/warehouses/createWarehousePage")
    public String addWarehousesPage(Model model) {
        log.info("GET /warehouses/createWarehousePage → rendering create warehouse form");
        model.addAttribute("warehouse", new Warehouses());
        model.addAttribute("activePage", "createWarehouse");
        return "create-warehouse-with-address";
    }

    /**
     * Handles the create-warehouse form POST.
     *
     * Flow:
     *  1. Reject immediately if the warehouseId already exists.
     *  2. Run field-level validation via the service.
     *  3. On errors → redirect back to the form with error flash attributes.
     *  4. On success → create the warehouse and redirect with a success message.
     */
    @PostMapping("/warehouses/createWarehouse")
    public String saveWarehouse(@ModelAttribute Warehouses warehouse, RedirectAttributes redirectAttributes) {

        String warehouseId = warehouse.getWarehouseId();
        log.info("POST /warehouses/createWarehouse → warehouseId={}", warehouseId);

        // ── 1. Duplicate check ────────────────────────────────────────────────
        if (warehousesService.warehouseExists(warehouseId)) {
            log.warn("saveWarehouse() → warehouse already exists: warehouseId={}", warehouseId);
            redirectAttributes.addFlashAttribute("msg",
                    "Warehouse " + warehouseId + " already exists!"
                    + "&nbsp;&nbsp;&nbsp;&nbsp;<a style='color:yellow;'"
                    + " href='/warehouses/showWarehouseDetails/" + warehouseId
                    + "'>View " + warehouseId + "</a>");
            redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
            redirectAttributes.addFlashAttribute("textColor", "#ffffff");
            return "redirect:/warehouses/createWarehousePage";
        }

        // ── 2. Validation ─────────────────────────────────────────────────────
        List<String> errors = warehousesService.validateNewWarehouse(warehouse);

        if (!errors.isEmpty()) {
            log.warn("saveWarehouse() → validation failed for warehouseId={}: {}", warehouseId, errors);
            redirectAttributes.addFlashAttribute("msg", String.join("\n", errors));
            // Re-populate fields so the user doesn't have to retype them
            redirectAttributes.addFlashAttribute("warehouseIdFromController", warehouseId);
            redirectAttributes.addFlashAttribute("warehouseNameFromController", warehouse.getWarehouseName());
            redirectAttributes.addFlashAttribute("bgColor", "#f03a5b");
            redirectAttributes.addFlashAttribute("textColor", "#f5f0f1");
        } else {
            // ── 3. Persist ────────────────────────────────────────────────────
            warehousesService.createWarehouse(warehouse);
            log.info("saveWarehouse() → warehouse created: warehouseId={}", warehouseId);
            redirectAttributes.addFlashAttribute("msg",
                    "Created Warehouse: " + warehouseId + "!"
                    + "&nbsp;&nbsp;&nbsp;&nbsp;<a href='/warehouses/showWarehouseDetails/" + warehouseId
                    + "'>View " + warehouseId + "</a>");
            redirectAttributes.addFlashAttribute("bgColor", "#d1fae5");
            redirectAttributes.addFlashAttribute("textColor", "#45484d");
        }

        return "redirect:/warehouses/createWarehousePage";
    }

    /**
     * REST endpoint: creates a warehouse and links a delivery address to it in one call.
     * Used by API/integration clients that supply the address at creation time.
     *
     * Returns a plain-text status string:
     *  - "WAREHOUSE_ALREADY_EXISTS: {id}" if the ID is taken
     *  - validation error list as a string if fields are invalid
     *  - "WAREHOUSE_CREATED_SUCCESSFULLY_WITH_ID: {id}" on success
     */
    @PostMapping("/warehouses/createWarehouseWithAddress")
    @ResponseBody
    public String createWarehouseWithAddress(
            @ModelAttribute Warehouses warehouse,
            @RequestBody Address address) {

        String warehouseId = warehouse.getWarehouseId();
        log.info("POST /warehouses/createWarehouseWithAddress → warehouseId={}", warehouseId);

        // ── 1. Duplicate check ────────────────────────────────────────────────
        if (warehousesService.warehouseExists(warehouseId)) {
            log.warn("createWarehouseWithAddress() → warehouse already exists: warehouseId={}", warehouseId);
            return "WAREHOUSE_ALREADY_EXISTS: " + warehouseId;
        }

        // ── 2. Validation ─────────────────────────────────────────────────────
        List<String> errors = warehousesService.validateNewWarehouse(warehouse);
        if (!errors.isEmpty()) {
            log.warn("createWarehouseWithAddress() → validation failed for warehouseId={}: {}", warehouseId, errors);
            return errors.toString();
        }

        // ── 3. Create warehouse header, then link address ─────────────────────
        String createdId = warehousesService.createWarehouse(warehouse);
        String result = warehousesService.createWarehouseWithAddress(createdId, address);

        if (result.startsWith("WAREHOUSE_CREATED")) {
            log.info("createWarehouseWithAddress() completed → warehouseId={}", createdId);
        } else {
            log.error("createWarehouseWithAddress() failed after header creation → warehouseId={}: {}",
                    createdId, result);
        }

        return result;
    }

    // ─────────────────────────────────────────────
    // VIEW / EDIT WAREHOUSE
    // ─────────────────────────────────────────────

    /**
     * Redirects the legacy /showWarehouseDetails/{id} URL to the canonical
     * /viewOrEditWarehouse/{id} URL so old links keep working.
     */
    @GetMapping("/warehouses/showWarehouseDetails/{warehouseId}")
    public String showWarehouseDetails(@PathVariable String warehouseId) {
        log.info("GET /warehouses/showWarehouseDetails/{} → redirecting to viewOrEditWarehouse", warehouseId);
        return "redirect:/warehouses/viewOrEditWarehouse/" + warehouseId;
    }

    /**
     * Renders the view/edit page for a specific warehouse.
     * Redirects to the warehouse list with an error message if the ID does not exist.
     */
    @GetMapping("/warehouses/viewOrEditWarehouse/{warehouseId}")
    public String viewOrEditWarehouse(@PathVariable String warehouseId, Model model,
            RedirectAttributes redirectAttributes) {

        log.info("GET /warehouses/viewOrEditWarehouse/{}", warehouseId);

        if (!warehousesService.warehouseExists(warehouseId)) {
            log.warn("viewOrEditWarehouse() → warehouse not found: warehouseId={}", warehouseId);
            redirectAttributes.addFlashAttribute("msg", "Warehouse " + warehouseId + " doesn't exist!");
            redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
            redirectAttributes.addFlashAttribute("textColor", "#ffffff");
            return "redirect:/warehouses/";
        }

        warehousesService.populateEditWarehouseModel(warehouseId, model);
        return "edit-warehouse";
    }

    // ─────────────────────────────────────────────
    // UPDATE WAREHOUSE
    // ─────────────────────────────────────────────

    /**
     * Handles the update-warehouse form POST.
     *
     * Flow:
     *  1. Reject immediately if the warehouse no longer exists (defensive check).
     *  2. Run field-level validation via the service.
     *  3. On errors → redirect back to the edit page with error flash attributes.
     *  4. On success → update the warehouse and redirect with a success message.
     */
    @PostMapping("/warehouses/updateWarehouse")
    public String updateWarehouse(
            @RequestParam String warehouseId,
            @RequestParam String warehouseName,
            @RequestParam String warehouseStatus,
            RedirectAttributes redirectAttributes) {

        log.info("POST /warehouses/updateWarehouse → warehouseId={}, warehouseStatus={}",
                warehouseId, warehouseStatus);

        // ── 1. Existence check ────────────────────────────────────────────────
        if (!warehousesService.warehouseExists(warehouseId)) {
            log.warn("updateWarehouse() → warehouse not found: warehouseId={}", warehouseId);
            redirectAttributes.addFlashAttribute("msg", "Warehouse " + warehouseId + " doesn't exist!");
            redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
            redirectAttributes.addFlashAttribute("textColor", "#ffffff");
            return "redirect:/warehouses/viewOrEditWarehouse/" + warehouseId;
        }

        // ── 2. Validation ─────────────────────────────────────────────────────
        List<String> errors = warehousesService.validateWarehouseUpdate(warehouseName, warehouseStatus);

        if (!errors.isEmpty()) {
            log.warn("updateWarehouse() → validation failed for warehouseId={}: {}", warehouseId, errors);
            redirectAttributes.addFlashAttribute("msg", String.join("\n", errors));
            // Re-populate fields so the user sees what they entered
            redirectAttributes.addFlashAttribute("warehouseIdFromController", warehouseId);
            redirectAttributes.addFlashAttribute("warehouseNameFromController", warehouseName);
            redirectAttributes.addFlashAttribute("bgColor", "#f03a5b");
            redirectAttributes.addFlashAttribute("textColor", "#f5f0f1");
        } else {
            // ── 3. Persist ────────────────────────────────────────────────────
            warehousesService.updateWarehouse(warehouseId, warehouseName, warehouseStatus);
            log.info("updateWarehouse() → warehouse updated: warehouseId={}", warehouseId);
            redirectAttributes.addFlashAttribute("msg", "Updated Warehouse: " + warehouseId + "!");
            redirectAttributes.addFlashAttribute("bgColor", "#d1fae5");
            redirectAttributes.addFlashAttribute("textColor", "#45484d");
        }

        return "redirect:/warehouses/viewOrEditWarehouse/" + warehouseId;
    }

    // ─────────────────────────────────────────────
    // DELETE WAREHOUSE
    // ─────────────────────────────────────────────

    /**
     * Deletes a warehouse. Deletion is blocked if the warehouse still has
     * inventory records — the user must adjust out all inventory first.
     *
     * The service returns a status string; the controller translates that into
     * the appropriate flash message and colours.
     */
    @PostMapping("/warehouses/deleteWarehouse/{warehouseId}")
    public String deleteWarehouse(@PathVariable String warehouseId, RedirectAttributes redirectAttributes) {

        log.info("POST /warehouses/deleteWarehouse/{}", warehouseId);

        if (!warehousesService.warehouseExists(warehouseId)) {
            log.warn("deleteWarehouse() → warehouse not found: warehouseId={}", warehouseId);
            redirectAttributes.addFlashAttribute("msg", "Warehouse " + warehouseId + " doesn't exist!");
            redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
            redirectAttributes.addFlashAttribute("textColor", "#ffffff");
        } else {
            String deletionStatus = warehousesService.deleteWarehouse(warehouseId);

            if ("WAREHOUSE_DELETED".equals(deletionStatus)) {
                log.info("deleteWarehouse() → warehouse deleted: warehouseId={}", warehouseId);
                redirectAttributes.addFlashAttribute("msg", "Warehouse " + warehouseId + " has been deleted.");
                redirectAttributes.addFlashAttribute("bgColor", "#d1fae5");
                redirectAttributes.addFlashAttribute("textColor", "#45484d");
            } else {
                // Inventory still exists — guide the user to clear it first
                log.warn("deleteWarehouse() blocked by existing inventory → warehouseId={}", warehouseId);
                redirectAttributes.addFlashAttribute("msg",
                        "Inventory exists! Adjust out all inventory from <strong>" + warehouseId
                        + "</strong> before deleting it."
                        + "&nbsp;&nbsp;&nbsp;&nbsp;<a style='color: #ffffff;' target='_blank'"
                        + " href='/inventory/showInventoryByFilters?itemId=&itemUom=ALL&customerId=ALL&warehouseSelect="
                        + warehouseId + "'>View Inventory</a>");
                redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
                redirectAttributes.addFlashAttribute("textColor", "#ffffff");
            }
        }

        return "redirect:/warehouses/";
    }

    // ─────────────────────────────────────────────
    // ADDRESS
    // ─────────────────────────────────────────────

    /**
     * REST endpoint: updates the delivery address linked to a warehouse.
     *
     * Because addresses use a hash-based PK, updating any field creates a new
     * address record (the old one is not deleted — it may be shared with other
     * entities). The warehouse's addressId foreign key is updated to point to
     * the new record.
     *
     * Returns a plain-text status string from the service.
     */
    @PostMapping("/warehouses/updateAddress")
    @ResponseBody
    public String updateAddress(
            @RequestParam String warehouseId,
            @RequestBody Address address) {

        log.info("POST /warehouses/updateAddress → warehouseId={}", warehouseId);

        String result = warehousesService.updateAddress(warehouseId, address);

        if (result.contains("SUCCESSFULLY")) {
            log.info("updateAddress() completed → warehouseId={}: {}", warehouseId, result);
        } else {
            log.warn("updateAddress() failed → warehouseId={}: {}", warehouseId, result);
        }

        return result;
    }
}