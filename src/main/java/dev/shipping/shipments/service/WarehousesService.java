package dev.shipping.shipments.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;

import dev.shipping.shipments.model.Address;
import dev.shipping.shipments.model.Items;
import dev.shipping.shipments.model.Warehouses;
import dev.shipping.shipments.repo.AddressRepository;
import dev.shipping.shipments.repo.WarehousesRepository;
import dev.shipping.shipments.utils.MyResourceUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

@Service
public class WarehousesService {

	@Autowired
	private EntityManager entityManager;

	private final WarehousesRepository warehousesRepo;
	private final AddressRepository addressRepo;


	public WarehousesService(WarehousesRepository warehousesRepo, AddressRepository addressRepo) {
		this.warehousesRepo = warehousesRepo;
		this.addressRepo = addressRepo;
	}

	// ─────────────────────────────────────────────
	// READ
	// ─────────────────────────────────────────────

	public List<Warehouses> getAllWarehouses() {
		return warehousesRepo.findAll();
	}

	public boolean warehouseExists(String warehouseId) {
		return warehousesRepo.findById(warehouseId).isPresent();
	}

	/**
	 * Populates all model attributes needed for the edit-warehouse page. Called
	 * only after confirming the warehouse exists.
	 */
	public void populateEditWarehouseModel(String warehouseId, Model model) {
		Warehouses warehouse = warehousesRepo.findById(warehouseId)
				.orElseThrow(() -> new RuntimeException("Warehouse not found: " + warehouseId));

		model.addAttribute("warehouse", warehouse);
		// model.addAttribute("formattedCreatedAt",
		// MyResourceUtils.getFormattedDateTime(warehouse.getCreatedAt()));
		// model.addAttribute("formattedModifiedAt",
		// MyResourceUtils.getFormattedDateTime(warehouse.getModifiedAt()));
		model.addAttribute("options", List.of("Active", "Disabled"));
		model.addAttribute("selectedStatus", warehouse.getWarehouseStatus());
	}

	// ─────────────────────────────────────────────
	// VALIDATION
	// ─────────────────────────────────────────────

	/**
	 * Validates fields when creating a new warehouse. Returns a list of error
	 * messages; empty list means no errors.
	 */
	public List<String> validateNewWarehouse(Warehouses warehouse) {
		List<String> errors = new ArrayList<>();
		String warehouseId = warehouse.getWarehouseId();
		String warehouseName = warehouse.getWarehouseName();
		String warehouseAddress = warehouse.getWarehouseAddress();

		// Warehouse ID checks
		if (!warehouseId.matches("^[a-zA-Z0-9-]+$")) {
			errors.add(
					"Warehouse ID can contain only alphabets, numbers and hyphens (other symbols, spaces, tabs, next-line characters are not allowed)!!");
		} else {
			if (warehouseId.length() < 3) {
				errors.add("Cannot use Warehouse ID as  \"" + warehouseId
						+ "\"\nWarehouse ID must be atleast 3 characters long !!");
			} else if (warehouseId.length() > 5) {
				errors.add("Cannot use Warehouse ID as \"" + warehouseId
						+ "\"\nWarehouse ID must be maximum 5 characters only !!");
			}
		}

		// Warehouse Name checks
		if (warehouseName.trim().length() < 5) {
			errors.add("Cannot update Warehouse Name to \"" + warehouseName
					+ "\"\nWarehouse Name must be atleast 5 characters long !!");
		} else if (warehouseName.trim().length() > 15) {
			errors.add("Cannot update Warehouse Name to \"" + warehouseName
					+ "\"\nWarehouse Name must be maximum 15 characters only !!");
		}

		// Warehouse Address checks
		if (warehouseAddress.trim().length() < 10) {
			errors.add("Cannot update Warehouse Address to \"" + warehouseAddress
					+ "\"\nWarehouse Address must be atleast 10 characters long !!");
		} else if (warehouseAddress.trim().length() > 50) {
			errors.add("Cannot update Warehouse Address to \"" + warehouseAddress
					+ "\"\nWarehouse Address must be maximum 50 characters only !!");
		}

		return errors;
	}

	/**
	 * Validates fields when updating an existing warehouse. Returns a list of error
	 * messages; empty list means no errors.
	 */
	public List<String> validateWarehouseUpdate(String warehouseName, String warehouseAddress) {
		List<String> errors = new ArrayList<>();

		// Warehouse Name checks
		if (warehouseName.trim().length() < 5) {
			errors.add("Cannot update Warehouse Name to \"" + warehouseName
					+ "\"\nWarehouse Name must be atleast 5 characters long !!");
		} else if (warehouseName.trim().length() > 15) {
			errors.add("Cannot update Warehouse Name to \"" + warehouseName
					+ "\"\nWarehouse Name must be maximum 15 characters only !!");
		}

		// Warehouse Address checks
		if (warehouseAddress.trim().length() < 10) {
			errors.add("Cannot update Warehouse Address to \"" + warehouseAddress
					+ "\"\nWarehouse Address must be atleast 10 characters long !!");
		} else if (warehouseAddress.trim().length() > 50) {
			errors.add("Cannot update Warehouse Address to \"" + warehouseAddress
					+ "\"\nWarehouse Address must be maximum 50 characters only !!");
		}

		return errors;
	}

	// ─────────────────────────────────────────────
	// WRITE
	// ─────────────────────────────────────────────

	@Transactional
	public String createWarehouse(Warehouses warehouse) {
		return warehousesRepo.save(warehouse).getWarehouseId();
	}

	@Transactional
	public void updateWarehouse(String warehouseId, String warehouseName, String warehouseStatus,
			String warehouseAddress) {
		Warehouses warehouse = warehousesRepo.findById(warehouseId)
				.orElseThrow(() -> new RuntimeException("Warehouse not found: " + warehouseId));

		warehouse.setWarehouseName(warehouseName);
		warehouse.setWarehouseAddress(warehouseAddress);
		warehouse.setWarehouseStatus(warehouseStatus);

		warehousesRepo.save(warehouse);
	}

	@Transactional
	public void deleteWarehouse(String warehouseId) {
		warehousesRepo.deleteById(warehouseId);
	}

	// Dynamically setting the conditions and running a custom query in service
	// instead of calling individual methods in Repo
	@Transactional
	public List<Warehouses> getWarehousesList(String warehouseId, String warehouseStatus) {

		StringBuilder query = new StringBuilder("SELECT w FROM Warehouses w");

		// Dynamically build WHERE clause
		List<String> conditions = new ArrayList<>();

		if (!warehouseId.equals("ALL"))
			conditions.add("w.warehouseId = :warehouseId");
		if (!warehouseStatus.equals("ALL"))
			conditions.add("w.warehouseStatus = :warehouseStatus");

		// Append WHERE + AND automatically
		if (!conditions.isEmpty()) {
			query.append(" WHERE ").append(String.join(" AND ", conditions));
		}

		// Create query
		TypedQuery<Warehouses> typedQuery = entityManager.createQuery(query.toString(), Warehouses.class);

		// Bind only non-null parameters
		if (!warehouseId.equals("ALL"))
			typedQuery.setParameter("warehouseId", warehouseId);
		if (!warehouseStatus.equals("ALL"))
			typedQuery.setParameter("warehouseStatus", warehouseStatus);

		List<Warehouses> resultList = typedQuery.getResultList();

		System.out.println("final Query: " + query.toString() + "\nresultList: " + resultList.toString());

		return resultList;
	}

	public String getWarehouseNameById(String warehouseId) {
		return warehousesRepo.findById(warehouseId).get().getWarehouseName();
	}

	public String createWarehouseWithAddress(String warehouseId, Address warehouseAddress) {

		System.out.println("ShipmentsService createAddress():: warehouseAddress: " + warehouseAddress.toString());

		Address address = new Address();

		address.setAddress1(warehouseAddress.getAddress1());
		address.setAddress2(warehouseAddress.getAddress2());
		address.setCountry(warehouseAddress.getCountry());
		address.setDistrict(warehouseAddress.getDistrict());
		address.setTaluk(warehouseAddress.getTaluk());
		address.setFirstName(warehouseAddress.getFirstName());
		address.setLastName(warehouseAddress.getLastName());
		address.setState(warehouseAddress.getState());
		address.setZipCode(warehouseAddress.getZipCode());

		String addressId = addressRepo.save(address).getAddressId();

		if (!(addressId.isEmpty()) && addressId != null) {
			Optional<Warehouses> optWarehouse = warehousesRepo.findById(warehouseId);

			if (optWarehouse.isPresent()) {
				Warehouses warehouse = optWarehouse.get();
				warehouse.setAddressId(addressId);
				warehousesRepo.save(warehouse);
			}

		}		
		
		return "WAREHOUSE_CREATED_SUCCESSFULLY_WITH_ID: "+warehouseId;
	}

}