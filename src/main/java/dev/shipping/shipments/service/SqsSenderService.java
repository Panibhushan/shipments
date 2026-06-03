package dev.shipping.shipments.service;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class SqsSenderService {

    private final SqsClient sqsClient;

    // Replace with your SNS Topic ARN
    private final String topicArn =
            "arn:aws:sns:us-east-1:953158925887:shipment-status-updates";

    public SqsSenderService(SqsClient sqsClient) {
        this.sqsClient = sqsClient;
    }

    public void sendShipmentStatus(String shipmentId, String shipmentStatusAndDesc, String comment) {

		/*
		 * String message = "{" + "\"shipmentId\":\"" + shipmentId + "\"," +
		 * "\"shipmentStatusAndDesc\":\"" + shipmentStatusAndDesc + "\"," +
		 * "\"comment\":\"" + comment + "\"" + "}";
		 */
    	
    	//Building a JSON object to send to SQS
    	ObjectMapper mapper = new ObjectMapper();

    	ObjectNode json = mapper.createObjectNode();
    	json.put("shipmentId", shipmentId);
    	json.put("shipmentStatusAndDesc", shipmentStatusAndDesc);
    		json.put("comment", comment); 

    	String message = mapper.writeValueAsString(json);

        String queueUrl = "https://sqs.us-east-1.amazonaws.com/953158925887/ShippingSQS";

        SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(message)
                .build();
 
        
        try {
        	SendMessageResponse  response = sqsClient.sendMessage(request);

            System.out.println("SQS PUBLISH SUCCESS ✔");
            System.out.println("MessageId: " + response.messageId());

        } catch (Exception e) {
            System.out.println("SQS PUBLISH FAILED ❌");
            e.printStackTrace();
        } 
    }
}

