package dev.shipping.shipments;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ShipmentsApplication {
    public static void main(String[] args) {

        // Print the current working directory at runtime
        // Local  → project root (e.g., /home/user/projects/shipments)
        // EC2    → /opt/myapp (set via WorkingDirectory in systemd service)
        System.out.println("Working directory: " + System.getProperty("user.dir"));

        // Read the dotenv directory from JVM system property "-Ddotenv.directory"
        // This is passed as a JVM argument in the systemd service file on EC2:
        //     ExecStart=/usr/bin/java -Ddotenv.directory=/opt/myapp -jar app.jar
        //
        // If the property is NOT set (i.e., running locally),
        // it defaults to "./" which resolves to the project root
        // where the local .env file exists
        //
        // Local  → "./"           → looks for .env in project root
        // EC2    → "/opt/myapp"   → looks for .env in /opt/myapp/.env
        String dotenvDir = System.getProperty("dotenv.directory", "./");
        System.out.println(">>> Dotenv directory: " + dotenvDir);

        // Configure and load the .env file from the resolved directory
        // ignoreIfMissing() → prevents app crash if .env file is not found
        //                      useful on EC2 if .env wasn't created yet,
        //                      or if environment variables are set another way
        Dotenv dotenv = Dotenv.configure()
                .directory(dotenvDir)
                .ignoreIfMissing()
                .load();

        // Temporary debug logs to verify correct values are loaded from .env
        // Helps confirm the app is reading the right file at runtime
        // Remove these lines once everything is working correctly
        System.out.println(">>> DB_URL:  " + dotenv.get("DB_URL_CLOUD",      "NOT FOUND"));
        System.out.println(">>> DB_USER: " + dotenv.get("DB_USERNAME_CLOUD", "NOT FOUND"));
        System.out.println(">>> DB_PASS: " + dotenv.get("DB_PASSWORD_CLOUD", "NOT FOUND"));

        // Load all .env key-value pairs into Java System properties
        // This makes them available to Spring Boot as ${KEY} placeholders
        // in application.properties / application-devCloud.properties
        // e.g., DB_URL_CLOUD=jdbc:mysql://... becomes ${DB_URL_CLOUD}
        dotenv.entries().forEach(entry ->
            System.setProperty(entry.getKey(), entry.getValue())
        );

        // Start the Spring Boot application
        SpringApplication.run(ShipmentsApplication.class, args);
    }
}
