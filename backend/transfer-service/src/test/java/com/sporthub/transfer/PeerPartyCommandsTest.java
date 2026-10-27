package com.sporthub.transfer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sporthub.common.exception.*;
import com.sporthub.common.money.*;
import com.sporthub.transfer.service.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;

class PeerPartyCommandsTest extends PeerEvidenceTest {
  PeerPartyCommands parties;
  PeerPartyCommandStore store;
  AtomicBoolean loseReply, beforeWrite;
  AtomicInteger committed;
  Map<String, JsonNode> results;

  @BeforeEach
  void partyFixture() {
    store = new PeerPartyCommandStore(repo, clock, manager);
    parties =
        new PeerPartyCommands(
            new PeerEvidenceAccess(repo, authority),
            evidence,
            store,
            authority,
            projection,
            json,
            clock);
    loseReply = new AtomicBoolean();
    beforeWrite = new AtomicBoolean();
    committed = new AtomicInteger();
    results = new HashMap<>();
    when(authority.party(any(), anyString(), any(), any(), any(), any(), anyString()))
        .thenAnswer(
            call -> {
              noTransaction();
              UUID id = call.getArgument(0), actor = call.getArgument(2);
              String target = call.getArgument(1), key = call.getArgument(6);
              String action =
                  switch (target) {
                    case "declare" -> "PEER_DECLARE";
                    case "confirm" -> "PEER_CONFIRM";
                    default -> "PEER_OPEN_DISPUTE";
                  };
              String resultKey = actor + "|" + action + "|" + key;
              if (results.containsKey(resultKey)) return results.get(resultKey);
              if (beforeWrite.getAndSet(false)) throw uncertain();
              var attempt = (ObjectNode) remoteAttempts.get(id).path("attempt");
              JsonNode input = call.getArgument(3);
              if (input.path("version").asLong() != attempt.path("version").asLong()
                  || !input.path("termsHash").asText().equals(attempt.path("termsHash").asText()))
                throw new ActionPolicyException(409, "STALE", "Phiên đã thay đổi.", Map.of());
              switch (target) {
                case "declare" -> {
                  var receipt =
                      json.treeToValue((JsonNode) call.getArgument(4), PeerEvidenceReceipt.class);
                  receipt.validate();
                  assertThat(receipt.buyerId()).isEqualTo(buyer.id());
                  if (!attempt.path("phase").asText().equals("WAITING_PAYMENT")
                      || !clock.instant().isBefore(repo.acquisition(id).expiresAt()))
                    throw new ActionPolicyException(409, "EXPIRED", "Đã hết hạn.", Map.of());
                  attempt
                      .put("phase", "PAYMENT_DECLARED")
                      .put("confirmationMethod", "BUYER_DECLARED");
                }
                case "confirm" -> {
                  assertThat(actor).isEqualTo(seller.id());
                  if (!attempt.path("phase").asText().equals("PAYMENT_DECLARED"))
                    throw new ActionPolicyException(
                        409, "NOT_DECLARED", "Chưa có lời khai hợp lệ.", Map.of());
                  attempt
                      .put("phase", "COMPLETED")
                      .put("confirmationMethod", "COUNTERPARTY_ACKNOWLEDGED");
                }
                case "dispute" -> attempt.put("phase", "DISPUTED");
                default -> throw new IllegalArgumentException();
              }
              attempt.put("version", attempt.path("version").asLong() + 1);
              JsonNode result = attempt.deepCopy();
              results.put(resultKey, result);
              committed.incrementAndGet();
              if (loseReply.getAndSet(false)) throw uncertain();
              return result;
            });
    when(authority.commandResult(any(), any(), anyString(), anyString()))
        .thenAnswer(
            call -> {
              noTransaction();
              return results.getOrDefault(
                  call.getArgument(1) + "|" + call.getArgument(2) + "|" + call.getArgument(3),
                  json.createObjectNode().put("phase", "UNKNOWN"));
            });
  }

  private PeerResaleContract.Declaration declaration(UUID bill) {
    return new PeerResaleContract.Declaration(
        0,
        repo.acquisition(attempt).financeTerms().path("termsHash").asText(),
        "BANK REF",
        List.of(bill));
  }

  private PeerResaleContract.Command command(long version) {
    return new PeerResaleContract.Command(
        version, repo.acquisition(attempt).financeTerms().path("termsHash").asText());
  }

  private int count(String table) {
    return repo.jdbc()
        .queryForObject(
            "SELECT count(*) FROM " + table + " WHERE acquisition_id=?", Integer.class, attempt);
  }

  @Test
  void peerPartyOnlyReadyBuyerClaimThenSellerAcknowledgmentCompletesWithoutBankAssurance() {
    var bill = evidence.upload(attempt, photo, key(), buyer);
    String declaredKey = key(), confirmedKey = key();
    var declared = parties.declare(attempt, declaration(bill.id()), declaredKey, buyer);
    assertThat(declared.path("phase").asText()).isEqualTo("PAYMENT_DECLARED");
    assertThat(repo.acquisition(attempt).state()).isEqualTo("PENDING");
    assertThat(count("peer_seller_acknowledgments")).isZero();
    var completed = parties.confirm(attempt, command(1), confirmedKey, seller);
    assertThat(completed.path("confirmationMethod").asText())
        .isEqualTo("COUNTERPARTY_ACKNOWLEDGED");
    assertThat(repo.acquisition(attempt).state()).isEqualTo("SUCCESS");
    assertThat(repo.acquisition(attempt).paymentId()).isNull();
    when(clock.instant()).thenReturn(start.plusSeconds(60));
    assertThat(parties.declare(attempt, declaration(bill.id()), declaredKey, buyer))
        .isEqualTo(declared);
    assertThat(parties.confirm(attempt, command(1), confirmedKey, seller)).isEqualTo(completed);
    assertThat(count("peer_payment_claims")).isEqualTo(1);
    assertThat(count("peer_seller_acknowledgments")).isEqualTo(1);
    assertThat(committed.get()).isEqualTo(2);
    assertThat(repo.acquisition(attempt).financeTerms().toString())
        .doesNotContain("feeBps", "sellerNetAmount", "BANK_VERIFIED");
    assertThatThrownBy(
            () ->
                repo.jdbc()
                    .update(
                        "UPDATE peer_transfer_commands SET command='{}' WHERE acquisition_id=?",
                        attempt))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }

  @Test
  void peerPartyLostReplyRecoversOnlyCommittedResultsAndUnknownNeedsTheSameHumanRetry() {
    var bill = evidence.upload(attempt, photo, key(), buyer);
    String key = key();
    beforeWrite.set(true);
    assertThatThrownBy(() -> parties.declare(attempt, declaration(bill.id()), key, buyer))
        .isInstanceOf(ActionPolicyException.class);
    parties.recover();
    assertThat(committed.get()).isZero();
    verify(authority, times(1)).party(any(), anyString(), any(), any(), any(), any(), anyString());
    loseReply.set(true);
    assertThatThrownBy(() -> parties.declare(attempt, declaration(bill.id()), key, buyer))
        .isInstanceOf(ActionPolicyException.class);
    var restarted =
        new PeerPartyCommands(
            new PeerEvidenceAccess(repo, authority),
            evidence,
            new PeerPartyCommandStore(repo, clock, manager),
            authority,
            projection,
            json,
            clock);
    restarted.recover();
    assertThat(repo.acquisition(attempt).state()).isEqualTo("PENDING");
    assertThat(count("peer_payment_claims")).isEqualTo(1);
    loseReply.set(true);
    String confirmKey = key();
    assertThatThrownBy(() -> restarted.confirm(attempt, command(1), confirmKey, seller))
        .isInstanceOf(ActionPolicyException.class);
    restarted.recover();
    assertThat(repo.acquisition(attempt).state()).isEqualTo("SUCCESS");
    assertThat(count("peer_seller_acknowledgments")).isEqualTo(1);
    assertThat(committed.get()).isEqualTo(2);
    verify(authority, times(3)).party(any(), anyString(), any(), any(), any(), any(), anyString());
  }

  @Test
  void peerPartyRejectsImpersonationWrongUploaderStaleVersionsAndDeclaringWithoutProof() {
    var bill = evidence.upload(attempt, photo, key(), buyer);
    var sellerBill = evidence.upload(attempt, photo, key(), seller);
    assertThatThrownBy(() -> parties.declare(attempt, declaration(bill.id()), key(), seller))
        .isInstanceOf(ForbiddenException.class);
    assertThatThrownBy(() -> parties.confirm(attempt, command(0), key(), buyer))
        .isInstanceOf(ForbiddenException.class);
    assertThatThrownBy(() -> parties.deny(attempt, command(0), key(), buyer))
        .isInstanceOf(ForbiddenException.class);
    assertThatThrownBy(() -> parties.dispute(attempt, command(0), key(), seller))
        .isInstanceOf(ForbiddenException.class);
    assertThatThrownBy(() -> parties.declare(attempt, declaration(sellerBill.id()), key(), buyer))
        .isInstanceOf(ForbiddenException.class);
    assertThatThrownBy(
            () ->
                parties.declare(
                    attempt,
                    new PeerResaleContract.Declaration(
                        0, command(0).termsHash(), "", List.of(bill.id())),
                    key(),
                    buyer))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> parties.confirm(attempt, command(9), key(), seller))
        .isInstanceOf(ActionPolicyException.class);
    assertThat(committed.get()).isZero();
    assertThat(count("peer_seller_acknowledgments")).isZero();
    assertThat(repo.acquisition(attempt).state()).isEqualTo("PENDING");
  }

  @Test
  void peerPartySellerDenialFreezesUsageWithoutInventingAPaymentOrSellerAcknowledgment() {
    var bill = evidence.upload(attempt, photo, key(), buyer);
    parties.declare(attempt, declaration(bill.id()), key(), buyer);
    assertThat(parties.deny(attempt, command(1), key(), seller).path("phase").asText())
        .isEqualTo("DISPUTED");
    assertThat(count("peer_seller_acknowledgments")).isZero();
    assertThat(repo.acquisition(attempt).state()).isEqualTo("PENDING");
    assertThat(repo.acquisition(attempt).paymentId()).isNull();
    assertThatThrownBy(() -> parties.confirm(attempt, command(2), key(), seller))
        .isInstanceOf(ActionPolicyException.class);
    assertThat(count("peer_seller_acknowledgments")).isZero();
  }

  @Test
  void peerPartyStaleDeclarationDoesNotPoisonTheBuyersLaterCorrectDeclaration() {
    var bill = evidence.upload(attempt, photo, key(), buyer);
    var valid = declaration(bill.id());
    assertThatThrownBy(
            () ->
                parties.declare(
                    attempt,
                    new PeerResaleContract.Declaration(
                        9, valid.termsHash(), "BANK REF", valid.evidenceIds()),
                    key(),
                    buyer))
        .isInstanceOf(ConflictException.class);
    assertThat(count("peer_payment_claims")).isZero();
    parties.declare(attempt, valid, key(), buyer);
    assertThat(parties.dispute(attempt, command(1), key(), buyer).path("phase").asText())
        .isEqualTo("DISPUTED");
    assertThat(count("peer_payment_claims")).isEqualTo(1);
    assertThat(count("peer_seller_acknowledgments")).isZero();
  }
}
