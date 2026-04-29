package dev.shipping.shipments.model;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;

@Entity
public class Customers {

	@Id
	@Column(name = "customer_id")
	private String customerId;

	@Column(name = "customer_name")
	private String customerName;

	@Column(name = "created_at")
	private String createdAt;

	@PrePersist
	public void generateFields() {
		// Format created_at: DD-MON-YYYY HH:MM:SS
		DateTimeFormatter createdFormat = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss");
		this.createdAt = LocalDateTime.now().format(createdFormat);
	}

	public String getCustomerId() {
		return customerId;
	}

	public void setCustomerId(String customerId) {
		this.customerId = customerId;
	}

	public String getCustomerName() {
		return customerName;
	}

	public void setCustomerName(String customerName) {
		this.customerName = customerName;
	}

	public String getCreatedAt() {
		return createdAt;
	}

	@Override
	public String toString() {
		return "Customers [customerId=" + customerId + ", customerName=" + customerName + ", createdAt=" + createdAt
				+ "]";
	}

}
