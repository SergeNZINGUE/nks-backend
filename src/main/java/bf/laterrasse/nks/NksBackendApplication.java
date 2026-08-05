package bf.laterrasse.nks;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Night Karaoke Stars (NKS) — Backend API.
 * Restaurant La Terrasse (Ouagadougou, Burkina Faso) x Bright Group.
 *
 * Architecture : monolithe modulaire Spring Boot (ADR-07).
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class NksBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(NksBackendApplication.class, args);
    }
}
