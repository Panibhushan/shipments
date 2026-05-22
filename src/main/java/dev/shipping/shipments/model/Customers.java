package dev.shipping.shipments.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

@Entity
public class Customers {

	@Id
	@Column(name = "customer_id")
	private String customerId;

	@Column(name = "customer_name")
	private String customerName;

	@Column(name = "created_at")
	private String createdAt;

	@Column(name = "modified_at")
	private String modifiedAt;

	@Column(name = "customer_status")
	private String customerStatus;

	// This field stays for DB mapping
	@Column(name = "valid_upto")
	private LocalDateTime validUpto;

	/*
	 * @JsonFormat(pattern = "dd-MMM-yyyy HH:mm:ss")
	 * 
	 * @Column(name = "valid_upto") private LocalDateTime validUpto;
	 * 
	 * @JsonFormat(pattern = "dd-MMM-yyyy HH:mm:ss") public LocalDateTime
	 * getValidUpto() { return validUpto; } * 
	 * 
	 * 
	 * //Updating valid up to to always have time as 00.00.00
	 * 
	 * @JsonProperty("validUpto") public void setValidUpto(String dateStr) {
	 * LocalDate date = LocalDate.parse(dateStr,
	 * DateTimeFormatter.ofPattern("yyyy-MM-dd")); this.validUpto =
	 * date.atStartOfDay(); // always 00:00:00 }
	 */

	@PrePersist
	public void generateFields() {
		// Format created_at: DD-MON-YYYY HH:MM:SS  12hrs with AM & PM format 
		DateTimeFormatter createdFormat = DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm:ss a", Locale.ENGLISH);
        ZonedDateTime istDateTime = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
		// set createdAt & modifiedAt date-time at the time of first record creation
		this.createdAt = istDateTime.format(createdFormat).toUpperCase(); 
		this.modifiedAt = istDateTime.format(createdFormat).toUpperCase();
	}

	// Update modified date-time every time you make update to the table i.e.,
	// shipment status
	@PreUpdate
	public void setModifiedAt() {
		DateTimeFormatter createdFormat = DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm:ss a", Locale.ENGLISH);
        ZonedDateTime istDateTime = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
		// set createdAt & modifiedAt date-time at the time of first record creation
		this.modifiedAt = istDateTime.format(createdFormat).toUpperCase();
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

	public String getModifiedAt() {
		return modifiedAt;
	}

	public String getCustomerStatus() {
		return customerStatus;
	}

	public void setCustomerStatus(String customerStatus) {
		this.customerStatus = customerStatus;
	}
	
	// Setter — accepts yyyy-MM-dd from HTML
	@JsonProperty("validUpto")
	public void setValidUpto(String dateStr) {
		LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
		this.validUpto = date.atStartOfDay();
	}

	// Getter — returns formatted string directly to HTML/API
	@JsonProperty("validUpto")
	public String getValidUpto() {
		if (validUpto == null)
			return null;
		return validUpto.format(DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss"));
		// returns "15-May-2026 00:00:00"
	}

	@Override
	public String toString() {
		return "Customers [customerId=" + customerId + ", customerName=" + customerName + ", createdAt=" + createdAt
				+ ", modifiedAt=" + modifiedAt + ", customerStatus=" + customerStatus + ", validUpto=" + validUpto
				+ "]";
	}
 

}
