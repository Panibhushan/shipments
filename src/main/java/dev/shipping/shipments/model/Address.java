package dev.shipping.shipments.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import dev.shipping.shipments.config.AppProperties;
import dev.shipping.shipments.utils.MyCustomUtils;

@Entity
public class Address {

	@Id
	@Column(name = "address_id")
	private String addressId;

	@Column(name = "first_name")
	private String firstName;

	@Column(name = "last_name")
	private String lastName;

	@Column(name = "email")
	private String email;

	@Column(name = "phone")
	private String phone;

	@Column(name = "address1")
	private String address1;

	@Column(name = "address2")
	private String address2;

	@Column(name = "zipCode")
	private String zipCode;

	@Column(name = "district")
	private String district;

	@Column(name = "taluk")
	private String taluk;

	@Column(name = "state")
	private String state;

	@Column(name = "country")
	private String country = "INDIA";

	@Column(name = "country_shortform")
	private String countryShortform = "IN";

	@Column(name = "latitude")
	private Double  latitude;

	@Column(name = "longitude")
	private Double  longitude;

	// Store as LocalDateTime in DB (no timezone conversion by Hibernate)
	@Column(name = "created_at", updatable = false, columnDefinition = "DATETIME(6)")
	private LocalDateTime createdAt;

	// Store as LocalDateTime in DB (no timezone conversion by Hibernate)
	@Column(name = "modified_at", columnDefinition = "DATETIME(6)")
	private LocalDateTime modifiedAt;
	
	@Column(name = "address_hash", unique = true, nullable = false, length = 64)
	private String addressHash;

	@PrePersist
	public void generateFields() {

		// Generate shipment_id: SHIP + YYYYMMDDHHMMSS + millis (for uniqueness)
		DateTimeFormatter idFormat = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

		this.addressId = zipCode + "_" + district.toUpperCase() + "_" + state.toUpperCase() + "_"
				+ country.toUpperCase() + "_" + LocalDateTime.now().format(idFormat);

		// Get current IST time as LocalDateTime (no timezone stored, but value is IST)
		this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
		this.modifiedAt = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
		// this.addressHash = MyCustomUtils.generateAddressHash(address1, address2, country, countryShortform, district, email, firstName, lastName, phone, state, taluk, zipCode);
	}

	// Update modified date-time every time you make update to the table
	@PreUpdate
	public void setModifiedAt() {
		this.modifiedAt = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
	}

	// Returning string for createdAt in DD-MMM-YYYY hh:mm:ss AM/PM IST format
	public String getCreatedAt() {
		if (createdAt == null)
			return null;
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm:ss a");
		return createdAt.format(formatter).toUpperCase() + " IST";
	}

	// Returning string for modifiedAt in DD-MMM-YYYY hh:mm:ss AM/PM IST format
	public String getModifiedAt() {
		if (modifiedAt == null)
			return null;
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm:ss a");
		return modifiedAt.format(formatter).toUpperCase() + " IST";
	}

	public String getAddressId() {
		return addressId;
	}

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public String getAddress1() {
		return address1;
	}

	public void setAddress1(String address1) {
		this.address1 = address1;
	}

	public String getAddress2() {
		return address2;
	}

	public void setAddress2(String address2) {
		this.address2 = address2;
	}

	public String getZipCode() {
		return zipCode;
	}

	public void setZipCode(String zipCode) {
		this.zipCode = zipCode;
	}

	public String getDistrict() {
		return district;
	}

	public void setDistrict(String district) {
		this.district = district;
	}

	public String getTaluk() {
		return taluk;
	}

	public void setTaluk(String taluk) {
		this.taluk = taluk;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public String getCountry() {
		return country;
	}

	public void setCountry(String country) {
		this.country = country;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public String getCountryShortform() {
		return countryShortform;
	}

	public void setCountryShortform(String countryShortform) {
		this.countryShortform = countryShortform;
	}

	public double getLatitude() {
		return latitude;
	}

	public Double getLongitude() {
		return longitude;
	}

	public void setLongitude(Double longitude) {
		this.longitude = longitude;
	}

	public void setLatitude(Double latitude) {
		this.latitude = latitude;
	}

	public String getAddressHash() {
		return addressHash;
	}

	public void setAddressHash(String addressHash) {
		this.addressHash = addressHash;
	}

	@Override
	public String toString() {
		return "Address [addressId=" + addressId + ", firstName=" + firstName + ", lastName=" + lastName + ", email="
				+ email + ", phone=" + phone + ", address1=" + address1 + ", address2=" + address2 + ", zipCode="
				+ zipCode + ", district=" + district + ", taluk=" + taluk + ", state=" + state + ", country=" + country
				+ ", countryShortform=" + countryShortform + ", latitude=" + latitude + ", longitude=" + longitude
				+ ", createdAt=" + createdAt + ", modifiedAt=" + modifiedAt + ", addressHash=" + addressHash + "]";
	} 
		 
}