package utils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.codec.digest.DigestUtils;

import dev.shipping.shipments.model.Address;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper; 

public class MyCustomUtils {


	public static Map<String, Address> buildAddressFields(Address warehouseAddress) {

		Map<String, Address> addrMap = new HashMap<>();  
		
		Address address = new Address();

		try { 
			
			String address1 = warehouseAddress.getAddress1();
			String address2 = warehouseAddress.getAddress2();
			String country = warehouseAddress.getCountry();
			String district = warehouseAddress.getDistrict();
			String taluk = warehouseAddress.getTaluk();
			String firstName = warehouseAddress.getFirstName();
			String lastName = warehouseAddress.getLastName();
			String state = warehouseAddress.getState();
			String zipCode = warehouseAddress.getZipCode();
			String email = warehouseAddress.getEmail();
			String phone = warehouseAddress.getPhone();
			
			
			address.setAddress1(address1);
			address.setAddress2(address2);
			address.setCountry(country);
			address.setDistrict(district);
			address.setTaluk(taluk);
			address.setFirstName(firstName);
			address.setLastName(lastName);
			address.setState(state);
			address.setZipCode(zipCode);
			address.setEmail(email);
			address.setPhone(phone);
			
			String hash = MyCustomUtils.generateAddressHash(address1, address2, country, "IN", district, email, firstName, lastName, phone, state, taluk, zipCode);
			
			addrMap.put(hash, address);

		} catch (Exception e) {
			System.out.println("Exception occurred while buildingAddress in MyCustomUtils: " + e.getMessage());
		}

		return addrMap;

	}

	public static String getFormattedAddress(Address address) {
		StringBuilder sb = new StringBuilder();

		sb.append(address.getFirstName()).append(", ").append(address.getLastName()).append("<br />")
				.append(address.getAddress1());

		if (address.getAddress2() != null && !address.getAddress2().isBlank()) {
			sb.append("<br />").append(address.getAddress2());
		}

		sb.append("<br />").append(address.getTaluk()).append(", ").append(address.getDistrict()).append(", ")
				.append(address.getState()).append(", ").append(address.getCountry()).append(" - ")
				.append(address.getZipCode());
		sb.append("<br />");
		sb.append("Ph: " + address.getPhone());
		sb.append("<br />");
		sb.append("Email: " + address.getEmail());

		String formattedAddress = sb.toString();

		MyCustomUtils.calculateDistance(0.0, 0.0, 0.0, 0.0);

		double distance = MyCustomUtils.calculateDistance(12.9716, 77.5946, // Bangalore
				13.0827, 80.2707 // Chennai
		);

		System.out.println(distance + " km");

		return formattedAddress;
	}

	private final static ObjectMapper mapper = new ObjectMapper();

	public static Double[] getCoordinates(String pincode) throws Exception {
		
		System.out.println("Calling getCoordinates(pincode): "+pincode);

		String url = "https://nominatim.openstreetmap.org/search" + "?postalcode=" + pincode + "&country=India"
				+ "&format=jsonv2";

		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent", "MySpringBootApp/1.0")
				.GET().build();
		
		System.out.println("request :" +request.uri());

		HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

		System.out.println("response :" +response.body());
		
		JsonNode root = mapper.readTree(response.body());

		if (!root.isArray() || root.isEmpty()) {
			return new Double[] { 0.0, 0.0 };
		}

		JsonNode first = root.get(0);

		return new Double[] { first.get("lat").asDouble(), first.get("lon").asDouble() };
	}
	
	public static Double calculateDistance(Double lat1, Double lon1, Double lat2, Double lon2) {

		final int EARTH_RADIUS_KM = 6371;

		Double latDistance = Math.toRadians(lat2 - lat1);
		Double lonDistance = Math.toRadians(lon2 - lon1);

		Double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) + Math.cos(Math.toRadians(lat1))
				* Math.cos(Math.toRadians(lat2)) * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

		Double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

		return EARTH_RADIUS_KM * c;
	}

	public static String generateAddressHash(String address1, String address2, String country, String countryShortform,
			String district, String email, String firstName, String lastName, String phone, String state, String taluk, String zipCode) {
		
		String normalized =
	            (address1+ "|" +address2+ "|" +country+ "|" +countryShortform+ "|" +district+ "|" +email+ "|" +firstName+ "|" +lastName+ "|" +phone+ "|" +state+ "|" +taluk+ "|" +zipCode)
	            .trim()
	            .toUpperCase();

		String hash = DigestUtils.sha256Hex(normalized);
		return hash;
	}

}
