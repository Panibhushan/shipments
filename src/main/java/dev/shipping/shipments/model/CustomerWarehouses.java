package dev.shipping.shipments.model;


 

 
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
		// Format created_at: DD-MON-YYYY HH:MM:SS
		DateTimeFormatter createdFormat = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss");
		// set createdAt & modifiedAt date-time at the time of first record creation
		this.createdAt = LocalDateTime.now().format(createdFormat);
				
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
