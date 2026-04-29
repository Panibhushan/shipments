package dev.shipping.shipments.service;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

@Service
public class SnsPublisherService {

    private final SnsClient snsClient;

    // Replace with your SNS Topic ARN
    private final String topicArn =
            "arn:aws:sns:us-east-1:953158925887:shipment-status-updates";

    public SnsPublisherService(SnsClient snsClient) {
        this.snsClient = snsClient;
    }

    public void publishShipmentStatus(String shipmentId, String shipmentStatusAndDesc) {

        String message = "{"
                + "\"shipmentId\":\"" + shipmentId + "\","
                + "\"shipmentStatusAndDesc\":\"" + shipmentStatusAndDesc + "\""
                + "}";

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
