package com.krizaka.sovkora.automationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Isolated Spring Boot application for executing approved automation jobs. */
@SpringBootApplication
public class AutomationServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(AutomationServiceApplication.class, args);
  }
}
