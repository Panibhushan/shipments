package dev.shipping.shipments.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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
import dev.shipping.shipments.model.Items;
import dev.shipping.shipments.service.CustomersService;
import dev.shipping.shipments.service.ItemsService;
import dev.shipping.shipments.service.ShipmentsService;

/**
 * Handles all HTTP requests related to items.
 *
 * Responsibilities:
 *  - Parse and validate incoming HTTP parameters
 *  - Delegate all business logic to ItemsService
 *  - Populate the view model and set flash attributes for redirects
 *
 * This controller intentionally contains NO business logic.
 * Validation, persistence, and domain rules all live in ItemsService.
 *
 * Item composite PK convention: itemId_customerId_itemUom
 * (e.g. "SKU001_CUST01_EACH"), referred to throughout as itemCustomerUomId.
 */
@Controller
public class ItemsController {

    private static final Logger log = LoggerFactory.getLogger(ItemsController.class);

    /**
     * Valid units of measure for items.
     * Defined once here as a constant so the same list is reused across all
     * handlers without re-instantiating it on every request.
     */
    private static final List<String> ITEM_UOMS_LIST = Arrays.asList("EACH", "MTR", "CMTR", "PAIR");

    private final ItemsService itemsService;
    private final ShipmentsService shipmentsService;
    private final CustomersService customersService;

    public ItemsController(ItemsService itemsService, ShipmentsService shipmentsService,
            CustomersService customersService) {
        this.itemsService = itemsService;
        this.shipmentsService = shipmentsService;
        this.customersService = customersService;
    }

    // ─────────────────────────────────────────────
    // LIST / FILTER
    // ─────────────────────────────────────────────

    /** Renders the full items list with no filter applied. */
    @GetMapping("/items/")
    public String showAllItems(Model model) {
        log.info("GET /items/ → loading all items (no filter)");
        model.addAttribute("items", itemsService.getAllItems());
        model.addAttribute("selectedCustomer", "ALL");
        model.addAttribute("customers", customersService.getAllCustomers());
        model.addAttribute("activePage", "allItems");
        model.addAttribute("itemUomsList", ITEM_UOMS_LIST);
        model.addAttribute("filterApplied", false);
        return "show-all-items";
    }

    /**
     * Filters the items list using the submitted form values.
     * If all three filters are "ALL", redirects to the unfiltered list
     * to avoid a redundant query.
     */
    @GetMapping("/items/showItemsByFilter")
    public String showItemsByFilter(
            @RequestParam(required = false, defaultValue = "ALL") String customerId,
            @RequestParam(required = false, defaultValue = "ALL") String itemId,
            @RequestParam(required = false, defaultValue = "ALL") String itemUom,
            Model model) {

        log.info("GET /items/showItemsByFilter → customerId={}, itemId={}, itemUom={}",
                customerId, itemId, itemUom);

        // No filter applied at all → redirect to the unfiltered list
        if (customerId.equals("ALL") && itemId.equals("ALL") && itemUom.equals("ALL")) {
            log.info("showItemsByFilter() → all filters are ALL, redirecting to /items/");
            return "redirect:/items/";
        }

        List<Items> items = itemsService.getItemsList(customerId, itemId, itemUom);
        log.info("showItemsByFilter() → returned {} item(s)", items.size());

        model.addAttribute("items", items);
        model.addAttribute("selectedCustomer", customerId);
        model.addAttribute("selectedItemId", itemId.equals("ALL") ? "" : itemId);
        model.addAttribute("selectedItemUom", itemUom);
        model.addAttribute("customers", customersService.getAllCustomers());
        model.addAttribute("itemUomsList", ITEM_UOMS_LIST);
        model.addAttribute("filterApplied", true);
        return "show-all-items";
    }

    // ─────────────────────────────────────────────
    // CREATE ITEM
    // ─────────────────────────────────────────────

    /**
     * Backward-compatibility redirect for any page still using the old URL.
     */
    @GetMapping("/items/goToCreateItemPage")
    public String goToCreateItemPage() {
        log.info("GET /items/goToCreateItemPage → redirecting to /items/createItemPage");
        return "redirect:/items/createItemPage";
    }

    /** Renders the create-item form with active customers and UOM options pre-loaded. */
    @GetMapping("/items/createItemPage")
    public String addItemsPage(Model model) {
        log.info("GET /items/createItemPage → rendering create item form");
        model.addAttribute("items", new Items());
        model.addAttribute("customers", itemsService.getActiveAndValidCustomers());
        model.addAttribute("activePage", "createItem");
        model.addAttribute("itemUomsList", ITEM_UOMS_LIST);
        return "create-item";
    }

    /**
     * Handles the create-item form POST.
     *
     * Flow:
     *  1. Reject immediately if the itemCustomerUomId already exists.
     *  2. Run field-level validation via the service.
     *  3. On errors → redirect back to the form with error flash attributes
     *     (pre-populating fields so the user doesn't retype everything).
     *  4. On success → create the item and redirect with a success message.
     *
     * Note: the composite PK is assembled here from the individual form fields
     * rather than trusting the hidden itemCustomerUomId field, as a safety measure.
     */
    @PostMapping("/items/createItem")
    public String saveItem(@ModelAttribute Items item, RedirectAttributes redirectAttributes) {

        String itemId     = item.getItemId();
        String customerId = item.getCustomerId();
        String itemUom    = item.getItemUom();

        // Assemble the composite PK from its parts
        String itemCustomerUomId = itemId + "_" + customerId + "_" + itemUom;

        log.info("POST /items/createItem → itemCustomerUomId={}", itemCustomerUomId);

        // ── 1. Duplicate check ────────────────────────────────────────────────
        if (itemsService.itemExists(itemCustomerUomId)) {
            log.warn("saveItem() → item already exists: itemCustomerUomId={}", itemCustomerUomId);
            redirectAttributes.addFlashAttribute("msg",
                    "Item " + itemId + " already exists for customer: " + customerId + " & UOM: " + itemUom + "!");
            redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
            redirectAttributes.addFlashAttribute("textColor", "#ffffff");
            return "redirect:/items/createItemPage";
        }

        // ── 2. Validation ─────────────────────────────────────────────────────
        List<String> errors = itemsService.validateNewItem(item, ITEM_UOMS_LIST);

        if (!errors.isEmpty()) {
            log.warn("saveItem() → validation failed for itemCustomerUomId={}: {}", itemCustomerUomId, errors);
            redirectAttributes.addFlashAttribute("msg", String.join("\n", errors));
            // Re-populate form fields so the user doesn't have to retype them
            redirectAttributes.addFlashAttribute("selectedCustomer", customerId);
            redirectAttributes.addFlashAttribute("enteredItemId", itemId);
            redirectAttributes.addFlashAttribute("enteredItemDescription", item.getItemDescription());
            redirectAttributes.addFlashAttribute("selectedItemUom", item.getItemUom());
            redirectAttributes.addFlashAttribute("selectedItemStatus", item.getItemStatus());
            redirectAttributes.addFlashAttribute("bgColor", "#f03a5b");
            redirectAttributes.addFlashAttribute("textColor", "#f5f0f1");
        } else {
            // ── 3. Persist ────────────────────────────────────────────────────
            itemsService.createItem(item);
            log.info("saveItem() → item created: itemCustomerUomId={}", itemCustomerUomId);
            redirectAttributes.addFlashAttribute("msg",
                    "Created Item: " + itemId + "!"
                    + " &nbsp;&nbsp;<a href='/items/showItemDetails/" + itemCustomerUomId
                    + "'>View Item Details</a>");
            redirectAttributes.addFlashAttribute("itemCustomerUomId", itemCustomerUomId);
            redirectAttributes.addFlashAttribute("bgColor", "#d1fae5");
            redirectAttributes.addFlashAttribute("textColor", "#45484d");
        }

        // Always carry the UOM list through flash so the form repopulates correctly
        redirectAttributes.addFlashAttribute("itemUomsList", ITEM_UOMS_LIST);
        return "redirect:/items/createItemPage";
    }

    // ─────────────────────────────────────────────
    // VIEW / EDIT ITEM
    // ─────────────────────────────────────────────

    /**
     * Redirects the legacy /showItemDetails/{id} URL to the canonical
     * /viewOrEditItem/{id} URL so old links keep working.
     */
    @GetMapping("/items/showItemDetails/{itemCustomerUomId}")
    public String showItemDetails(@PathVariable String itemCustomerUomId) {
        log.info("GET /items/showItemDetails/{} → redirecting to viewOrEditItem", itemCustomerUomId);
        return "redirect:/items/viewOrEditItem/" + itemCustomerUomId;
    }

    /**
     * Renders the view/edit page for a specific item.
     * Redirects to the items list with an error message if the composite key
     * does not match any existing record.
     *
     * The composite PK is split into its components to produce a meaningful
     * error message (showing itemId, customerId, and UOM separately).
     */
    @GetMapping("/items/viewOrEditItem/{itemCustomerUomId}")
    public String viewOrEditItem(@PathVariable String itemCustomerUomId, Model model,
            RedirectAttributes redirectAttributes) {

        log.info("GET /items/viewOrEditItem/{}", itemCustomerUomId);

        String[] parts    = itemCustomerUomId.split("_");
        String itemId     = parts[0];
        String customerId = parts[1];
        String itemUom    = parts[2];

        if (!itemsService.itemExists(itemCustomerUomId)) {
            log.warn("viewOrEditItem() → item not found: itemCustomerUomId={}", itemCustomerUomId);
            redirectAttributes.addFlashAttribute("msg",
                    "Item " + itemId + " doesn't exist for customer: " + customerId + " & UOM: " + itemUom + "!");
            redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
            redirectAttributes.addFlashAttribute("textColor", "#ffffff");
            return "redirect:/items/";
        }

        // Bind an empty Items object so Thymeleaf form bindings don't NPE,
        // then let the service overwrite it with the real data
        model.addAttribute("item", new Items());
        itemsService.populateEditItemModel(itemCustomerUomId, model);
        model.addAttribute("itemUomsList", ITEM_UOMS_LIST);
        return "edit-item";
    }

    // ─────────────────────────────────────────────
    // UPDATE ITEM
    // ─────────────────────────────────────────────

    /**
     * Handles the update-item form POST.
     *
     * Flow:
     *  1. Reject immediately if the item no longer exists (defensive check).
     *  2. Run field-level validation via the service.
     *  3. On errors → redirect back to the edit page with error flash attributes.
     *  4. On success → update the item and redirect with a success message.
     */
    @PostMapping("/items/updateItem")
    public String updateItem(
            @RequestParam String itemCustomerUomId,
            @RequestParam String itemStatus,
            @RequestParam String itemUom,
            @RequestParam String itemDescription,
            RedirectAttributes redirectAttributes) {

        String[] parts    = itemCustomerUomId.split("_");
        String itemId     = parts[0];
        String customerId = parts[1];

        log.info("POST /items/updateItem → itemCustomerUomId={}, itemStatus={}", itemCustomerUomId, itemStatus);

        // ── 1. Existence check ────────────────────────────────────────────────
        if (!itemsService.itemExists(itemCustomerUomId)) {
            log.warn("updateItem() → item not found: itemCustomerUomId={}", itemCustomerUomId);
            redirectAttributes.addFlashAttribute("msg",
                    "Item " + itemId + " doesn't exist for customer: " + customerId + " & UOM: " + itemUom + "!");
            redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
            redirectAttributes.addFlashAttribute("textColor", "#ffffff");
            return "redirect:/items/viewOrEditItem/" + itemCustomerUomId;
        }

        // ── 2. Validation ─────────────────────────────────────────────────────
        List<String> errors = itemsService.validateItemUpdate(itemDescription, itemStatus);

        if (!errors.isEmpty()) {
            log.warn("updateItem() → validation failed for itemCustomerUomId={}: {}", itemCustomerUomId, errors);
            redirectAttributes.addFlashAttribute("msg", String.join("\n", errors));
            // Re-populate fields so the user sees what they entered
            redirectAttributes.addFlashAttribute("itemDescriptionFromController", itemDescription);
            redirectAttributes.addFlashAttribute("selectedItemStatus", itemStatus);
            redirectAttributes.addFlashAttribute("bgColor", "#f03a5b");
            redirectAttributes.addFlashAttribute("textColor", "#f5f0f1");
        } else {
            // ── 3. Persist ────────────────────────────────────────────────────
            itemsService.updateItem(itemCustomerUomId, itemDescription, itemStatus, itemUom);
            log.info("updateItem() → item updated: itemCustomerUomId={}", itemCustomerUomId);
            redirectAttributes.addFlashAttribute("msg",
                    "Updated Item " + itemId + " for customer: " + customerId + " & UOM: " + itemUom + "!");
            redirectAttributes.addFlashAttribute("bgColor", "#d1fae5");
            redirectAttributes.addFlashAttribute("textColor", "#45484d");
        }

        return "redirect:/items/viewOrEditItem/" + itemCustomerUomId;
    }

    // ─────────────────────────────────────────────
    // DELETE ITEM
    // ─────────────────────────────────────────────

    /**
     * Deletes an item by its composite PK.
     * Redirects to the items list with a success or error message.
     */
    @PostMapping("/items/deleteItem/{itemCustomerUomId}")
    public String deleteItem(@PathVariable String itemCustomerUomId, RedirectAttributes redirectAttributes) {

        String[] parts    = itemCustomerUomId.split("_");
        String itemId     = parts[0];
        String customerId = parts[1];
        String itemUom    = parts[2];

        log.info("POST /items/deleteItem/{}", itemCustomerUomId);

        if (!itemsService.itemExists(itemCustomerUomId)) {
            log.warn("deleteItem() → item not found: itemCustomerUomId={}", itemCustomerUomId);
            redirectAttributes.addFlashAttribute("msg",
                    "Item " + itemId + " doesn't exist for customer: " + customerId + " & UOM: " + itemUom + "!");
            redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
            redirectAttributes.addFlashAttribute("textColor", "#ffffff");
        } else {
            itemsService.deleteItem(itemCustomerUomId);
            log.info("deleteItem() → item deleted: itemCustomerUomId={}", itemCustomerUomId);
            redirectAttributes.addFlashAttribute("msg",
                    "Item " + itemId + " deleted for customer: " + customerId + " & UOM: " + itemUom + "!");
            redirectAttributes.addFlashAttribute("bgColor", "#d1fae5");
            redirectAttributes.addFlashAttribute("textColor", "#45484d");
        }

        return "redirect:/items/";
    }
    
    @PostMapping("/items/getCustomerItems")
    @ResponseBody
    public List<Items> getCustomerItems(@RequestParam String customerId) {

        log.info("POST /items/getCustomerItems → customerId={}", customerId);

        List<Items> itemsListResult = itemsService.getCustomerItems(customerId);

        if (itemsListResult == null || itemsListResult.isEmpty()) {
            log.warn("getCustomerItems() returned no items → customerId={}", customerId);
        } else {
            log.info("getCustomerItems() success → customerId={}: {}",
                    customerId,
                    itemsListResult);
        }

        return itemsListResult;
    }
    
    
    
    
}