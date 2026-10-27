package com.sporthub.booking.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporthub.booking.repository.BookingRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

class PeerUsageTimeoutSelectionTest {
  @Test
  void waitingForPresentPartiesDoesNotHideDueAttemptsBehindTheBatchLimit() {
    try (var pg = new PostgreSQLContainer<>("postgres:16-alpine")) {
      pg.start();
      var jdbc = new JdbcTemplate(new DriverManagerDataSource(
          pg.getJdbcUrl(), pg.getUsername(), pg.getPassword()));
      jdbc.execute("CREATE TABLE peer_usage_attempts(acquisition_id UUID PRIMARY KEY,"
          + "phase TEXT,starts_at TIMESTAMPTZ,ends_at TIMESTAMPTZ,pay_by TIMESTAMPTZ)");
      jdbc.execute("CREATE TABLE peer_attendance(acquisition_id UUID,observed_at TIMESTAMPTZ)");
      var repo = mock(BookingRepository.class);
      when(repo.jdbc()).thenReturn(jdbc);
      var store = mock(PeerUsageStore.class);
      var commands = mock(PeerUsageCommands.class);
      var now = Instant.parse("2026-10-27T09:00:00Z");
      var worker = new PeerUsageTimeouts(repo, store, commands, mock(PeerHandoffs.class),
          Clock.fixed(now, ZoneOffset.UTC), new ObjectMapper());
      var postponed = new ArrayList<UUID>();
      for (int i = 0; i < 100; i++) {
        postponed.add(attempt(jdbc, store, "DISPUTED", now.minusSeconds(600),
            now.plusSeconds(3000), now.minusSeconds(900), now.minusSeconds(600)));
      }
      UUID absent = attempt(jdbc, store, "DISPUTED", now.minusSeconds(300),
          now.plusSeconds(3000), now.minusSeconds(900), null);
      UUID expired = attempt(jdbc, store, "WAITING_PAYMENT", now.plusSeconds(600),
          now.plusSeconds(4200), now, null);
      UUID ended = attempt(jdbc, store, "PAYMENT_DECLARED", now.minusSeconds(300),
          now, now.minusSeconds(900), now.minusSeconds(301));
      UUID latePresence = attempt(jdbc, store, "DISPUTED", now.minusSeconds(300),
          now.plusSeconds(3000), now.minusSeconds(900), now.minusSeconds(299));
      UUID atStart = attempt(jdbc, store, "PAYMENT_DECLARED", now,
          now.plusSeconds(3600), now.minusSeconds(300), null);
      UUID future = attempt(jdbc, store, "WAITING_PAYMENT", now.plusSeconds(600),
          now.plusSeconds(4200), now.plusSeconds(1), null);

      worker.tick();

      for (UUID id : List.of(absent, expired, ended, latePresence, atStart))
        verify(commands).run(eq(id), any(), eq("PEER_USAGE_TIMEOUT"), anyString(), any(), any());
      verifyNoMoreInteractions(commands);
      for (UUID id : postponed) verify(store, never()).attempt(id);
      verify(store, never()).attempt(future);
    }
  }

  private UUID attempt(JdbcTemplate jdbc, PeerUsageStore store, String phase,
      Instant start, Instant end, Instant payBy, Instant presence) {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO peer_usage_attempts VALUES(?,?,?,?,?)", id, phase,
        Timestamp.from(start), Timestamp.from(end), Timestamp.from(payBy));
    if (presence != null) jdbc.update("INSERT INTO peer_attendance VALUES(?,?)",
        id, Timestamp.from(presence));
    when(store.attempt(id)).thenReturn(new PeerUsageStore.Attempt(
        id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
        UUID.randomUUID(), UUID.randomUUID(), BigDecimal.valueOf(100000), "VND", start,
        end, UUID.randomUUID(), "bank", "policy", "terms", payBy, phase, "NONE", 0));
    return id;
  }
}
