package dev.shipping.shipments.model;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.format.annotation.DateTimeFormat;

import com.fasterxml.jackson.annotation.JsonFormat;

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

	// Store as LocalDateTime in DB (no timezone conversion by Hibernate)
	@Column(name = "created_at", updatable = false, columnDefinition = "DATETIME(6)")
	private LocalDateTime createdAt;

	// Store as LocalDateTime in DB (no timezone conversion by Hibernate)
	@Column(name = "modified_at", columnDefinition = "DATETIME(6)")
	private LocalDateTime modifiedAt;

	@Column(name = "item_status")
	private String itemStatus;

	@Column(name = "item_uom")
	private String itemUom;

	@PrePersist
	public void generateFields() {
		// Get current IST time as LocalDateTime (no timezone stored, but value is IST)
		this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
		this.modifiedAt = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
		this.itemCustomerUomId = itemId + "_" + customerId + "_" + itemUom;
	}

	// Update modified date-time every time you make update to the item
	@PreUpdate
	public void setModifiedAt() {
		this.modifiedAt = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
	}

	public String getItemCustomerUomId() {
		return itemCustomerUomId;
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
