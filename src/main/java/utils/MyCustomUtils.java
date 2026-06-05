package utils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import dev.shipping.shipments.model.Address;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper; 

public class MyCustomUtils {


	public static Address buildAddressFields(Address warehouseAddress) {

		Address address = new Address();

		try {
			address.setAddress1(warehouseAddress.getAddress1());
			address.setAddress2(warehouseAddress.getAddress2());
			address.setCountry(warehouseAddress.getCountry());
			address.setDistrict(warehouseAddress.getDistrict());
			address.setTaluk(warehouseAddress.getTaluk());
			address.setFirstName(warehouseAddress.getFirstName());
			address.setLastName(warehouseAddress.getLastName());
			address.setState(warehouseAddress.getState());
			address.setZipCode(warehouseAddress.getZipCode());
			address.setEmail(warehouseAddress.getEmail());
			address.setPhone(warehouseAddress.getPhone());

		} catch (Exception e) {
			System.out.println("Exception occurred while buildingAddress in MyCustomUtils: " + e.getMessage());
		}

		return address;

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

		MyCustomUtils.calculateDistance(0, 0, 0, 0);

		double distance = MyCustomUtils.calculateDistance(12.9716, 77.5946, // Bangalore
				13.0827, 80.2707 // Chennai
		);

		System.out.println(distance + " km");

		return formattedAddress;
	}

	private final static ObjectMapper mapper = new ObjectMapper();

	public static double[] getCoordinates(String pincode) throws Exception {
		
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
			return new double[] { 0, 0 };
		}

		JsonNode first = root.get(0);

		return new double[] { first.get("lat").asDouble(), first.get("lon").asDouble() };
	}
	
	public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {

		final int EARTH_RADIUS_KM = 6371;

		double latDistance = Math.toRadians(lat2 - lat1);
		double lonDistance = Math.toRadians(lon2 - lon1);

		double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) + Math.cos(Math.toRadians(lat1))
				* Math.cos(Math.toRadians(lat2)) * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

		return EARTH_RADIUS_KM * c;
	}

}
