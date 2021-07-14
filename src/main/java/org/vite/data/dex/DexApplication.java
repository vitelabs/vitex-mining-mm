package org.vite.data.dex;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude={DataSourceAutoConfiguration.class})
public class DexApplication {

    public static void main(String[] args) {
        SpringApplication.run(DexApplication.class, args);
    }

}
