package dev.shipping.shipments.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;

import dev.shipping.shipments.model.Customers;
import dev.shipping.shipments.model.Inventory;
import dev.shipping.shipments.model.Items;
import dev.shipping.shipments.model.Warehouses;
import dev.shipping.shipments.repo.CustomersRepository;
import dev.shipping.shipments.repo.ItemsRepository;
import dev.shipping.shipments.repo.WarehousesRepository;
import dev.shipping.shipments.utils.MyResourceUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

@Service
public class ItemsService {

	@Autowired
	private EntityManager entityManager;

	private final ItemsRepository itemsRepo;
	private final CustomersRepository customersRepo;

	// List<String> itemUomsList = Arrays.asList("EACH", "MTR", "CMTR", "PAIR");

	public ItemsService(ItemsRepository itemsRepo, CustomersRepository customersRepo) {
		this.itemsRepo = itemsRepo;
		this.customersRepo = customersRepo;
	}

	// ─────────────────────────────────────────────
	// READ
	// ─────────────────────────────────────────────

	public boolean itemExists(String itemCustomerUomId) {
		System.out.println("ItemsService::itemExists(): itemCustomerUomId: " + itemCustomerUomId);
		return itemsRepo.findById(itemCustomerUomId).isPresent();
	}
	
	public Optional<Items> getItemById(String itemCustomerUomId) {
		System.out.println("ItemsService::getItemById(): itemCustomerUomId: " + itemCustomerUomId);
		return itemsRepo.findById(itemCustomerUomId);
	}

	public List<Items> getAllItems() {
		return itemsRepo.findAll();
	}

	@Transactional
	public void createItem(Items item) {
		itemsRepo.save(item);
	}

	public List<Customers> getActiveAndValidCustomers() {
		LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
		return customersRepo.findByCustomerStatusAndValidUpto("Active", startOfToday);
	}

	public void populateEditItemModel(String itemId, Model model) {
		Items item = itemsRepo.findById(itemId).orElseThrow(() -> new RuntimeException("Item not found: " + itemId));
				
		model.addAttribute("item", item);
		model.addAttribute("formattedCreatedAt", MyResourceUtils.getFormattedDateTime(item.getCreatedAt()));
		model.addAttribute("formattedModifiedAt", MyResourceUtils.getFormattedDateTime(item.getModifiedAt()));
		model.addAttribute("options", List.of("Active", "Disabled"));
		model.addAttribute("selectedItemStatus", item.getItemStatus());
		model.addAttribute("selectedUom", item.getItemUom());			
	}

	// VALIDATION //
	/*
	 * Validates fields when creating a new item. Returns a list of error
	 * messages; empty list means no errors.
	 */

	public List<String> validateNewItem(Items item, List<String> itemUomsList) {
		List<String> errors = new ArrayList<>();
		String itemId = item.getItemId();
		String itemUom = item.getItemUom();
		String itemDescription = item.getItemDescription();

		// Item ID checks
		if (itemId.matches(".*\\s.*")) {
			errors.add("Item ID cannot contain any whitespaces (spaces, tabs, next-line characters)!!");
		} else {
			if (itemId.length() < 3) {
				errors.add("Cannot use Item ID as  \"" + itemId + "\"\nItem ID must be atleast 3 characters long !!");
			} else if (itemId.length() > 5) {
				errors.add("Cannot use Item ID as \"" + itemId + "\"\nItem ID must be maximum 5 characters only !!");
			}
		}

		// Item UOM checks

		boolean uomExists = itemUomsList.contains(itemUom); // returns true
		if (!uomExists) {
			errors.add("Invalid Item UOM: " + itemUom + " \nPlease select cirrect UOM from the dropdown !!");
		}

		// Item Description checks
		if (itemDescription.trim().length() < 10) {
			errors.add("Cannot update Item Description to \"" + itemDescription
					+ "\"\nItem Description must be atleast 10 characters long !!");
		} else if (itemDescription.trim().length() > 50) {
			errors.add("Cannot update Item Description to \"" + itemDescription
					+ "\"\nItem Description must be maximum 50 characters only !!");
		}

		return errors;
	}

	@Transactional
	public void deleteItem(String itemCustomerUomId) {
		itemsRepo.deleteById(itemCustomerUomId);
	}

	public List<String> validateItemUpdate(String itemDescription, String itemStatus) {
		List<String> errors = new ArrayList<>();

		// Item Description checks
		if (itemDescription.trim().length() < 10) {
			errors.add("Cannot update Item Description to \"" + itemDescription
					+ "\"\nItem Description must be atleast 10 characters long !!");
		} else if (itemDescription.trim().length() > 50) {
			errors.add("Cannot update Item Description to \"" + itemDescription
					+ "\"\nItem Description must be maximum 50 characters only !!");
		}

		// Item UOM checks

		boolean itemStatusExists = List.of("Active", "Disabled").contains(itemStatus); // returns true
		if (!itemStatusExists) {
			errors.add("Invalid Item Status: " + itemStatus + " \nPlease select correct Status from the dropdown !!");
		}

		return errors;
	}

	/**
	 * Validates fields when updating an existing warehouse. Returns a list of error
	 * messages; empty list means no errors.
	 */

	// ───────────────────────────────────────────── // WRITE //

	@Transactional
	public void updateItem(String itemCustomerUomId, String itemDescription, String itemStatus, String itemUom) {
		Items item = itemsRepo.findById(itemCustomerUomId)
				.orElseThrow(() -> new RuntimeException("itemCustomerUomId not found: " + itemCustomerUomId));

		item.setItemDescription(itemDescription);
		item.setItemUom(itemUom);
		item.setItemStatus(itemStatus);

		itemsRepo.save(item);
	}

	public List<Items> getItemsByCustomer(String customerId) {
		return itemsRepo.findItemsByCustomer(customerId);
	}

	// Dynamically setting the conditions and running a custom query in service
	// instead of calling individual methods in Repo
	@Transactional
	public List<Items> getItemsList(String customerId, String itemId) {

		StringBuilder query = new StringBuilder("SELECT i FROM Items i");

		// Dynamically build WHERE clause
		List<String> conditions = new ArrayList<>();

		if (!customerId.equals("ALL"))
			conditions.add("i.customerId = :customerId");
		if (!itemId.equals("ALL"))
			conditions.add("i.itemId = :itemId");

		// Append WHERE + AND automatically
		if (!conditions.isEmpty()) {
			query.append(" WHERE ").append(String.join(" AND ", conditions));
		}

		// Create query
		TypedQuery<Items> typedQuery = entityManager.createQuery(query.toString(), Items.class);

		// Bind only non-null parameters
		if (!customerId.equals("ALL"))
			typedQuery.setParameter("customerId", customerId);
		if (!itemId.equals("ALL"))
			typedQuery.setParameter("itemId", itemId);

		List<Items> resultList = typedQuery.getResultList();

		System.out.println("final Query: " + query.toString() + "\nresultList: " + resultList.toString());

		return resultList;
	}

}