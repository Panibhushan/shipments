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

	@Column(name = "created_at")
	private String createdAt;


	@PrePersist
	public void generateFields() {
		// Format  created_at: DD-MON-YYYY HH:MM:SS  12hrs with AM & PM format 
		DateTimeFormatter createdFormat = DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm:ss a", Locale.ENGLISH);
        ZonedDateTime istDateTime = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
		// set createdAt & modifiedAt date-time at the time of first record creation
		this.createdAt = istDateTime.format(createdFormat).toUpperCase(); 
 				
		// setting customerWarehouseId as combination of customerId & warehouseId
		this.customerWarehouseId = customerId+"_"+warehouseId;		
 	}
 
	public String getCreatedAt() {
		return createdAt;
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
