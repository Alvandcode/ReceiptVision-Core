package com.receiptvision.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ReceiptVisionApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReceiptVisionApplication.class, args);
    }
}
