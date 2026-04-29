package dev.shipping.shipments.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;

@Configuration
public class AwsSnsConfig {

	@Bean
	public SnsClient snsClient() {
		
		System.out.println("SNS BEAN CREATED ✔");
		
		return SnsClient.builder().region(Region.US_EAST_1).credentialsProvider(DefaultCredentialsProvider.create())
				.build();
	}

}