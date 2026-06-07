package dev.shipping.shipments.model;

import java.util.List;

// This wrapper class to recive the shipment-lines and address from create-shipment-page as a request method
public class CreateShipmentRequestWithLinesAndAddress {

    private List<ShipmentLines> lines;
    private Address deliveryAddress;
    
	public List<ShipmentLines> getLines() {
		return lines;
	}
	public void setLines(List<ShipmentLines> lines) {
		this.lines = lines;
	}
	public Address getDeliveryAddress() {
		return deliveryAddress;
	}
	public void setDeliveryAddress(Address deliveryAddress) {
		this.deliveryAddress = deliveryAddress;
	}
	@Override
	public String toString() {
		return "CreateShipmentRequestWithLinesAndAddress [lines=" + lines + ", deliveryAddress="
				+ deliveryAddress + "]";
	}
	
}