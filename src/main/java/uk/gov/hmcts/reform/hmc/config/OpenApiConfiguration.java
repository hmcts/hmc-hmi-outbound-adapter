package uk.gov.hmcts.reform.hmc.config;


import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
            .info(new Info().title("CFT Hearing Service")
                .version("v0.0.1")
                .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
            .info(new Info().contact(new Contact()
                .name("CDM")
                .url("https://tools.hmcts.net/confluence/display/RCCD/Reform%3A+Case+Management+Home")
                .email("corecasedatateam@hmcts.net")))
            .externalDocs(new ExternalDocumentation()
                .description("README")
                .url("https://github.com/hmcts/hmc-cft-hearing-service#readme"));
    }

}
