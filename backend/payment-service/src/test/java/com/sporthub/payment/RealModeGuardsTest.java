package com.sporthub.payment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sporthub.common.money.CollectionRuntime;
import com.sporthub.payment.provider.*;
import com.sporthub.payment.service.PayosChannels;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;

class RealModeGuardsTest {
  @Test
  void productionCombinedWithDemoNeverRegistersSimulationProvider() {
    for (String profile : new String[] {"prod", "production", "live"}) {
      try (var context = new AnnotationConfigApplicationContext()) {
        var environment = new MockEnvironment();
        environment.setActiveProfiles(profile, "demo", "local");
        context.setEnvironment(environment);
        context.registerBean(PayosClient.class, () -> mock(PayosClient.class));
        context.registerBean(PayosChannels.class, () -> mock(PayosChannels.class));
        context.register(PayosPaymentProvider.class, DemoPaymentProvider.class);
        context.refresh();
        assertThat(context.getBeansOfType(DemoPaymentProvider.class)).isEmpty();
        assertThat(context.getBean(PaymentProvider.class).name()).isEqualTo("PAYOS");
        assertThatThrownBy(() -> new CollectionRuntime("DEMO", environment).requireDemo())
            .isInstanceOf(com.sporthub.common.exception.ForbiddenException.class);
      }
    }
  }

  @Test
  void realModeCannotBeDowngradedAndProductionCannotUseLocalMockProvider() {
    var environment = new MockEnvironment();
    environment.setActiveProfiles("local");
    assertThatThrownBy(() -> new CollectionRuntime("REAL", environment).requireDemo())
        .isInstanceOf(com.sporthub.common.exception.ForbiddenException.class);
    environment.setActiveProfiles("prod", "demo");
    assertThatThrownBy(
            () ->
                new PayosClient(
                    "http://localhost:12345",
                    "https://app.example",
                    "https://gateway.example",
                    true,
                    environment))
        .isInstanceOf(IllegalStateException.class);
  }
}
