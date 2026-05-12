package dev.shipping.shipments.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

	@Column(name = "created_at")
	private String createdAt;

	@Column(name = "modified_at")
	private String modifiedAt;

	@Column(name = "warehouse_status")
	private String warehouseStatus;

	@PrePersist
	public void generateFields() {
		// Format created_at: DD-MON-YYYY HH:MM:SS
		DateTimeFormatter createdFormat = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss");
		// set createdAt & modifiedAt date-time at the time of first record creation
		this.createdAt = LocalDateTime.now().format(createdFormat);
		this.modifiedAt = LocalDateTime.now().format(createdFormat);
	}

	// Update modified date-time every time you make update to the table i.e.,
	// shipment status
	@PreUpdate
	public void setModifiedAt() {
		DateTimeFormatter createdFormat = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss");
		this.modifiedAt = LocalDateTime.now().format(createdFormat);
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

	public String getCreatedAt() {
		return createdAt;
	}

	public String getModifiedAt() {
		return modifiedAt;
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
