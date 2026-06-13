package dev.shipping.shipments.service;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class SnsPublisherService {

    private final SnsClient snsClient;

    // Replace with your SNS Topic ARN
    private final String topicArn =
            "arn:aws:sns:us-east-1:953158925887:shipment-status-updates";

    public SnsPublisherService(SnsClient snsClient) {
        this.snsClient = snsClient;
    }

    public void publishShipmentStatus(String shipmentId, String shipmentStatusAndDesc, String comment) {

    	//Building a JSON object to send to SQS
    	ObjectMapper mapper = new ObjectMapper();

    	ObjectNode json = mapper.createObjectNode();
    	json.put("shipmentId", shipmentId);
    	json.put("shipmentStatusAndDesc", shipmentStatusAndDesc);
		json.put("comment", comment);
		
		String message = mapper.writeValueAsString(json);
		
		publishToSns(message);

    }     
    
    public void publishToSns(String message) {		

        PublishRequest request = PublishRequest.builder()
                .topicArn(topicArn)
                .message(message)
                .build();
        
        try {
            PublishResponse response = snsClient.publish(request);

            System.out.println("SNS PUBLISH SUCCESS ✔");
            System.out.println("MessageId: " + response.messageId());

        } catch (Exception e) {
            System.out.println("SNS PUBLISH FAILED ❌");
            e.printStackTrace();
        } 
    }
    
    
}
