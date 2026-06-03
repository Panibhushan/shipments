package dev.shipping.shipments.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

@Service
public class DynamoDbService {

	private final DynamoDbClient dynamoDbClient;

	public DynamoDbService(DynamoDbClient dynamoDbClient) {
		this.dynamoDbClient = dynamoDbClient;
	}

	public List<Map<String, String>> getShipmentAudiyByShipmentIdAsPartitionKey(String tableName,
			String singleShipmentId) {

		// ShipmentId is partition-key in DDB, while singleShipmentId is the id of the
		// shipment that we need the audit details
		QueryRequest request = QueryRequest.builder().tableName(tableName)
				.keyConditionExpression("ShipmentId = :singleShipmentId").expressionAttributeValues(
						Map.of(":singleShipmentId", AttributeValue.builder().s(singleShipmentId).build()))
				.build();

		QueryResponse response = dynamoDbClient.query(request);

		// DynamoDb will send data in List<Map<String, AttributeValue>> format
		List<Map<String, AttributeValue>> responseFromDynamoDb = response.items();

		System.out.println("responseFromDynamoDb inside Service: " + responseFromDynamoDb);

		// We convert it to List<Map<String, String>> for easy readabiliy and for
		// passing to HTML
		List<Map<String, String>> singleShipmentAuditDetails = new ArrayList<>();

		for (Map<String, AttributeValue> audit : responseFromDynamoDb) {

			Map<String, String> map = new HashMap<>();

			map.put("shipmentId", audit.get("ShipmentId").s());
			map.put("eventDate", audit.get("EventDate").s());
			map.put("shipmentStatus", audit.get("ShipmentStatus").s());
			map.put("comment", audit.get("Comments").s());

			singleShipmentAuditDetails.add(map);
		}

		return singleShipmentAuditDetails;
	}

}