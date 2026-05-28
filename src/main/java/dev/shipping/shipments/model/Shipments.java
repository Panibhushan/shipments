package dev.shipping.shipments.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import dev.shipping.shipments.config.AppProperties;

@Entity
public class Shipments {

	@Id
	@Column(name = "shipment_id")
	private String shipmentId;

	@Column(name = "customer_id")
	private String customerId;

	@Column(name = "warehouse_id")
	private String warehouseId;

	@Column(name = "ship_status")
	private int shipStatus = 1100;

	// Store as LocalDateTime in DB (no timezone conversion by Hibernate)
	@Column(name = "created_at", updatable = false, columnDefinition = "DATETIME(6)")
	private LocalDateTime createdAt;

	// Store as LocalDateTime in DB (no timezone conversion by Hibernate)
	@Column(name = "modified_at", columnDefinition = "DATETIME(6)")
	private LocalDateTime modifiedAt;

	@PrePersist
	public void generateFields() {

		// Generate shipment_id: SHIP + YYYYMMDDHHMMSS + millis (for uniqueness)
		DateTimeFormatter idFormat = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

		// Get profile name from application.properties file and set it to Local or
		// Cloud based on the profile being used
		String profile = AppProperties.activeProfile; // "devLocal", "devCloud", "dev", "prod" etc.

		if (profile.equals("devLocal")) {
			this.shipmentId = customerId + "_LOCAL_SHIPMENT_" + LocalDateTime.now().format(idFormat);
		} else {
			this.shipmentId = customerId + "_CLOUD_SHIPMENT_" + LocalDateTime.now().format(idFormat);
		}

		// Get current IST time as LocalDateTime (no timezone stored, but value is IST)
		this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
		this.modifiedAt = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
	}

	// Update modified date-time every time you make update to the table i.e.,
	// shipment status
	@PreUpdate
	public void setModifiedAt() {
		this.modifiedAt = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
	}

	// Getters and Setters

	public String getShipmentId() {
		return shipmentId;
	}

	public String getCustomerId() {
		return customerId;
	}

	public void setCustomerId(String customerId) {
		this.customerId = customerId;
	}

	public int getShipStatus() {
		return shipStatus;
	}

	public void setShipStatus(int shipStatus) {
		this.shipStatus = shipStatus;
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

	public String getWarehouseId() {
		return warehouseId;
	}

	public void setWarehouseId(String warehouseId) {
		this.warehouseId = warehouseId;
	}

	@Override
	public String toString() {
		return "Shipments [shipmentId=" + shipmentId + ", customerId=" + customerId + ", warehouseId=" + warehouseId
				+ ", shipStatus=" + shipStatus + ", createdAt=" + createdAt + ", modifiedAt=" + modifiedAt + "]";
	}

}