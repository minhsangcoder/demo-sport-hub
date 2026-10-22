package com.sporthub.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporthub.common.exception.ConflictException;
import com.sporthub.payment.repository.PaymentRepository;
import com.sporthub.payment.service.FinancialLedger;
import com.sporthub.payment.service.FinancialLedger.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class FinancialLedgerMigrationTest {
    static PostgreSQLContainer<?> pg;
    JdbcTemplate jdbc;TransactionTemplate tx;FinancialLedger ledger;
    @BeforeAll static void start(){pg=new PostgreSQLContainer<>("postgres:16-alpine");pg.start();}
    @AfterAll static void stop(){if(pg!=null)pg.stop();}
    @BeforeEach void setup(){
        String schema="finance_"+UUID.randomUUID().toString().replace("-","");
        var flyway=Flyway.configure().dataSource(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword()).schemas(schema).defaultSchema(schema).load();flyway.migrate();flyway.validate();
        var ds=new DriverManagerDataSource(schemaUrl(schema),pg.getUsername(),pg.getPassword());jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        ledger=new FinancialLedger(new PaymentRepository(jdbc),new ObjectMapper().findAndRegisterModules());
    }
    Account account(String kind,MoneyClass moneyClass){return new Account(kind,null,"DEMO","demo-main","VND",moneyClass);}
    Effect effect(String key,BigDecimal amount){return new Effect("TEST",key,null,null,null,"VND",MoneyClass.DEMO,Instant.parse("2026-10-21T00:00:00Z"),null,Map.of("test",true),List.of(new Line(account("PROVIDER_CLEARING",MoneyClass.DEMO),Side.D,amount),new Line(account("BUYER_ESCROW",MoneyClass.DEMO),Side.C,amount)));}
    @Test void effectsAreExactBalancedIdempotentAndAppendOnly(){
        var effect=effect("collect",new BigDecimal("100001"));UUID id=tx.execute(s->ledger.post(effect));UUID replay=tx.execute(s->ledger.post(effect));assertThat(replay).isEqualTo(id);
        assertThat(ledger.debitBalance(account("PROVIDER_CLEARING",MoneyClass.DEMO))).isEqualByComparingTo("100001");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_journals",Integer.class)).isEqualTo(1);
        assertThatThrownBy(()->tx.execute(s->ledger.post(effect("collect",new BigDecimal("100002"))))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE financial_journals SET effective_at=clock_timestamp() WHERE id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM financial_postings WHERE journal_id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->tx.execute(s->{jdbc.update("INSERT INTO financial_postings(journal_id,line_no,account_id,side,amount) SELECT ?,3,account_id,'D',1 FROM financial_postings WHERE journal_id=? LIMIT 1",id,id);return null;})).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->tx.execute(s->ledger.post(effect("fraction",new BigDecimal("1.25"))))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void databaseRejectsEmptyUnbalancedAndCrossClassJournals(){
        for(String mode:List.of("EMPTY","UNBALANCED","CROSS_CLASS")){
            assertThatThrownBy(()->tx.execute(s->{
                UUID id=UUID.randomUUID();jdbc.update("INSERT INTO financial_journals(id,effect_type,effect_ref,currency,money_class,effective_at,evidence) VALUES(?,'RAW',?,'VND','DEMO',clock_timestamp(),'{}')",id,mode);
                if(!mode.equals("EMPTY"))for(int i=0;i<2;i++){
                    UUID account=UUID.randomUUID();jdbc.update("INSERT INTO financial_accounts(id,kind,scope_id,provider,provider_account,currency,money_class) VALUES(?,'SUSPENSE',?,'DEMO','raw','VND',?)",account,UUID.randomUUID(),mode.equals("CROSS_CLASS")&&i==1?"REAL":"DEMO");
                    jdbc.update("INSERT INTO financial_postings(journal_id,line_no,account_id,side,amount) VALUES(?,?,?,?,?)",id,i+1,account,i==0?"D":"C",mode.equals("UNBALANCED")&&i==1?2:1);
                }return null;
            })).hasRootCauseInstanceOf(SQLException.class);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_journals",Integer.class)).isZero();
    }
    @Test void nullableAccountScopeStillHasOneBusinessIdentity(){
        tx.execute(s->ledger.post(effect("one",BigDecimal.ONE)));tx.execute(s->ledger.post(effect("two",BigDecimal.TEN)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_accounts",Integer.class)).isEqualTo(2);
        assertThat(ledger.debitBalance(account("BUYER_ESCROW",MoneyClass.DEMO))).isEqualByComparingTo("-11");
    }
    @Test void upgradeDoesNotInventLegacySettlementMoneyOrRefunds(){
        String schema="upgrade_finance_"+UUID.randomUUID().toString().replace("-","");
        var old=Flyway.configure().dataSource(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword()).schemas(schema).defaultSchema(schema).target("9").load();old.migrate();
        var ds=new DriverManagerDataSource(schemaUrl(schema),pg.getUsername(),pg.getPassword());var oldJdbc=new JdbcTemplate(ds);
        UUID booking=UUID.randomUUID(),payer=UUID.randomUUID(),payment=UUID.randomUUID();
        oldJdbc.update("INSERT INTO payment_obligations(booking_id,purpose,subject_id,amount,currency) VALUES(?,'BOOKING','00000000-0000-0000-0000-000000000000',120000.25,'VND')",booking);
        oldJdbc.update("INSERT INTO payment_orders(id,booking_id,payer_id,purpose,amount,currency,provider,provider_reference,status,idempotency_key,expires_at) VALUES(?,?,?,'BOOKING',120000.25,'VND','DEMO',?,'PENDING',?,clock_timestamp()+interval '1 hour')",payment,booking,payer,"old-"+payment,"key-"+payment);
        String before=oldJdbc.queryForObject("SELECT to_jsonb(p)::text FROM payment_orders p WHERE id=?",String.class,payment);
        var latest=Flyway.configure().dataSource(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword()).schemas(schema).defaultSchema(schema).load();latest.migrate();latest.validate();
        assertThat(oldJdbc.queryForObject("SELECT to_jsonb(p)::text FROM payment_orders p WHERE id=?",String.class,payment)).isEqualTo(before);
        for(String table:List.of("transfer_settlements","financial_journals","money_movement_orders","facility_refund_requests"))assertThat(oldJdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class)).isZero();
    }
    record Fixture(UUID payment,UUID booking,UUID buyer,UUID seller,UUID acquisition,UUID listing,UUID facility,UUID court,UUID beneficiary,UUID settlement,UUID bucket) {}
    Fixture fixture(){return fixture(false);}
    Fixture fixture(boolean aborted){
        var f=new Fixture(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());UUID account=UUID.randomUUID();
        tx.executeWithoutResult(s->{
            jdbc.update("INSERT INTO payment_obligations(booking_id,purpose,subject_id,amount,currency) VALUES(?,'TRANSFER',?,100000,'VND')",f.booking(),f.acquisition());
            jdbc.update("INSERT INTO payment_orders(id,booking_id,payer_id,acquisition_id,seller_id,purpose,amount,currency,provider,provider_reference,status,idempotency_key,expires_at) VALUES(?,?,?,?,?,'TRANSFER',100000,'VND','DEMO',?,'PENDING',?,clock_timestamp()+interval '1 hour')",f.payment(),f.booking(),f.buyer(),f.acquisition(),f.seller(),"demo-"+f.payment(),"key-"+f.payment());
            jdbc.update("UPDATE payment_orders SET status='SUCCESS',paid_at=clock_timestamp() WHERE id=?",f.payment());
            jdbc.update("UPDATE payment_obligations SET settled_payment_id=?,current_payment_id=? WHERE booking_id=?",f.payment(),f.payment(),f.booking());
            jdbc.update("INSERT INTO transfer_escrow(payment_id,acquisition_id,payer_id,seller_id,amount,currency,state) VALUES(?,?,?,?,100000,'VND','HELD_POLICY_BLOCKED')",f.payment(),f.acquisition(),f.buyer(),f.seller());
            jdbc.update("INSERT INTO seller_payout_accounts(id,owner_id,provider,provider_account,currency,money_class) VALUES(?,?,'DEMO','demo-main','VND','DEMO')",account,f.seller());
            jdbc.update("INSERT INTO seller_payout_account_revisions(id,account_id,owner_id,revision,provider,provider_account,provider_token,masked_label,verification_status,consent_reference,verified_at,verification_proof) VALUES(?,?,?,1,'DEMO','demo-main',?,'DEMO masked','VERIFIED','demo-consent',clock_timestamp(),'{\"demo\":true}')",f.beneficiary(),account,f.seller(),"token-"+f.beneficiary());
            var mapper=new ObjectMapper().findAndRegisterModules();Instant expiry=jdbc.queryForObject("SELECT expires_at FROM payment_orders WHERE id=?",Timestamp.class,f.payment()).toInstant();
            var policy=mapper.valueToTree(Map.of("configured",true,"id",UUID.randomUUID(),"version",1,"rateBps",8000,"currency","VND","rounding","HALF_UP","termsHash",com.sporthub.common.security.Signatures.sha256(f.facility()+"|1|8000|VND|HALF_UP|FACILITY_POLICY_CANCELLATION")));
            var beneficiary=mapper.valueToTree(Map.of("verified",true,"sellerId",f.seller(),"revisionId",f.beneficiary(),"provider","DEMO","providerAccount","demo-main","moneyClass","DEMO","currency","VND"));
            var sellerTerms=com.sporthub.common.money.TransferFinancialTerms.seller(mapper,f.booking(),f.facility(),f.court(),f.seller(),new BigDecimal("100000"),expiry.plusSeconds(3600),beneficiary,policy);
            var snapshot=com.sporthub.common.money.TransferFinancialTerms.acquisition(sellerTerms,f.listing(),0,f.acquisition(),f.buyer(),expiry,Instant.parse("2026-10-21T00:00:00Z"));
            jdbc.update("INSERT INTO transfer_payment_terms(payment_id,acquisition_id,listing_id,booking_id,facility_id,court_id,seller_id,buyer_id,policy_version,fee_bps,rounding,amount,quoted_fee_amount,quoted_seller_net_amount,currency,beneficiary_revision_id,terms_hash,refund_policy,snapshot,accepted_at) VALUES(?,?,?,?,?,?,?,?,1,200,'HALF_UP',100000,2000,98000,'VND',?,?,?::jsonb,?::jsonb,?)",f.payment(),f.acquisition(),f.listing(),f.booking(),f.facility(),f.court(),f.seller(),f.buyer(),f.beneficiary(),snapshot.path("termsHash").asText(),policy.toString(),snapshot.toString(),Timestamp.from(Instant.parse(snapshot.path("acceptedAt").asText())));
            jdbc.update("INSERT INTO transfer_settlements(id,acquisition_id,payment_id,origin,state,provider,provider_account,money_class,currency,listing_id,booking_id,facility_id,court_id,seller_id,buyer_id,amount,quoted_fee_amount,quoted_seller_net_amount,fee_bps,policy_version,rounding,terms_hash,beneficiary_revision_id,decision_ref,decision_fingerprint,decided_at) VALUES(?,?,?,'NEW_TERMS',?,'DEMO','demo-main','DEMO','VND',?,?,?,?,?,?,100000,2000,98000,200,1,'HALF_UP',?,?,?, ?,clock_timestamp())",f.settlement(),f.acquisition(),f.payment(),aborted?"ABORTED":"ALLOCATED",f.listing(),f.booking(),f.facility(),f.court(),f.seller(),f.buyer(),snapshot.path("termsHash").asText(),f.beneficiary(),f.acquisition(),"b".repeat(64));
            jdbc.update("INSERT INTO financial_funding_buckets(id,scope_kind,payment_id,provider,provider_account,currency,money_class) VALUES(?,'TRANSFER_COLLECTION',?,'DEMO','demo-main','VND','DEMO')",f.bucket(),f.payment());
        });return f;
    }
    UUID refund(Fixture f,int amount,UUID payee){
        UUID id=UUID.randomUUID();jdbc.update("INSERT INTO money_movement_orders(id,kind,business_key,payment_id,settlement_id,payee_id,amount,currency,money_class,provider,provider_account,state,proof) VALUES(?,'BUYER_REFUND',?,?,?,?,?,'VND','DEMO','DEMO','demo-main','NEW',?::jsonb)",id,"refund-"+id,f.payment(),f.settlement(),payee,amount,"{\"acquisitionId\":\""+f.acquisition()+"\",\"decisionFingerprint\":\""+"b".repeat(64)+"\"}");return id;
    }
    UUID payout(Fixture f){
        UUID id=UUID.randomUUID();jdbc.update("INSERT INTO money_movement_orders(id,kind,business_key,payment_id,settlement_id,payee_id,beneficiary_revision_id,amount,currency,money_class,provider,provider_account,state,proof) VALUES(?,'SELLER_PAYOUT',?,?,?,?,?,98000,'VND','DEMO','DEMO','demo-main','NEW','{}')",id,"payout-"+id,f.payment(),f.settlement(),f.seller(),f.beneficiary());return id;
    }
    @Test void schemaBindsTermsToCanonicalPaymentAndDoesNotAllowMissingTermsOrFinancialMutation(){
        var f=fixture();
        assertThatThrownBy(()->jdbc.update("UPDATE transfer_payment_terms SET quoted_fee_amount=0 WHERE payment_id=?",f.payment())).hasRootCauseInstanceOf(SQLException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE transfer_settlements SET quoted_seller_net_amount=NULL,version=version+1 WHERE id=?",f.settlement())).hasRootCauseInstanceOf(SQLException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE transfer_settlements SET state='ABORTED',version=version+1 WHERE id=?",f.settlement())).hasRootCauseInstanceOf(SQLException.class);
        assertThatThrownBy(()->tx.execute(s->{UUID id=UUID.randomUUID();jdbc.update("INSERT INTO transfer_settlements(id,acquisition_id,payment_id,origin,state,provider,provider_account,money_class,currency,booking_id,buyer_id,amount) VALUES(?,?,?,'NEW_TERMS','WAITING_HANDOFF','DEMO','demo-main','DEMO','VND',?,?,100000)",id,UUID.randomUUID(),f.payment(),f.booking(),f.buyer());return null;})).hasRootCauseInstanceOf(SQLException.class);
        UUID order=tx.execute(s->payout(f));assertThat(order).isNotNull();assertThatThrownBy(()->tx.execute(s->payout(f))).hasRootCauseInstanceOf(SQLException.class);
    }
    @Test void refundsShareReceivedPaymentCapAndAlwaysReturnToOriginalPayer(){
        var f=fixture(true);assertThatThrownBy(()->tx.execute(s->refund(f,100000,f.seller()))).hasRootCauseInstanceOf(SQLException.class);
        assertThatThrownBy(()->tx.execute(s->payout(f))).hasRootCauseInstanceOf(SQLException.class);
        tx.execute(s->refund(f,100000,f.buyer()));assertThatThrownBy(()->tx.execute(s->refund(f,100000,f.buyer()))).hasRootCauseInstanceOf(SQLException.class);
        assertThat(jdbc.queryForObject("SELECT sum(amount) FROM money_movement_orders WHERE payment_id=?",BigDecimal.class,f.payment())).isEqualByComparingTo("100000");
    }
    @Test void exceptionApprovalRequiresAnotherActorAndNotesDoNotPostMoney(){
        UUID c=UUID.randomUUID(),action=UUID.randomUUID(),actor=UUID.randomUUID();
        jdbc.update("INSERT INTO financial_reconciliation_cases(id,case_key,category,reason) VALUES(?,?,'EXCEPTION','proof review')",c,"case-"+c);
        jdbc.update("INSERT INTO financial_case_actions(id,case_id,actor_id,action,resolution_key,effect_type,proof) VALUES(?,?,?,'PROPOSE_RESOLUTION',?,'FEE_REVERSAL','{}')",action,c,actor,"resolution-"+action);
        assertThatThrownBy(()->jdbc.update("INSERT INTO financial_approvals(action_id,actor_id,proof) VALUES(?,?,'{}')",action,actor)).hasRootCauseInstanceOf(SQLException.class);
        jdbc.update("INSERT INTO financial_approvals(action_id,actor_id,proof) VALUES(?,?,'{}')",action,UUID.randomUUID());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_journals",Integer.class)).isZero();
    }
    UUID attempt(Fixture f,UUID order){
        UUID id=UUID.randomUUID();jdbc.update("INSERT INTO money_movement_attempts(id,order_id,attempt_no,amount,beneficiary_revision_id,funding_bucket_id,provider,provider_account,region,idempotency_key,request_fingerprint,request_body,key_expires_at,state,lease_until,next_query_at) VALUES(?,?,1,98000,?,?,'DEMO','demo-main','local',?,?,'{}',clock_timestamp()+interval '7 days','UNKNOWN',clock_timestamp()+interval '1 minute',clock_timestamp())",id,order,f.beneficiary(),f.bucket(),"attempt-"+id,"c".repeat(64));
        jdbc.update("INSERT INTO financial_funding_reservations(attempt_id,bucket_id,original_amount,remaining_amount) VALUES(?,?,98000,98000)",id,f.bucket());return id;
    }
    @Test void concurrentWorkersCannotReserveOrAttemptTheSameEntitlementTwice()throws Exception{
        var f=fixture();UUID order=tx.execute(s->payout(f));
        assertThatThrownBy(()->tx.execute(s->attempt(f,order))).hasRootCauseInstanceOf(SQLException.class);
        var available=new Account("PROVIDER_AVAILABLE",f.bucket(),"DEMO","demo-main","VND",MoneyClass.DEMO);
        var suspense=new Account("SUSPENSE",f.bucket(),"DEMO","demo-main","VND",MoneyClass.DEMO);
        tx.execute(s->ledger.post(new Effect("TEST_FUNDING",f.bucket().toString(),f.payment(),null,null,"VND",MoneyClass.DEMO,Instant.parse("2026-10-21T00:00:00Z"),null,Map.of("testOnly",true),List.of(new Line(available,Side.D,new BigDecimal("100000")),new Line(suspense,Side.C,new BigDecimal("100000"))))));
        var go=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var workers=new ArrayList<java.util.concurrent.Future<Boolean>>();
            for(int i=0;i<2;i++)workers.add(pool.submit(()->{go.await();try{tx.execute(s->attempt(f,order));return true;}catch(org.springframework.dao.DataAccessException ex){return false;}}));
            go.countDown();int claimed=0;for(var worker:workers)if(worker.get(20,java.util.concurrent.TimeUnit.SECONDS))claimed++;assertThat(claimed).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT sum(remaining_amount) FROM financial_funding_reservations WHERE bucket_id=?",BigDecimal.class,f.bucket())).isEqualByComparingTo("98000");
        assertThat(ledger.debitBalance(available)).isEqualByComparingTo("100000");
    }
    @Test void legacySuccessNeedsActualCallbackAndOutcomeEvidenceAndReadingNeverInventsMovements(){
        var bindings=new ArrayList<com.sporthub.common.money.PaymentProofFacts.Binding>();var mapper=new ObjectMapper().findAndRegisterModules();Instant paid=Instant.parse("2026-10-21T00:00:00Z");
        for(boolean proof:List.of(true,false)){
            UUID payment=UUID.randomUUID(),booking=UUID.randomUUID(),payer=UUID.randomUUID();String reference="legacy-"+payment;
            tx.executeWithoutResult(s->{
                jdbc.update("INSERT INTO payment_obligations(booking_id,purpose,subject_id,amount,currency) VALUES(?,'BOOKING','00000000-0000-0000-0000-000000000000',120000.25,'VND')",booking);
                jdbc.update("INSERT INTO payment_orders(id,booking_id,payer_id,purpose,amount,currency,provider,provider_reference,status,idempotency_key,expires_at) VALUES(?,?,?,'BOOKING',120000.25,'VND','DEMO',?,'PENDING',?,?)",payment,booking,payer,reference,reference,Timestamp.from(paid.plusSeconds(600)));
                jdbc.update("UPDATE payment_orders SET status='SUCCESS',paid_at=? WHERE id=?",Timestamp.from(paid),payment);jdbc.update("UPDATE payment_obligations SET current_payment_id=?,settled_payment_id=? WHERE booking_id=?",payment,payment,booking);
                if(proof){
                    var callback=mapper.valueToTree(Map.of("paymentId",payment,"providerReference",reference,"transactionId","TX-"+payment,"amount",new BigDecimal("120000.25"),"currency","VND","status","SUCCESS","timestamp",paid.getEpochSecond()));
                    jdbc.update("INSERT INTO provider_callbacks(provider,transaction_id,payment_id,fingerprint,body) VALUES('DEMO',?,?,?,?::jsonb)","TX-"+payment,payment,com.sporthub.common.security.Signatures.sha256(payment+"|TX-"+payment+"|120000.25|VND|SUCCESS"),callback.toString());
                    var payload=mapper.valueToTree(Map.of("paymentId",payment,"bookingId",booking,"payerId",payer,"amount",new BigDecimal("120000.25"),"currency","VND","status","SUCCESS","purpose","BOOKING","paidAt",paid.toString()));
                    var event=mapper.valueToTree(com.sporthub.common.event.DomainEvent.create("payment.completed",1,"payment-service",payment,payload));
                    jdbc.update("INSERT INTO event_outbox(event_id,exchange,routing_key,body) VALUES(?,'sporthub.payment','payment.completed',?::jsonb)",UUID.fromString(event.path("eventId").asText()),event.toString());
                }
            });bindings.add(new com.sporthub.common.money.PaymentProofFacts.Binding(payment,booking,null,"BOOKING"));
        }
        var service=new com.sporthub.payment.service.PaymentReportFacts(new PaymentRepository(jdbc),mapper);var result=service.read(new com.sporthub.common.money.PaymentProofFacts.Request(bindings));
        assertThat(result.facts()).filteredOn(com.sporthub.common.money.PaymentProofFacts.Fact::verified).hasSize(1);
        var valid=result.facts().stream().filter(com.sporthub.common.money.PaymentProofFacts.Fact::verified).findFirst().orElseThrow();assertThat(valid.moneyClass()).isEqualTo("DEMO");assertThat(valid.amount()).isEqualTo("120000.25");assertThat(valid.proofSource()).isEqualTo("LEGACY_CALLBACK_AND_OUTCOME");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_journals",Integer.class)).isZero();assertThat(jdbc.queryForObject("SELECT count(*) FROM money_movement_orders",Integer.class)).isZero();
    }
    private String schemaUrl(String schema){return pg.getJdbcUrl()+(pg.getJdbcUrl().contains("?")?"&":"?")+"currentSchema="+schema+",public";}
}
