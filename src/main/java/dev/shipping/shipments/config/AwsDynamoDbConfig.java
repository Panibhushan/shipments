package dev.shipping.shipments.config;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class AwsDynamoDbConfig {

	@Bean
	public DynamoDbClient dynamoDbClient() {
		
		System.out.println("DynamoDb BEAN CREATED ✔");

	    return DynamoDbClient.builder()
	            .region(Region.US_EAST_1)
	            .credentialsProvider(DefaultCredentialsProvider.create())
	            .build();
	}
}
