package dev.shipping.shipments.model;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

@Entity
public class Inventory {
	@Id
	@Column(name = "item_customer_uom_warehouse_id")
	private String itemCustomerUomWarehouseId;

	@Column(name = "item_id")
	private String itemId;

	@Column(name = "customer_id")
	private String customerId;

	@Column(name = "warehouse_id")
	private String warehouseId;

	// Store as LocalDateTime in DB (no timezone conversion by Hibernate)
	@Column(name = "created_at", updatable = false, columnDefinition = "DATETIME(6)")
	private LocalDateTime createdAt;

	// Store as LocalDateTime in DB (no timezone conversion by Hibernate)
	@Column(name = "modified_at", columnDefinition = "DATETIME(6)")
	private LocalDateTime modifiedAt;

	@Column(name = "quantity")
	private int quantity;

	@Column(name = "allocated_quantity")
	private int allocatedQuantity = 0;
	
	@Column(name = "available_quantity")
	private int availableQuantity;
	
	@Column(name = "item_uom")
	private String itemUom;

	@PrePersist
	public void generateFields() {
		// Get current IST time as LocalDateTime (no timezone stored, but value is IST)
		this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
		this.modifiedAt = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
		this.itemCustomerUomWarehouseId = itemId + "_" + customerId + "_" + itemUom + "_" + warehouseId;
		this.availableQuantity=  quantity - allocatedQuantity;
	}

	// Update modified date-time every time you make update to the inventory
	@PreUpdate
	public void setModifiedAtAndAvailableQuantity() {
		this.modifiedAt = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
		this.availableQuantity =  quantity - allocatedQuantity;
	}

	// Returning string for createdAt in DD-MMM-YYYY hh:mm:ss AM/PM IST format
	public String getCreatedAt() {
		if (createdAt == null)
			return null;
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm:ss a");
		return createdAt.format(formatter).toUpperCase() + " IST";
	}

	// Returning string for modifiedAt in DD-MMM-YYYY hh:mm:ss AM/PM IST format
	public String getModifiedAt() {
		if (modifiedAt == null)
			return null;
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm:ss a");
		return modifiedAt.format(formatter).toUpperCase() + " IST";
	}

	public String getItemId() {
		return itemId;
	}

	public void setItemId(String itemId) {
		this.itemId = itemId;
	}

	public String getCustomerId() {
		return customerId;
	}

	public void setCustomerId(String customerId) {
		this.customerId = customerId;
	}

	public String getItemUom() {
		return itemUom;
	}

	public void setItemUom(String itemUom) {
		this.itemUom = itemUom;
	}

	public String getItemCustomerUomWarehouseId() {
		return itemCustomerUomWarehouseId;
	}

	public String getWarehouseId() {
		return warehouseId;
	}

	public void setWarehouseId(String warehouseId) {
		this.warehouseId = warehouseId;
	}

	public int getQuantity() {
		return quantity;
	}

	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}

	public int getAllocatedQuantity() {
		return allocatedQuantity;
	}

	public void setAllocatedQuantity(int allocatedQuantity) {
		this.allocatedQuantity = allocatedQuantity;
	}

	public int getAvailableQuantity() {
		return availableQuantity;
	}

	@Override
	public String toString() {
		return "Inventory [itemCustomerUomWarehouseId=" + itemCustomerUomWarehouseId + ", itemId=" + itemId
				+ ", customerId=" + customerId + ", warehouseId=" + warehouseId + ", createdAt=" + createdAt
				+ ", modifiedAt=" + modifiedAt + ", quantity=" + quantity + ", allocatedQuantity=" + allocatedQuantity
				+ ", availableQuantity=" + availableQuantity + ", itemUom=" + itemUom + "]";
	}

}
