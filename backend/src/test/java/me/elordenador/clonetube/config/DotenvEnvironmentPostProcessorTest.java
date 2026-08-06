package me.elordenador.clonetube.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

class DotenvEnvironmentPostProcessorTest {

    @Test
    void parsesDockerStyleEnvFile() {
        Properties values = DotenvEnvironmentPostProcessor.parse(List.of(
                "# comentario",
                "export SPRING_DATASOURCE_URL=jdbc:mariadb://localhost:3306/clonetube",
                "JWT_SECRET=\"clave con comillas\"",
                "SQS_QUEUE_URL=",
                "AWS_BUCKET_NAME = clonetube"
        ));

        assertThat(values)
                .containsEntry("SPRING_DATASOURCE_URL", "jdbc:mariadb://localhost:3306/clonetube")
                .containsEntry("JWT_SECRET", "clave con comillas")
                .containsEntry("SQS_QUEUE_URL", "")
                .containsEntry("AWS_BUCKET_NAME", "clonetube");
    }
}
