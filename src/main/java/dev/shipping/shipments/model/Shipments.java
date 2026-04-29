package dev.shipping.shipments.model;
 
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
public class Shipments {

    @Id
    @Column(name = "shipment_id")
    private String shipmentId;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "ship_status")
    private int shipStatus = 1100;

    @Column(name = "created_at")
    private String createdAt;

    @PrePersist
    public void generateFields() {

        // 1. Generate shipment_id: SHIP + YYYYMMDDHHMMSS + millis (for uniqueness)
        DateTimeFormatter idFormat =
                DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

        this.shipmentId = customerName+"_SHIPMENT_" + LocalDateTime.now().format(idFormat);

        // 2. Format created_at: DD-MON-YYYY HH:MM:SS
        DateTimeFormatter createdFormat =
                DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss");

        this.createdAt = LocalDateTime.now().format(createdFormat);
    }

    // Getters and Setters

    public String getShipmentId() {
        return shipmentId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
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

	@Override
	public String toString() {
		return "Shipments [shipmentId=" + shipmentId + ", customerName=" + customerName + ", shipStatus=" + shipStatus
				+ ", createdAt=" + createdAt + "]";
	}
        
    
}