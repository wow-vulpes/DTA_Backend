package ru.dta.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.dta.check.domain.MaterialTypeDetector;
import ru.dta.check.domain.RecordChecker;

@Configuration(proxyBeanMethods = false)
public class CheckConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public MaterialTypeDetector materialTypeDetector() {
        return new MaterialTypeDetector();
    }

    @Bean
    public RecordChecker recordChecker(MaterialTypeDetector detector) {
        return new RecordChecker(detector);
    }
}
