package com.engineering.challenge.solution;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class RedisKafkaSolutionApplication {

	public static void main(String[] args) {
		SpringApplication.run(RedisKafkaSolutionApplication.class, args);
	}

}
