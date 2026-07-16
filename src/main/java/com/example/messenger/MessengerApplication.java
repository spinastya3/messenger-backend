package com.example.messenger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = "com.example.messenger")
@EnableAsync
public class MessengerApplication {
  public static void main(String[] args) {
		SpringApplication.run(MessengerApplication.class, args);
	}

}
