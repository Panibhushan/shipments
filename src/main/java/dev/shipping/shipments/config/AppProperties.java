package dev.shipping.shipments.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AppProperties {

    public static String activeProfile;

    @Value("${spring.profiles.active:default}")
    public void setActiveProfile(String value) {
        activeProfile = value; // Spring injects → stored as static
    }
}