package com.hospital.spd;

import com.hospital.spd.deployment.DeploymentDatabaseCommand;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Arrays;
import java.util.Optional;

@SpringBootApplication
@EnableScheduling
public class SpdApplication {

    public static void main(String[] args) {
        Optional<String> deploymentCommand = Arrays.stream(args)
                .filter(argument -> argument.startsWith("--spd.command="))
                .map(argument -> argument.substring("--spd.command=".length()))
                .findFirst();
        if (deploymentCommand.isPresent()) {
            int exitCode = new DeploymentDatabaseCommand()
                    .execute(deploymentCommand.get(), System.getenv(), System.out);
            if (exitCode != 0) {
                System.exit(exitCode);
            }
            return;
        }
        SpringApplication.run(SpdApplication.class, args);
    }
}
