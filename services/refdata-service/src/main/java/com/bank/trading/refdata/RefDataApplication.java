package com.bank.trading.refdata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;

@SpringBootApplication
@EnableEurekaClient
public class RefDataApplication {

    public static void main(String[] args) {
        SpringApplication.run(RefDataApplication.class, args);
    }
}
