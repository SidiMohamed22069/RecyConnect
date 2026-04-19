package com.project.RecyConnect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RecyConnectApplication {

	public static void main(String[] args) {
		SpringApplication.run(RecyConnectApplication.class, args);
	}

}
