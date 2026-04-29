package dev.shipping.shipments.config;
 
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class AwsSqsConfig {

	@Bean
	public SqsClient sqsClient() {
		
		System.out.println("SQS BEAN CREATED ✔");

	    return SqsClient.builder()
	            .region(Region.US_EAST_1)
	            .credentialsProvider(DefaultCredentialsProvider.create())
	            .build();
	}
	
}