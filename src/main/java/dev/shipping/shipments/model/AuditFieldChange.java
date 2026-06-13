package dev.shipping.shipments.model;

public class AuditFieldChange {

	private String field;
	private String oldValue;
	private String newValue;

	public AuditFieldChange() {
	}

	public AuditFieldChange(String field, String oldValue, String newValue) {
		this.field = field;
		this.oldValue = oldValue == null ? "" : oldValue;
		this.newValue = newValue == null ? "" : newValue;
	}

	public String getField() {
		return field;
	}

	public void setField(String field) {
		this.field = field;
	}

	public String getOldValue() {
		return oldValue;
	}

	public void setOldValue(String oldValue) {
		this.oldValue = oldValue;
	}

	public String getNewValue() {
		return newValue;
	}

	public void setNewValue(String newValue) {
		this.newValue = newValue;
	}
}
