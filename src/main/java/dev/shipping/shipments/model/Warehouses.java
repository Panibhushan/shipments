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
public class Warehouses {
	@Id
	@Column(name = "warehouse_id")
	private String warehouseId;

	@Column(name = "warehouse_name")
	private String warehouseName;

	@Column(name = "warehouse_address")
	private String warehouseAddress;

	// Store as LocalDateTime in DB (no timezone conversion by Hibernate)
	@Column(name = "created_at", updatable = false, columnDefinition = "DATETIME(6)")
	private LocalDateTime createdAt;
	
	// Store as LocalDateTime in DB (no timezone conversion by Hibernate)
	@Column(name = "modified_at", columnDefinition = "DATETIME(6)")
	private LocalDateTime modifiedAt;

	@Column(name = "warehouse_status")
	private String warehouseStatus;

	@PrePersist
	public void generateFields() {

		System.out.println("Warehouses.class:::LocalDateTime.now(ZoneId.of(\"Asia/Kolkata\"): "
				+ LocalDateTime.now(ZoneId.of("Asia/Kolkata")));

		// Get current IST time as LocalDateTime (no timezone stored, but value is IST)
		this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
		this.modifiedAt = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
	}

	// Update modified date-time every time you make update to the warehouse
	@PreUpdate
	public void setModifiedAt() {
		this.modifiedAt = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
	}

	public String getWarehouseId() {
		return warehouseId;
	}

	public void setWarehouseId(String warehouseId) {
		this.warehouseId = warehouseId;
	}

	public String getWarehouseName() {
		return warehouseName;
	}

	public void setWarehouseName(String warehouseName) {
		this.warehouseName = warehouseName;
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

	public String getWarehouseStatus() {
		return warehouseStatus;
	}

	public void setWarehouseStatus(String warehouseStatus) {
		this.warehouseStatus = warehouseStatus;
	}

	public String getWarehouseAddress() {
		return warehouseAddress;
	}

	public void setWarehouseAddress(String warehouseAddress) {
		this.warehouseAddress = warehouseAddress;
	}

	@Override
	public String toString() {
		return "Warehouses [warehouseId=" + warehouseId + ", warehouseName=" + warehouseName + ", warehouseAddress="
				+ warehouseAddress + ", createdAt=" + createdAt + ", modifiedAt=" + modifiedAt + ", warehouseStatus="
				+ warehouseStatus + "]";
	}

}
