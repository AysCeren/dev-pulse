package com.devpulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// DevPulse has its own users table, so Boot's generated default user must not exist.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class DevpulseApplication {

	public static void main(String[] args) {
		SpringApplication.run(DevpulseApplication.class, args);
	}

}
