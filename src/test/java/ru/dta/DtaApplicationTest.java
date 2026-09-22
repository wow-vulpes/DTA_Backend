package ru.dta;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(useMainMethod = SpringBootTest.UseMainMethod.ALWAYS)
class DtaApplicationTest {

    @Test
    void applicationContextStarts(@Autowired ConfigurableApplicationContext context) {
        assertThat(context.isActive()).isTrue();
    }
}
