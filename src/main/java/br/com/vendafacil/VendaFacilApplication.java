package br.com.vendafacil;

import br.com.vendafacil.shared.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class VendaFacilApplication {
  public static void main(String[] args) {
    SpringApplication.run(VendaFacilApplication.class, args);
  }
}
