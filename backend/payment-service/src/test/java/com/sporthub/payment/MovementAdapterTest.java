package com.sporthub.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporthub.common.exception.*;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.payment.provider.*;
import com.sporthub.payment.provider.MovementProvider.*;
import com.sporthub.payment.repository.PaymentRepository;
import com.sporthub.payment.service.*;
import com.sporthub.payment.service.FinancialLedger.MoneyClass;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class MovementAdapterTest {
    static PostgreSQLContainer<?> pg;JdbcTemplate jdbc;DemoMovementProvider provider;TransactionTemplate tx;SellerPayoutAccounts accounts;MovementAdapters adapters;
    Clock clock=Clock.fixed(Instant.parse("2026-10-21T00:00:00Z"),ZoneOffset.UTC);ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    static final String SECRET="demo-movement-fixture-secret-over-thirty-two-characters";
    @BeforeAll static void start(){pg=new PostgreSQLContainer<>("postgres:16-alpine");pg.start();}
    @AfterAll static void stop(){if(pg!=null)pg.stop();}
    @BeforeEach void setup(){
        String schema="adapter_"+UUID.randomUUID().toString().replace("-","");Flyway.configure().dataSource(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword()).schemas(schema).defaultSchema(schema).load().migrate();
        var ds=new DriverManagerDataSource(pg.getJdbcUrl()+(pg.getJdbcUrl().contains("?")?"&":"?")+"currentSchema="+schema+",public",pg.getUsername(),pg.getPassword());jdbc=new JdbcTemplate(ds);var manager=new DataSourceTransactionManager(ds);tx=new TransactionTemplate(manager);
        provider=new DemoMovementProvider(new PaymentRepository(jdbc),json,clock,manager,SECRET);adapters=new MovementAdapters(List.of(provider));accounts=new SellerPayoutAccounts(new PaymentRepository(jdbc),adapters,json);
    }
    Caller customer(){return new Caller(UUID.randomUUID(),"Seller",Set.of("CUSTOMER"),Map.of());}
    Command command(){UUID order=UUID.randomUUID(),attempt=UUID.randomUUID();BigDecimal amount=new BigDecimal("98000");String key="demo-"+attempt,counterparty="BENEFICIARY:"+UUID.randomUUID();Instant expiry=clock.instant().plus(Duration.ofDays(7));return new Command(order,attempt,"SELLER_PAYOUT",amount,"VND",MoneyClass.DEMO,counterparty,key,MovementProvider.fingerprint(order,attempt,"SELLER_PAYOUT",amount,"VND",MoneyClass.DEMO,counterparty,key,expiry),expiry);}
    @Test void lostSuccessIsDurableAndSameKeyDoesNotPayTwiceAfterRestart(){
        var c=command();provider.fault(c.orderId(),DemoMovementProvider.Fault.TIMEOUT_AFTER_SUCCESS);
        assertThatThrownBy(()->provider.submit(c)).isInstanceOf(MovementProvider.Uncertain.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM demo_provider_movement_commands",Integer.class)).isEqualTo(1);
        var restarted=new DemoMovementProvider(new PaymentRepository(jdbc),json,clock,tx.getTransactionManager(),SECRET);
        var result=restarted.query(c).orElseThrow();restarted.verify(result);assertThat(result.kind()).isEqualTo("FINAL");assertThat(result.amount()).isEqualByComparingTo("98000");
        assertThat(restarted.submit(c)).isEqualTo(result);assertThat(jdbc.queryForObject("SELECT count(*) FROM demo_provider_movement_commands",Integer.class)).isEqualTo(1);
    }
    @Test void acceptedAndSourceDebitAreDifferentFromBeneficiaryFinalReceipt(){
        for(var fault:List.of(DemoMovementProvider.Fault.ACCEPT_ONLY,DemoMovementProvider.Fault.DEBIT_ONLY,DemoMovementProvider.Fault.FAIL_NO_OUTFLOW)){
            var c=command();provider.fault(c.orderId(),fault);var event=provider.submit(c);provider.verify(event);assertThat(event.kind()).isEqualTo(switch(fault){case ACCEPT_ONLY->"ACCEPTED";case DEBIT_ONLY->"DEBITED";default->"FAILED_NO_OUTFLOW";});
        }
        var c=command();provider.fault(c.orderId(),DemoMovementProvider.Fault.UNKNOWN_FOREVER);assertThatThrownBy(()->provider.submit(c)).isInstanceOf(MovementProvider.Uncertain.class);assertThat(provider.query(c)).isEmpty();
    }
    @Test void partialBankReturnRemainsANewFactAndFinalReceiptIsStillQueryable(){
        var c=command();var paid=provider.submit(c);var returned=provider.event(c,"RETURN",new BigDecimal("20000"),"return-"+c.attemptId());provider.verify(returned);
        assertThat(provider.query(c).orElseThrow()).isEqualTo(paid);assertThat(returned.amount()).isEqualByComparingTo("20000");
        assertThat(provider.statement(clock.instant().minusSeconds(1),clock.instant().plusSeconds(1))).hasSize(2);
    }
    @Test void scopeExpiryPayloadTamperingAndForgedProviderProofAreRejected(){
        var c=command();var e=provider.submit(c);
        var forged=new Event(e.provider(),e.account(),e.region(),e.eventId(),e.reference(),e.orderId(),e.attemptId(),"FINAL",BigDecimal.ONE,e.currency(),e.counterparty(),e.occurredAt(),e.signature());
        assertThatThrownBy(()->provider.verify(forged)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(()->adapters.require("DEMO","demo-main",MoneyClass.REAL)).isInstanceOf(ConflictException.class);assertThat(provider.capabilities().liveVerified()).isFalse();
        var later=new DemoMovementProvider(new PaymentRepository(jdbc),json,Clock.offset(clock,Duration.ofDays(8)),tx.getTransactionManager(),SECRET);
        assertThatThrownBy(()->later.submit(c)).isInstanceOf(ConflictException.class);assertThat(later.query(c)).isPresent();
        var changed=new Command(c.orderId(),c.attemptId(),c.kind(),BigDecimal.ONE,c.currency(),c.moneyClass(),c.counterparty(),c.idempotencyKey(),c.fingerprint(),c.keyExpiresAt());assertThatThrownBy(()->provider.submit(changed)).isInstanceOf(ForbiddenException.class);
    }
    @Test void sellerOwnsVerifiedRevisionsAndPublicProjectionNeverLeaksProviderTokens()throws Exception{
        var seller=customer();var other=customer();var input=new SellerPayoutAccounts.Input(null,"DEMO","DEMO destination 1234","explicit demo consent");String key=UUID.randomUUID().toString();
        var destination=tx.execute(s->accounts.register(input,key,seller));var replay=tx.execute(s->accounts.register(input,key,seller));assertThat(replay.id()).isEqualTo(destination.id());
        assertThat(accounts.verified(destination.id(),seller.id()).verified()).isTrue();assertThatThrownBy(()->accounts.verified(destination.id(),other.id())).isInstanceOf(ConflictException.class);
        assertThatThrownBy(()->accounts.own(destination.id(),other)).isInstanceOf(ForbiddenException.class);
        assertThat(json.writeValueAsString(destination)).doesNotContain("providerToken","DEMO-BENEFICIARY","proofReference","explicit demo consent");
        assertThatThrownBy(()->tx.execute(s->accounts.register(new SellerPayoutAccounts.Input(destination.accountId(),"DEMO","DEMO other 9999","consent"),UUID.randomUUID().toString(),other))).isInstanceOf(ForbiddenException.class);
        var revised=tx.execute(s->accounts.register(new SellerPayoutAccounts.Input(destination.accountId(),"DEMO","DEMO destination 5678","new consent"),UUID.randomUUID().toString(),seller));assertThat(revised.revision()).isEqualTo(2);
        tx.execute(s->accounts.revoke(destination.id(),"Old destination revoked",seller));assertThatThrownBy(()->accounts.verified(destination.id(),seller.id())).isInstanceOf(ConflictException.class);
        assertThat(accounts.verified(revised.id(),seller.id()).verified()).isTrue();
    }
}
