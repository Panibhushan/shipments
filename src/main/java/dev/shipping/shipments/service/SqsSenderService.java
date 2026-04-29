package dev.shipping.shipments.service;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

@Service
public class SqsSenderService {

    private final SqsClient sqsClient;

    // Replace with your SNS Topic ARN
    private final String topicArn =
            "arn:aws:sns:us-east-1:953158925887:shipment-status-updates";

    public SqsSenderService(SqsClient sqsClient) {
        this.sqsClient = sqsClient;
    }

    public void sendShipmentStatus(String shipmentId, String shipmentStatusAndDesc) {

        String message = "{"
                + "\"shipmentId\":\"" + shipmentId + "\","
                + "\"shipmentStatusAndDesc\":\"" + shipmentStatusAndDesc + "\""
                + "}";

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

