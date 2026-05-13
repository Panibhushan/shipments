package dev.shipping.shipments.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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

	@Column(name = "created_at")
	private String createdAt;

	@Column(name = "modified_at")
	private String modifiedAt;

	@PrePersist
	public void generateFields() {

		// Generate shipment_id: SHIP + YYYYMMDDHHMMSS + millis (for uniqueness)
		DateTimeFormatter idFormat = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

		// Get profile name from application.properties file and set it to Local or Cloud based on the profile being used
		String profile = AppProperties.activeProfile; // "devLocal", "devCloud", "dev", "prod" etc.

		if (profile.equals("devLocal")) {
			this.shipmentId = customerId + "_LOCAL_SHIPMENT_" + LocalDateTime.now().format(idFormat);
		} else {
			this.shipmentId = customerId + "_CLOUD_SHIPMENT_" + LocalDateTime.now().format(idFormat);
		}

		// Format created_at: DD-MON-YYYY HH:MM:SS
		DateTimeFormatter createdFormat = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss");

		
		// set createdAt & modifiedAt date-time at the time of first record creation
		this.createdAt = LocalDateTime.now().format(createdFormat);
		this.modifiedAt = LocalDateTime.now().format(createdFormat);
	}
	
	//Update modified date-time every time you make update to the table i.e., shipment status
    @PreUpdate
	public void setModifiedAt() {
		DateTimeFormatter createdFormat = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss");
		this.modifiedAt = LocalDateTime.now().format(createdFormat);
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

	public String getCreatedAt() {
		return createdAt;
	}

	public String getModifiedAt() {
		return modifiedAt;
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