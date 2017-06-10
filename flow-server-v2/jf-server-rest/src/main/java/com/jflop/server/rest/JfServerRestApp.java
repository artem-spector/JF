package com.jflop.server.rest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main class of sping boot
 *
 * @author artem
 *         Date: 7/2/16
 */
@SpringBootApplication(scanBasePackages = {"com.jflop.server"})
public class JfServerRestApp {

    public static void main(String[] args) {
        SpringApplication.run(JfServerRestApp.class, args);
    }
}
