package io.praporets.controlplane;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ControlPlaneApplication {
    static void main(String[] args) {
        SpringApplication.run(ControlPlaneApplication.class, args);
    }
}
