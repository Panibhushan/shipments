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

	@Column(name = "created_at")
	private String createdAt;

	@Column(name = "modified_at")
	private String modifiedAt;

	@Column(name = "quantity")
	private String quantity;
	
	@Column(name = "item_uom")
	private String itemUom;

	@PrePersist
	public void generateFields() {
		// Format modified_at & created_at: DD-MON-YYYY HH:MM:SS  12hrs with AM & PM format 
		DateTimeFormatter createdFormat = DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm:ss a", Locale.ENGLISH);
        ZonedDateTime istDateTime = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
		// set createdAt & modifiedAt date-time at the time of first record creation
		this.createdAt = istDateTime.format(createdFormat).toUpperCase(); 
		this.modifiedAt = istDateTime.format(createdFormat).toUpperCase();
		
		this.itemCustomerUomWarehouseId = itemId+"_"+customerId+"_"+itemUom+"_"+warehouseId;
	}

	// Update modified date-time every time you make update to the table i.e., shipment status
	@PreUpdate
	public void setModifiedAt() {
		// Format created_at: DD-MON-YYYY HH:MM:SS  12hrs with AM & PM format 
		DateTimeFormatter createdFormat = DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm:ss a", Locale.ENGLISH);
        ZonedDateTime istDateTime = ZonedDateTime.now(ZoneId.of("Asia/Kolkata")); 
		this.modifiedAt = istDateTime.format(createdFormat).toUpperCase();
	}
 
	public String getCreatedAt() {
		return createdAt;
	}

	public String getModifiedAt() {
		return modifiedAt;
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

	public String getQuantity() {
		return quantity;
	}

	public void setQuantity(String quantity) {
		this.quantity = quantity;
	}

	@Override
	public String toString() {
		return "Inventory [itemCustomerUomWarehouseId=" + itemCustomerUomWarehouseId + ", itemId=" + itemId
				+ ", customerId=" + customerId + ", warehouseId=" + warehouseId + ", createdAt=" + createdAt
				+ ", modifiedAt=" + modifiedAt + ", quantity=" + quantity + ", itemUom=" + itemUom + "]";
	}
  	
}
