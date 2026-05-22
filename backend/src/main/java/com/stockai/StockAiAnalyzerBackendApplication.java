package com.stockai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableScheduling
public class StockAiAnalyzerBackendApplication {

	@Value("${spring.datasource.username}")
	private String dbUser;

	@Value("${spring.datasource.password}")
	private String dbPassword;

	@Bean
	CommandLineRunner testConfig() {
		return args -> {
			System.out.println("DB USER = " + dbUser);
			System.out.println("DB PASS = [" + dbPassword + "]");
		};
	}
	public static void main(String[] args) {
		SpringApplication.run(StockAiAnalyzerBackendApplication.class, args);
	}

}
