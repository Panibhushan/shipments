
package dev.shipping.shipments.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import dev.shipping.shipments.model.Address;
import dev.shipping.shipments.model.CreateShipmentRequestWithLinesAndAddress;
import dev.shipping.shipments.model.Warehouses;
import dev.shipping.shipments.service.AddressService;
import dev.shipping.shipments.service.WarehousesService;
import dev.shipping.shipments.utils.MyResourceUtils;

@Controller

public class AddressController {

	private final AddressService addressService;

	public AddressController(AddressService addressService ) {
		this.addressService = addressService;
	}

	@PostMapping("/address/updateAddress")
	@ResponseBody
	public String updateAddress(@RequestParam String warehouseId, @RequestBody Address address) {

		System.out.println("/address/updateAddress: warehouseId, address: " + warehouseId +" ---- "+address.toString());

		String addressUpdateStatus = addressService.updateAddress(warehouseId, address);

		return addressUpdateStatus;
	}
}
