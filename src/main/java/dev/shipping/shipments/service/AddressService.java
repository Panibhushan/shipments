
package dev.shipping.shipments.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;

import dev.shipping.shipments.model.Address;
import dev.shipping.shipments.model.Items;
import dev.shipping.shipments.model.Warehouses;
import dev.shipping.shipments.repo.AddressRepository;
import dev.shipping.shipments.repo.WarehousesRepository;
import dev.shipping.shipments.utils.MyResourceUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import utils.MyCustomUtils;

@Service
public class AddressService {

	@Autowired
	private EntityManager entityManager;

	private final AddressRepository addressRepo;

	public AddressService(AddressRepository addressRepo) {
		this.addressRepo = addressRepo;
	}

	public boolean addressExists(String addressId) {
		return addressRepo.findById(addressId).isPresent();
	}

	/*
	 * @Transactional public String updateAddress(Address address) { boolean
	 * addressExists = addressExists(address.getAddressId());
	 * 
	 * Address newAddress = addressRepo.findById(address.getAddressId()).get();
	 * 
	 * if (!(newAddress == null)) { newAddress.setAddress1(address.getAddress1());
	 * newAddress.setAddress2(address.getAddress2());
	 * newAddress.setFirstName(address.getFirstName());
	 * newAddress.setLastName(address.getLastName());
	 * newAddress.setEmail(address.getEmail());
	 * newAddress.setPhone(address.getPhone());
	 * newAddress.setState(address.getState());
	 * newAddress.setZipCode(address.getZipCode());
	 * newAddress.setCountry(address.getCountry());
	 * newAddress.setDistrict(address.getDistrict());
	 * newAddress.setTaluk(address.getTaluk());
	 * 
	 * 
	 * addressRepo.save(newAddress); return "SUCCESS"; }
	 * 
	 * return "FAILED"; }
	 */

	public String createAddress(String addrHash, Address address) {
		Address createdAddress = null ;
		
		Optional<Address> existingAddress = addressRepo.getByAddrHash(addrHash);
		
		System.out.println("AddressService createAddress(addrHash, address):: "+addrHash+" ---- "+address.toString()+"\nexistingAddress.isPresent(): "+existingAddress.isPresent());
		
		
		if(existingAddress.isPresent()) {
			System.out.println("AddressService.createAddress():: Address already exists, hence return the addressId");
			return existingAddress.get().getAddressId();
		}
			
		try {
			address.setAddressHash(addrHash);
			System.out.println("Before getCoordinates");

			Double[] coordinates = MyCustomUtils.getCoordinates(address.getZipCode());

			System.out.println("After getCoordinates");
			
			if(coordinates.length != 0) {				
				
				address.setLatitude(coordinates[0]);
				address.setLongitude(coordinates[1]);
				
				createdAddress = addressRepo.save(address);
			}
		}catch(Exception e) {
			System.out.println("Exception at createAddress(): "+e.getMessage()+"\n"+e.getStackTrace());
			return e.getMessage();
		}
		
		return createdAddress.getAddressId();
	}
	
	
	@Transactional
	public String updateAddress(String warehouseId, Address address) {
		boolean addressExists = addressExists(address.getAddressId());

		Address newAddress = addressRepo.findById(address.getAddressId()).get();
		
		Map<String, Address> addrMap = MyCustomUtils.buildAddressFields(address);

		String addrHash = addrMap.keySet().iterator().next();
		Address newlyBuiltAddress = addrMap.getOrDefault(addrHash, new Address());
		
		String addressId =  createAddress(addrHash, newlyBuiltAddress) ;
		
		if (!(addressId == null)) {	 
			return "SUCCESS";
		}
		
		return "FAILED";
	}

}
