package dev.shipping.shipments.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

@Entity
public class Items {
	@Id
	@Column(name = "item_customer_uom_id")
	private String itemCustomerUomId;
	
	@Column(name = "item_id")
	private String itemId;

	@Column(name = "customer_id")
	private String customerId;
	
	@Column(name = "item_description")
	private String itemDescription;

	@Column(name = "created_at")
	private String createdAt;

	@Column(name = "modified_at")
	private String modifiedAt;

	@Column(name = "item_status")
	private String itemStatus;
	
	@Column(name = "item_uom")
	private String itemUom;

	@PrePersist
	public void generateFields() {
		// Format created_at: DD-MON-YYYY HH:MM:SS
		DateTimeFormatter createdFormat = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss");
		// set createdAt & modifiedAt date-time at the time of first record creation
		this.createdAt = LocalDateTime.now().format(createdFormat);
		this.modifiedAt = LocalDateTime.now().format(createdFormat);
		
		this.itemCustomerUomId = itemId+"_"+customerId+"_"+itemUom;
	}

	// Update modified date-time every time you make update to the table i.e., shipment status
	@PreUpdate
	public void setModifiedAt() {
		DateTimeFormatter createdFormat = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss");
		this.modifiedAt = LocalDateTime.now().format(createdFormat);
	}
	 
	public String getItemCustomerUomId() {
		return itemCustomerUomId;
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

	public String getItemDescription() {
		return itemDescription;
	}

	public void setItemDescription(String itemDescription) {
		this.itemDescription = itemDescription;
	}

	public String getItemStatus() {
		return itemStatus;
	}

	public void setItemStatus(String itemStatus) {
		this.itemStatus = itemStatus;
	}

	public String getItemUom() {
		return itemUom;
	}

	public void setItemUom(String itemUom) {
		this.itemUom = itemUom;
	}

	@Override
	public String toString() {
		return "Items [itemCustomerUomId=" + itemCustomerUomId + ", itemId=" + itemId + ", customerId=" + customerId
				+ ", itemDescription=" + itemDescription + ", createdAt=" + createdAt + ", modifiedAt=" + modifiedAt
				+ ", itemStatus=" + itemStatus + ", itemUom=" + itemUom + "]";
	}

  	
}
