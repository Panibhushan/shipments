
package dev.shipping.shipments.service;

import java.util.ArrayList;
import java.util.List;
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

	@Transactional
	public String updateAddress(Address address) {
		boolean addressExists = addressExists(address.getAddressId());

		Address newAddress = addressRepo.findById(address.getAddressId()).get();
		
		if (!(newAddress == null)) {			
			newAddress.setAddress1(address.getAddress1());
			newAddress.setAddress2(address.getAddress2());			
			newAddress.setFirstName(address.getFirstName());
			newAddress.setLastName(address.getLastName());
			newAddress.setEmail(address.getEmail());
			newAddress.setPhone(address.getPhone());
			/* Not updating these fields as they are made read-only in the form
			 * newAddress.setState(address.getState());
			 * newAddress.setZipCode(address.getZipCode());
			 * newAddress.setCountry(address.getCountry());
			 * newAddress.setDistrict(address.getDistrict());
			 * newAddress.setTaluk(address.getTaluk());
			 */
			
			addressRepo.save(newAddress);
			return "SUCCESS";
		}
		
		return "FAILED";
	}

	public String createAddress(Address address) {
		Address createdAddress = null ;
			
		try {
			
			System.out.println("Before getCoordinates");

			double[] coordinates = MyCustomUtils.getCoordinates(address.getZipCode());

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

}
