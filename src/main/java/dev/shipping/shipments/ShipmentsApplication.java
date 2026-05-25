package dev.shipping.shipments;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ShipmentsApplication {
    public static void main(String[] args) {

        System.out.println("Working directory: " + System.getProperty("user.dir"));

        Dotenv dotenv = Dotenv.configure()
                .directory("./")
                .ignoreIfMissing()  // ← Will fetch data from .env when running in local, but when deployed in EC2, if the .env is not found then it won't crash if .env not found on EC2
                .load();

        dotenv.entries().forEach(entry ->
            System.setProperty(entry.getKey(), entry.getValue())
        );
        
        /*
		 * // Debug - print to confirm values loaded System.out.println("DB URL: " +
		 * System.getProperty("DB_URL_LOCAL")); System.out.println("DB USER: " +
		 * System.getProperty("DB_USERNAME_LOCAL")); System.out.println("DB PASSWORD: "
		 * + System.getProperty("DB_PASSWORD_LOCAL"));
		 */

        SpringApplication.run(ShipmentsApplication.class, args);
    }
}
