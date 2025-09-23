package devs.lab.ssrf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SsrfGuardApplication {

    public static void main(String[] args) {
        SpringApplication.run(SsrfGuardApplication.class, args);
    }

}