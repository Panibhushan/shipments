package dev.shipping.shipments.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import dev.shipping.shipments.config.AppProperties;

@Entity
public class Audits {

	@Id
	@Column(name = "audit_id", updatable = false )
	private String auditId;

	@Column(name = "entity_type")
	private String entityType;

	@Column(name = "entity_id")
	private String entityId;

	@Column(name = "action_type")
	private String actionType;
	
	@Lob // Normal String Varchar will only accept 255 chars, which may not be enough for audit, so setting this as Lob (Large Object) & Medium text which can store 16MB 
	@Column(name = "action",  columnDefinition = "MEDIUMTEXT") 
	private String action;
	
	@Column(name = "action_by")
	private String actionBy;
		
	// Store as LocalDateTime in DB (no timezone conversion by Hibernate)
	@Column(name = "created_at", updatable = false, columnDefinition = "DATETIME(6)")
	private LocalDateTime createdAt; 

	@PrePersist
	public void generateFields() {

		// Generate shipment_id: SHIP + YYYYMMDDHHMMSS + millis (for uniqueness)
		DateTimeFormatter idFormat = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
 
		// Get current IST time as LocalDateTime (no timezone stored, but value is IST)
		this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Kolkata")); 
		this.auditId = entityId+"_"+entityType+"_"+LocalDateTime.now(ZoneId.of("Asia/Kolkata")); 
	}

	public String getAuditId() {
		return auditId;
	}

	public String getEntityType() {
		return entityType;
	}

	public void setEntityType(String entityType) {
		this.entityType = entityType;
	}

	public String getEntityId() {
		return entityId;
	}

	public void setEntityId(String entityId) {
		this.entityId = entityId;
	}

	public String getActionType() {
		return actionType;
	}

	public void setActionType(String actionType) {
		this.actionType = actionType;
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public String getActionBy() {
		return actionBy;
	} 
	public void setActionBy(String actionBy) {
		this.actionBy = actionBy;
	}

	// Returning string for createdAt in DD-MMM-YYYY hh:mm:ss AM/PM IST format
		public String getCreatedAt() {
			if (createdAt == null)
				return null;
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm:ss a");
			return createdAt.format(formatter).toUpperCase() + " IST";
		}
		
	@Override
	public String toString() {
		return "Audit [auditId=" + auditId + ", entityType=" + entityType + ", entityId=" + entityId + ", actionType="
				+ actionType + ", action=" + action + ", actionBy=" + actionBy + ", createdAt=" + createdAt + "]";
	} 
	
	
}