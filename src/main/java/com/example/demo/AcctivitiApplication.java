package com.example.demo;

import org.activiti.spring.boot.SecurityAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@EnableAutoConfiguration(exclude={SecurityAutoConfiguration.class})
@SpringBootApplication

public class AcctivitiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AcctivitiApplication.class, args);
    }

}
