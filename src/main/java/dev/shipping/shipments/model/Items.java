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

	@Column(name = "created_at", updatable = false)
	@DateTimeFormat(pattern = "dd-MMM-yyyy hh:mm:ss a z")
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MMM-yyyy hh:mm:ss a z", locale = "en")
	private ZonedDateTime createdAt;

	@Column(name = "modified_at")
	@DateTimeFormat(pattern = "dd-MMM-yyyy hh:mm:ss a z")
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MMM-yyyy hh:mm:ss a z", locale = "en")
	private ZonedDateTime modifiedAt;

	@Column(name = "item_status")
	private String itemStatus;

	@Column(name = "item_uom")
	private String itemUom;

	@PrePersist
	public void generateFields() {
		ZonedDateTime istDateTime = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
		this.createdAt = istDateTime;
		this.modifiedAt = istDateTime;
		this.itemCustomerUomId = itemId + "_" + customerId + "_" + itemUom;
	}

	// Update modified date-time every time you make update to the table i.e.,
	// shipment status
	@PreUpdate
	public void setModifiedAt() {
		// Format modified_at & created_at: DD-MON-YYYY HH:MM:SS 12hrs with AM & PM and timezone visible as IST 
		ZonedDateTime istDateTime = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
		this.modifiedAt = istDateTime;
	}

	public String getItemCustomerUomId() {
		return itemCustomerUomId;
	}

	public ZonedDateTime getCreatedAt() {
		return createdAt;
	}

	public ZonedDateTime getModifiedAt() {
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
