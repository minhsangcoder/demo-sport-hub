package com.sporthub.payment;

import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporthub.payment.repository.PaymentRepository;
import com.sporthub.payment.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class PeerSettlementIsolationTest {
  @Test
  void peerHandoffNeverEntersLegacyFacilityOrPlatformSettlement() {
    var repo = mock(PaymentRepository.class);
    var ledger = mock(FinancialLedger.class);
    var booking = mock(BookingReceiptClient.class);
    var json = new ObjectMapper();
    var service = new TransferSettlementService(repo, ledger, booking, json, mock(PlatformTransactionManager.class));
    var event = json.createObjectNode().put("eventType", "transfer.handoff.completed").put("eventVersion", 1).put("producer", "transfer-service");
    event.putObject("payload").put("protocol", "PEER_RESALE_V1").put("moneyClass", "REAL");
    service.completed(event);
    verifyNoInteractions(repo, ledger, booking);
  }
}
