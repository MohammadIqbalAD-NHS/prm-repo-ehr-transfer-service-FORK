package uk.nhs.prm.deductions.gp2gpmessagehandler.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class SqsClientSpringConfiguration {

    @Bean
    public SqsClient sqsClient() {
        return SqsClient.create();
    }
}
