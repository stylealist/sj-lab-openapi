package com.example.openapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class SjLabOpenApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SjLabOpenApiApplication.class, args);
    }
}
