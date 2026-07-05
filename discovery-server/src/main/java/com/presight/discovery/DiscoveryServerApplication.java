package com.presight.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Central service registry. Order, Inventory and the Gateway all
 * register here so they can discover each other by logical name
 * instead of hard-coded host:port pairs. This is what lets us
 * scale any service horizontally in Kubernetes without touching
 * the other services' configuration.
 */
@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServerApplication.class, args);
    }
}
