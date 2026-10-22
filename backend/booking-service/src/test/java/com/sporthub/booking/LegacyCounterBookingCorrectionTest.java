package com.sporthub.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sporthub.booking.repository.BookingRepository;
import com.sporthub.booking.service.*;
import com.sporthub.booking.service.LegacyCounterBookingCorrectionService.*;
import com.sporthub.booking.web.BookingDtos.*;
import com.sporthub.common.exception.ForbiddenException;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.common.security.Signatures;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import java.sql.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LegacyCounterBookingCorrectionTest {
    static PostgreSQLContainer<?> postgres;
    static String baseUrl, username, password;
    String url, schema;
    BookingRepository repo;
    DataSourceTransactionManager tm;
    TransactionTemplate tx;
    LegacyCounterBookingCorrectionService service;
    Instant now=Instant.now().truncatedTo(ChronoUnit.MICROS);

    @BeforeAll static void database() throws Exception {
        baseUrl=System.getenv("SPORTHUB_BOOKING_TEST_DB_URL");
        username=System.getenv("SPORTHUB_TEST_DB_USER"); password=System.getenv("SPORTHUB_TEST_DB_PASSWORD");
        if(baseUrl==null){postgres=new PostgreSQLContainer<>("postgres:16-alpine").withInitScript("booking-test-prerequisites.sql");postgres.start();baseUrl=postgres.getJdbcUrl();username=postgres.getUsername();password=postgres.getPassword();}
        try(var c=DriverManager.getConnection(baseUrl,username,password);var s=c.createStatement()){
            s.execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\" WITH SCHEMA pg_catalog");
        }
    }
    @AfterAll static void stop(){if(postgres!=null)postgres.stop();}
    @BeforeEach void setup(){
        schema="ownership_legacy_"+UUID.randomUUID().toString().replace("-","");
        Flyway.configure().dataSource(baseUrl,username,password).schemas(schema).defaultSchema(schema).target("11").load().migrate();
        url=baseUrl+(baseUrl.contains("?")?"&":"?")+"currentSchema="+schema+",public";
        var ds=new DriverManagerDataSource(url,username,password);
        repo=new BookingRepository(new JdbcTemplate(ds),new ObjectMapper().findAndRegisterModules());
        tm=new DataSourceTransactionManager(ds);tx=new TransactionTemplate(tm);
        service=new LegacyCounterBookingCorrectionService(repo,tm);
    }
    @AfterEach void cleanup(){
        try(var c=DriverManager.getConnection(baseUrl,username,password);var s=c.createStatement()){s.execute("DROP SCHEMA "+schema+" CASCADE");}
        catch(SQLException e){throw new IllegalStateException(e);}
    }
    void upgrade(){Flyway.configure().dataSource(baseUrl,username,password).schemas(schema).defaultSchema(schema).load().migrate();}
    UUID seed(UUID linked,boolean snapshot){
        UUID id=UUID.randomUUID(),res=UUID.randomUUID(),court=UUID.randomUUID(),facility=UUID.randomUUID(),actor=UUID.randomUUID();
        UUID holder=linked==null?actor:linked;
        tx.executeWithoutResult(s->{
            repo.jdbc().update("INSERT INTO slot_reservations(id,court_id,facility_id,holder_id,starts_at,ends_at,expires_at,state,quote) VALUES(?,?,?,?,?,?,?,'HOLD','{}')",res,court,facility,holder,Timestamp.from(now.plusSeconds(600)),Timestamp.from(now.plusSeconds(4200)),Timestamp.from(now.plusSeconds(300)));
            repo.jdbc().update("INSERT INTO bookings(id,reservation_id,facility_id,court_id,created_by,customer_id,current_holder_id,starts_at,ends_at,status,source,guest_name,guest_phone,amount,currency,price_snapshot,hold_expires_at) VALUES(?,?,?,?,?,?,?,?,?,'PENDING','OFFLINE_COUNTER','Synthetic Guest','0911223344',100000,'VND','{}',?)",id,res,facility,court,actor,holder,holder,Timestamp.from(now.plusSeconds(600)),Timestamp.from(now.plusSeconds(4200)),Timestamp.from(now.plusSeconds(300)));
            if(snapshot)repo.jdbc().update("INSERT INTO counter_booking_guests(booking_id,operator_id,linked_customer_id,guest_name,guest_phone) VALUES(?,?,?,'Synthetic Guest','0911223344')",id,actor,linked);
        });
        return id;
    }
    void pay(UUID id){
        var b=repo.find(id);UUID payment=UUID.randomUUID();
        tx.executeWithoutResult(s->{
            String fingerprint=Signatures.sha256(id+"|"+b.createdBy()+"||BOOKING|SUCCESS|100000|VND|"+now);
            repo.jdbc().update("INSERT INTO booking_payment_receipts(payment_id,booking_id,fingerprint) VALUES(?,?,?)",payment,id,fingerprint);
            new BookingLifecycle(repo,Clock.fixed(now,ZoneOffset.UTC)).transition(b,"CONFIRMED",b.createdBy(),"CONFIRMED",Map.of("paymentId",payment),now,payment);
        });
    }
    Candidate candidate(UUID id){return service.preflight(url).candidates().stream().filter(c->c.bookingId().equals(id)).findFirst().orElseThrow();}

    @Test void explicitTargetMustMatchTheActualDatabase(){
        UUID id=seed(null,true);upgrade();
        assertThatThrownBy(()->service.preflight(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.preflight("test_db")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.preflight(url+"wrong")).isInstanceOf(IllegalArgumentException.class);
        var manifest=candidate(id);
        assertThatThrownBy(()->service.correct(url+"wrong",UUID.randomUUID(),List.of(manifest))).isInstanceOf(IllegalArgumentException.class);
        assertThat(repo.find(id).currentHolderId()).isEqualTo(manifest.expectedOperatorId());
    }
    @Test void classifiesGuestLinkedMissingSnapshotAndUnpaidConfirmedEvidence(){
        UUID eligible=seed(null,true),linked=seed(UUID.randomUUID(),true),missing=seed(null,false),paid=seed(null,true);
        pay(paid);upgrade();
        assertThat(candidate(eligible).category()).isEqualTo("CONFIRMED_GUEST_LEGACY");
        assertThat(candidate(paid).category()).isEqualTo("CONFIRMED_GUEST_LEGACY");
        assertThat(candidate(linked).category()).isEqualTo("EXCLUDED");
        assertThat(candidate(missing).reason()).isEqualTo("MISSING_GUEST_SNAPSHOT");
        UUID unpaid=seedOnlineForMissingPayment();
        // Linked online ownership is excluded, regardless of a Staff-like UUID.
        assertThat(service.classify(repo.find(unpaid)).reason()).isEqualTo("NOT_COUNTER_BOOKING");
    }
    UUID seedOnlineForMissingPayment(){
        UUID id=UUID.randomUUID(),res=UUID.randomUUID(),actor=UUID.randomUUID();
        tx.executeWithoutResult(s->{
            repo.jdbc().update("INSERT INTO slot_reservations(id,court_id,facility_id,holder_id,starts_at,ends_at,expires_at,state,quote) VALUES(?,gen_random_uuid(),gen_random_uuid(),?,now()+interval '2 days',now()+interval '2 days 1 hour',now()+interval '10 minutes','HOLD','{}')",res,actor);
            repo.jdbc().update("INSERT INTO bookings(id,reservation_id,facility_id,court_id,created_by,customer_id,current_holder_id,starts_at,ends_at,status,source,amount,currency,price_snapshot,hold_expires_at) SELECT ?,id,facility_id,court_id,holder_id,holder_id,holder_id,starts_at,ends_at,'PENDING','ONLINE',100,'VND',quote,expires_at FROM slot_reservations WHERE id=?",id,res);
        });return id;
    }
    @Test void reconciliationRequiresReviewEvenWhenAnAnnotationExists(){
        UUID id=seed(null,true);UUID reconciliation=UUID.randomUUID();
        repo.jdbc().update("INSERT INTO payment_reconciliation(id,booking_id,payment_id,reason) VALUES(?,?,?,'AMOUNT_MISMATCH')",reconciliation,id,UUID.randomUUID());
        repo.jdbc().update("INSERT INTO booking_reconciliation_reviews(id,reconciliation_id,actor_id,note,idempotency_key,fingerprint) VALUES(?,?,?,'Reviewed, not financially settled','review',?)",UUID.randomUUID(),reconciliation,UUID.randomUUID(),"a".repeat(64));
        upgrade();var item=candidate(id);
        assertThat(item.reason()).isEqualTo("UNRESOLVED_RECONCILIATION");
        assertThat(service.correct(url,UUID.randomUUID(),List.of(item)).correctedCount()).isZero();
        assertThat(repo.find(id).currentHolderId()).isNotNull();
    }
    @Test void correctionPreservesMoneyCodeRegistrySnapshotAndWritesJsonNullAuditOnce(){
        UUID id=seed(null,true);pay(id);upgrade();var before=repo.find(id);var item=candidate(id);
        var registry=repo.jdbc().queryForList("SELECT * FROM booking_code_registry WHERE booking_id=?",id);
        var guest=repo.jdbc().queryForList("SELECT * FROM counter_booking_guests WHERE booking_id=?",id);
        var receipt=repo.jdbc().queryForList("SELECT * FROM booking_payment_receipts WHERE booking_id=?",id);
        UUID actor=UUID.randomUUID();
        assertThat(service.correct(url,actor,List.of(item)).correctedCount()).isEqualTo(1);
        var after=repo.find(id);
        assertThat(after.currentHolderId()).isNull();assertThat(after.customerId()).isNull();
        assertThat(after.version()).isEqualTo(before.version()+1);assertThat(after.checkinTokenVersion()).isEqualTo(before.checkinTokenVersion()+1);
        assertThat(after.bookingCode()).isEqualTo(before.bookingCode());assertThat(after.status()).isEqualTo(before.status());
        assertThat(after.paymentId()).isEqualTo(before.paymentId());assertThat(after.paidAt()).isEqualTo(before.paidAt());
        assertThat(after.amount()).isEqualByComparingTo(before.amount());assertThat(after.priceSnapshot()).isEqualTo(before.priceSnapshot());
        assertThat(repo.jdbc().queryForList("SELECT * FROM booking_code_registry WHERE booking_id=?",id)).isEqualTo(registry);
        assertThat(repo.jdbc().queryForList("SELECT * FROM counter_booking_guests WHERE booking_id=?",id)).isEqualTo(guest);
        assertThat(repo.jdbc().queryForList("SELECT * FROM booking_payment_receipts WHERE booking_id=?",id)).isEqualTo(receipt);
        var audit=repo.history(id).stream().filter(h->h.action().equals("LEGACY_OWNERSHIP_CORRECTED")).findFirst().orElseThrow();
        assertThat(audit.actorId()).isEqualTo(actor);assertThat(audit.details().get("newCustomerId").isNull()).isTrue();assertThat(audit.details().get("newCurrentHolderId").isNull()).isTrue();
        assertThat(service.correct(url,actor,List.of(item)).reports().getFirst().status()).isEqualTo("SKIPPED_ALREADY_CORRECTED");
        assertThat(repo.find(id).version()).isEqualTo(after.version());assertThat(repo.history(id).stream().filter(h->h.action().equals("LEGACY_OWNERSHIP_CORRECTED")).count()).isEqualTo(1);
    }
    @Test void staleVersionAndTamperedManifestValuesAreSkipped(){
        UUID id=seed(null,true);upgrade();var c=candidate(id);
        var tampered=List.of(
            new Candidate(c.bookingId(),c.version(),UUID.randomUUID(),c.facilityId(),c.expectedOperatorId(),c.checkinTokenVersion(),c.category(),c.reason(),c.databaseIdentity(),c.evidenceFingerprint()),
            new Candidate(c.bookingId(),c.version(),c.courtId(),UUID.randomUUID(),c.expectedOperatorId(),c.checkinTokenVersion(),c.category(),c.reason(),c.databaseIdentity(),c.evidenceFingerprint()),
            new Candidate(c.bookingId(),c.version(),c.courtId(),c.facilityId(),UUID.randomUUID(),c.checkinTokenVersion(),c.category(),c.reason(),c.databaseIdentity(),c.evidenceFingerprint()),
            new Candidate(c.bookingId(),c.version(),c.courtId(),c.facilityId(),c.expectedOperatorId(),c.checkinTokenVersion()+1,c.category(),c.reason(),c.databaseIdentity(),c.evidenceFingerprint()),
            new Candidate(c.bookingId(),c.version(),c.courtId(),c.facilityId(),c.expectedOperatorId(),c.checkinTokenVersion(),"REVIEW_REQUIRED",c.reason(),c.databaseIdentity(),c.evidenceFingerprint()),
            new Candidate(c.bookingId(),c.version(),c.courtId(),c.facilityId(),c.expectedOperatorId(),c.checkinTokenVersion(),c.category(),c.reason(),"wrong database/schema",c.evidenceFingerprint()),
            new Candidate(c.bookingId(),c.version(),c.courtId(),c.facilityId(),c.expectedOperatorId(),c.checkinTokenVersion(),c.category(),c.reason(),c.databaseIdentity(),"0".repeat(64)));
        assertThat(service.correct(url,UUID.randomUUID(),tampered).reports()).allMatch(r->r.status().equals("SKIPPED_CHANGED"));
        repo.jdbc().update("UPDATE bookings SET version=version+1 WHERE id=?",id);
        assertThat(service.correct(url,UUID.randomUUID(),List.of(c)).reports().getFirst().status()).isEqualTo("SKIPPED_VERSION_MISMATCH");
        assertThat(repo.find(id).currentHolderId()).isEqualTo(c.expectedOperatorId());
    }
    @Test void evidenceChangedWithoutBookingVersionIsSkipped(){
        UUID id=seed(null,true);upgrade();var c=candidate(id);
        repo.history(id,UUID.randomUUID(),"MAINTENANCE_REVIEW",Map.of("reason","new evidence"));
        assertThat(service.correct(url,UUID.randomUUID(),List.of(c)).reports().getFirst().status()).isEqualTo("SKIPPED_CHANGED");
    }
    @Test void auditFailureRollsBackBothOwnerUpdates(){
        UUID id=seed(null,true);upgrade();var c=candidate(id);
        repo.jdbc().execute("CREATE FUNCTION reject_correction_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action='LEGACY_OWNERSHIP_CORRECTED' THEN RAISE EXCEPTION 'synthetic audit failure'; END IF; RETURN NEW; END $$");
        repo.jdbc().execute("CREATE TRIGGER reject_correction_audit BEFORE INSERT ON booking_history FOR EACH ROW EXECUTE FUNCTION reject_correction_audit()");
        assertThatThrownBy(()->service.correct(url,UUID.randomUUID(),List.of(c))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(repo.find(id).version()).isEqualTo(c.version());assertThat(repo.find(id).currentHolderId()).isEqualTo(c.expectedOperatorId());
        assertThat(repo.jdbc().queryForObject("SELECT holder_id FROM slot_reservations WHERE id=(SELECT reservation_id FROM bookings WHERE id=?)",UUID.class,id)).isEqualTo(c.expectedOperatorId());
    }
    @Test void preflightIsReadOnlyAndDoesNotExposeGuestContact(){
        UUID id=seed(null,true);upgrade();
        String before=repo.jdbc().queryForObject("SELECT to_jsonb(b)::text FROM bookings b WHERE id=?",String.class,id);
        var result=service.preflight(url);
        assertThat(repo.write(result)).doesNotContain("Synthetic Guest","0911223344");
        assertThat(repo.jdbc().queryForObject("SELECT to_jsonb(b)::text FROM bookings b WHERE id=?",String.class,id)).isEqualTo(before);
        assertThat(repo.history(id)).isEmpty();
    }
    @Test void groupAndAnyReleasedTransferEvidenceRequireReview(){
        UUID group=seed(null,true),transferred=seed(null,true);var b=repo.find(group);
        tx.executeWithoutResult(s->{
            repo.jdbc().update("INSERT INTO booking_groups(id,owner_id,name,state,deadline,max_members) VALUES(?,?,'Legacy group','GROUP_PENDING',?,1)",group,b.createdBy(),Timestamp.from(b.holdExpiresAt()));
            repo.jdbc().update("INSERT INTO group_members(id,group_id,user_id,display_name) VALUES(?,?,?,'Legacy owner')",UUID.randomUUID(),group,b.createdBy());
        });
        var t=repo.find(transferred);
        repo.jdbc().update("INSERT INTO transfer_leases(listing_id,booking_id,seller_id,price,deadline,state) VALUES(?,?,?,100000,?,'RELEASED')",UUID.randomUUID(),transferred,t.createdBy(),Timestamp.from(t.startsAt()));
        upgrade();assertThat(candidate(group).reason()).isEqualTo("GROUP_BOOKING");assertThat(candidate(transferred).reason()).isEqualTo("TRANSFER_EVIDENCE_EXISTS");
    }
    @Test void missingPaymentAndInvalidReceiptFingerprintRequireReview(){
        UUID unpaid=seed(null,true),mismatch=seed(null,true);pay(mismatch);
        tx.executeWithoutResult(s->{repo.jdbc().update("UPDATE slot_reservations SET state='BOOKED' WHERE id=(SELECT reservation_id FROM bookings WHERE id=?)",unpaid);repo.jdbc().update("UPDATE bookings SET status='CONFIRMED' WHERE id=?",unpaid);});
        repo.jdbc().update("UPDATE booking_payment_receipts SET fingerprint=? WHERE booking_id=?","0".repeat(64),mismatch);
        upgrade();assertThat(candidate(unpaid).reason()).isEqualTo("MISSING_PAYMENT_EVIDENCE");assertThat(candidate(mismatch).reason()).isEqualTo("PAYMENT_FINGERPRINT_MISMATCH");
    }
    @Test void correctionWaitsForCourtLockAndSkipsCallbackOrExpiryCommittedAfterPreflight()throws Exception{
        UUID paymentRace=seed(null,true),expiryRace=seed(null,true);upgrade();
        for(UUID id:List.of(paymentRace,expiryRace)){
            var c=candidate(id);var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
            try(var pool=Executors.newFixedThreadPool(2)){
                var writer=pool.submit(()->tx.executeWithoutResult(s->{
                    repo.lock(c.courtId());locked.countDown();
                    try{if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Writer release timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
                    if(id.equals(paymentRace))pay(id);
                    else new BookingLifecycle(repo,Clock.fixed(now.plusSeconds(301),ZoneOffset.UTC)).transition(repo.find(id),"EXPIRED",null,"EXPIRED",Map.of(),now.plusSeconds(301),null);
                }));
                try{
                    assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
                    var correction=pool.submit(()->service.correct(url,UUID.randomUUID(),List.of(c)));
                    long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);boolean waiting=false;
                    while(System.nanoTime()<deadline){if(repo.jdbc().queryForObject("SELECT count(*) FROM pg_locks WHERE locktype='advisory' AND NOT granted",Integer.class)>0){waiting=true;break;}Thread.sleep(20);}
                    assertThat(waiting).isTrue();assertThat(correction.isDone()).isFalse();release.countDown();writer.get(5,TimeUnit.SECONDS);
                    assertThat(correction.get(5,TimeUnit.SECONDS).reports().getFirst().status()).isEqualTo("SKIPPED_VERSION_MISMATCH");
                }finally{release.countDown();}
            }
            assertThat(repo.find(id).currentHolderId()).isEqualTo(c.expectedOperatorId());
            assertThat(repo.history(id)).noneMatch(h->h.action().equals("LEGACY_OWNERSHIP_CORRECTED"));
        }
    }
    @Test void legacyQrIsRejectedBeforeCorrectionButManualCodeStillChecksIn(){
        UUID id=seed(null,true);pay(id);upgrade();var b=repo.find(id);
        var clock=Clock.fixed(now,ZoneOffset.UTC);String secret="test-signing-key-longer-than-thirty-two-characters";
        var bookingService=new BookingService(repo,mock(BookingDependencies.class),600,5,90,secret,clock,null,null,null,new BookingLifecycle(repo,clock),10,mock(BookingRefundPolicies.class));
        var actor=new Caller(b.createdBy(),"Synthetic Staff Customer",Set.of("STAFF","CUSTOMER"),Map.of());
        String raw=b.id()+"|"+b.currentHolderId()+"|"+b.checkinTokenVersion()+"|"+now.plusSeconds(100).getEpochSecond();
        String qr=Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8))+"."+Signatures.hmac(secret,"CHECKIN|"+raw);
        assertThat(bookingService.detail(id,actor,"token").booking().currentHolderId()).isNull();
        assertThat(bookingService.detail(id,actor,"token").checkinToken()).isNull();
        assertThatThrownBy(()->tx.execute(s->bookingService.checkin(id,new CheckinInput(qr),actor,"token"))).isInstanceOf(ForbiddenException.class);
        assertThat(repo.find(id).status()).isEqualTo("CONFIRMED");
        var checked=tx.execute(s->bookingService.checkinByCode(b.facilityId(),new CodeInput(b.bookingCode()),actor,"token"));
        assertThat(checked.status()).isEqualTo("CHECKED_IN");assertThat(checked.currentHolderId()).isNull();
    }
}
