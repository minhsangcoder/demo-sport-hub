package com.sporthub.common.security;

import com.sporthub.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import static org.assertj.core.api.Assertions.*;

class ErrorPrivacyTest {
    @Test void upstreamCredentialsAndBodiesAreAbsentFromResponseAndLogs() {
        var appender = new ListAppender<ILoggingEvent>(); appender.start();
        var logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class); logger.addAppender(appender);
        try {
            var ex = new IllegalStateException("Bearer private-token password=Secret OTP=123456",
                    new RuntimeException("private-user-phone"));
            var response = new GlobalExceptionHandler().handleGeneric(ex, new MockHttpServletRequest("GET", "/api/v1/users/me"));
            assertThat(response.getStatusCode().value()).isEqualTo(500);
            assertThat(response.getBody().getMessage()).doesNotContain("private-token", "Secret", "123456");
            assertThat(appender.list).isNotEmpty();
            appender.list.forEach(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain("private-token", "Secret", "123456", "private-user-phone");
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally { logger.detachAppender(appender); appender.stop(); }
    }
}
