package dev.shipping.shipments.model;

public class InventoryCheckResult {

	private String customerId;
	private String itemId;
	private String warehouseId;
	private String itemUom;
	private int quantity;
	private int allocatedQuantity;
	private int availableQuantity;
	private int requestedQuantity;
	private String stockStatus;
	private String itemCustomerUomWarehouseId;

	// All args constructor
	public InventoryCheckResult(String customerId, String itemId, String warehouseId, String itemUom, int quantity,
			int allocatedQuantity, int availableQuantity, int requestedQuantity, String stockStatus, String itemCustomerUomWarehouseId) {
		this.customerId = customerId;
		this.itemId = itemId;
		this.warehouseId = warehouseId;
		this.itemUom = itemUom;
		this.quantity = quantity;
		this.allocatedQuantity = allocatedQuantity;
		this.availableQuantity = availableQuantity;
		this.requestedQuantity = requestedQuantity;
		this.stockStatus = stockStatus;
		this.itemCustomerUomWarehouseId= itemCustomerUomWarehouseId;
	}

	// Getters
	
	public String getCustomerId() {
		return customerId;
	}
	public String getItemId() {
		return itemId;
	}

	public String getWarehouseId() {
		return warehouseId;
	}

	public String getItemUom() {
		return itemUom;
	}

	public int getQuantity() {
		return quantity;
	}

	public int getAllocatedQuantity() {
		return allocatedQuantity;
	}

	public int getAvailableQuantity() {
		return availableQuantity;
	}

	public int getRequestedQuantity() {
		return requestedQuantity;
	}

	public String getStockStatus() {
		return stockStatus;
	}

	public String getItemCustomerUomWarehouseId() {
		return itemCustomerUomWarehouseId;
	}

	@Override
	public String toString() {
		return "InventoryCheckResult [customerId=" + customerId + ", itemId=" + itemId + ", warehouseId=" + warehouseId
				+ ", itemUom=" + itemUom + ", quantity=" + quantity + ", allocatedQuantity=" + allocatedQuantity
				+ ", availableQuantity=" + availableQuantity + ", requestedQuantity=" + requestedQuantity
				+ ", stockStatus=" + stockStatus + ", itemCustomerUomWarehouseId=" + itemCustomerUomWarehouseId + "]";
	} 
	 

}