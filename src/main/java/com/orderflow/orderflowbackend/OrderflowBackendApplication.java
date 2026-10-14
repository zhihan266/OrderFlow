package com.orderflow.orderflowbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class OrderflowBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderflowBackendApplication.class, args);
    }

}
