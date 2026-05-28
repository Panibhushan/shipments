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
public class CustomerWarehouses {

	@Id
	@Column(name = "customer_warehouse_id")
	private String customerWarehouseId;

	@Column(name = "warehouse_id")
	private String warehouseId;

	@Column(name = "customer_id")
	private String customerId;

	// Store as LocalDateTime in DB (no timezone conversion by Hibernate)
	@Column(name = "created_at", updatable = false, columnDefinition = "DATETIME(6)")
	private LocalDateTime createdAt;

	@PrePersist
	public void generateFields() {
		// Get current IST time as LocalDateTime (no timezone stored, but value is IST)
		this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));

		// setting customerWarehouseId as combination of customerId & warehouseId
		this.customerWarehouseId = customerId + "_" + warehouseId;
	}

	// Returning string for createdAt in DD-MMM-YYYY hh:mm:ss AM/PM IST format
	public String getCreatedAt() {
		if (createdAt == null)
			return null;
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm:ss a");
		return createdAt.format(formatter).toUpperCase() + " IST";
	}

	public String getCustomerWarehouseId() {
		return customerWarehouseId;
	}

	public void setCustomerWarehouseId(String customerWarehouseId) {
		this.customerWarehouseId = customerWarehouseId;
	}

	public String getWarehouseId() {
		return warehouseId;
	}

	public void setWarehouseId(String warehouseId) {
		this.warehouseId = warehouseId;
	}

	public String getCustomerId() {
		return customerId;
	}

	public void setCustomerId(String customerId) {
		this.customerId = customerId;
	}

	@Override
	public String toString() {
		return "CustomerWarehouse [customerWarehouseId=" + customerWarehouseId + ", warehouseId=" + warehouseId
				+ ", customerId=" + customerId + ", createdAt=" + createdAt + "]";
	}
}
