package me.elordenador.clonetube;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ClonetubeApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClonetubeApplication.class, args);
    }

}
