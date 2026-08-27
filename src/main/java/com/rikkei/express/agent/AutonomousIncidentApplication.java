package com.rikkei.express.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.rikkei.express")
public class AutonomousIncidentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutonomousIncidentApplication.class, args);
    }
}