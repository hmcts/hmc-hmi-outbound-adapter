package uk.gov.hmcts.reform.hmc.client.futurehearing;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.SneakyThrows;
import org.apache.tomcat.jni.Local;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.format.annotation.DateTimeFormat;
import uk.gov.hmcts.reform.hmc.ApplicationParams;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

public class HearingManagementInterfaceRequestInterceptor implements RequestInterceptor {

    private final ApplicationParams applicationParams;
    private final Clock clock;

    public HearingManagementInterfaceRequestInterceptor(ApplicationParams applicationParams,
                                                        @Qualifier("utcClock") Clock clock) {
        this.applicationParams = applicationParams;
        this.clock = clock;
    }

    @Override
    public void apply(RequestTemplate template) {
        String formatted =  DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
            .format(Instant.now(clock).atZone(ZoneId.of("UTC")));

        template.header("Source-System", applicationParams.getSourceSystem());
        template.header("Destination-System", applicationParams.getDestinationSystem());
        template.header("Request-Created-At", formatted);
        template.header("transactionIdHMCTS", String.valueOf(UUID.randomUUID()));
    }
}
