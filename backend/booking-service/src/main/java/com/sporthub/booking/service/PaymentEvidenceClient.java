package com.sporthub.booking.service;

import com.fasterxml.jackson.databind.*;
import com.sporthub.common.money.PaymentProofFacts.*;
import com.sporthub.common.security.ServiceCalls;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@Component
public class PaymentEvidenceClient {
    public record Result(Map<UUID,Fact> facts,Map<UUID,String> problems,List<Instant> capturedAt,List<String> reasons,int batchCount) {}
    private record Batch(List<Binding> bindings,Response response,String error) {}
    private static final String PATH="/api/v1/internal/payments/report-facts";
    private final RestClient client;private final ObjectMapper json;private final String secret;private final long deadlineMs;private final int concurrency;
    public PaymentEvidenceClient(@Value("${PAYMENT_SERVICE_URL:http://localhost:8085}") String url,@Value("${SERVICE_CALL_SECRET}") String secret,ObjectMapper json,
                                 @Value("${reporting.payment-facts.deadline-ms:7000}") long deadlineMs,@Value("${reporting.payment-facts.concurrency:3}") int concurrency){
        if(deadlineMs<100||deadlineMs>30000||concurrency<1||concurrency>8)throw new IllegalArgumentException("Invalid report facts read budget");
        this.json=json;this.secret=secret;this.deadlineMs=deadlineMs;this.concurrency=concurrency;
        var factory=new org.springframework.http.client.JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofMillis(Math.min(1000,deadlineMs))).build());factory.setReadTimeout(Duration.ofMillis(Math.min(2000,deadlineMs)));client=RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }
    public Result read(List<Binding> bindings){
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Report network reads must run after the local snapshot transaction ends");
        var unique=new LinkedHashMap<UUID,Binding>();for(var binding:bindings){var prior=unique.putIfAbsent(binding.paymentId(),binding);if(prior!=null&&!prior.equals(binding))throw new IllegalArgumentException("Conflicting local payment bindings");}
        if(unique.isEmpty())return new Result(Map.of(),Map.of(),List.of(),List.of(),0);
        var all=new ArrayList<>(unique.values());var batches=new ArrayList<List<Binding>>();for(int offset=0;offset<all.size();offset+=500)batches.add(List.copyOf(all.subList(offset,Math.min(all.size(),offset+500))));
        var facts=new LinkedHashMap<UUID,Fact>();var problems=new LinkedHashMap<UUID,String>();var captured=new ArrayList<Instant>();var reasons=new TreeSet<String>();
        long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(deadlineMs);var gate=new Semaphore(concurrency);var pool=Executors.newVirtualThreadPerTaskExecutor();var completion=new ExecutorCompletionService<Batch>(pool);var futures=new ArrayList<Future<Batch>>();
        try{
            for(var batch:batches)futures.add(completion.submit(()->{
                boolean acquired=false;try{
                    long remaining=deadline-System.nanoTime();if(remaining<=0||!gate.tryAcquire(remaining,TimeUnit.NANOSECONDS))return new Batch(batch,null,"PAYMENT_FACTS_DEADLINE");acquired=true;
                    return new Batch(batch,request(batch),null);
                }catch(InterruptedException ex){Thread.currentThread().interrupt();return new Batch(batch,null,"PAYMENT_FACTS_DEADLINE");}
                catch(RuntimeException ex){return new Batch(batch,null,"PAYMENT_FACTS_UNAVAILABLE");}
                finally{if(acquired)gate.release();}
            }));
            for(int complete=0;complete<batches.size();complete++){
                long remaining=deadline-System.nanoTime();if(remaining<=0){reasons.add("PAYMENT_FACTS_DEADLINE");break;}
                var done=completion.poll(remaining,TimeUnit.NANOSECONDS);if(done==null){reasons.add("PAYMENT_FACTS_DEADLINE");break;}var result=done.get();
                if(result.error()!=null){reasons.add(result.error());result.bindings().forEach(b->problems.put(b.paymentId(),result.error()));continue;}
                try{merge(result,facts,problems,captured);}catch(RuntimeException ex){reasons.add("PAYMENT_FACTS_PROTOCOL_ERROR");result.bindings().forEach(b->{facts.remove(b.paymentId());problems.put(b.paymentId(),"PAYMENT_FACTS_PROTOCOL_ERROR");});}
            }
        }catch(InterruptedException ex){Thread.currentThread().interrupt();reasons.add("PAYMENT_FACTS_INTERRUPTED");}
        catch(ExecutionException ex){reasons.add("PAYMENT_FACTS_UNAVAILABLE");}
        finally{futures.forEach(f->f.cancel(true));pool.shutdownNow();}
        for(var binding:all)if(!facts.containsKey(binding.paymentId())&&!problems.containsKey(binding.paymentId()))problems.put(binding.paymentId(),"PAYMENT_FACTS_DEADLINE");
        return new Result(Collections.unmodifiableMap(facts),Collections.unmodifiableMap(problems),List.copyOf(captured),List.copyOf(reasons),batches.size());
    }
    private Response request(List<Binding> bindings){
        try{String body=json.writeValueAsString(new Request(bindings));long time=Instant.now().getEpochSecond();var response=client.post().uri(PATH).header("X-Service-Time",Long.toString(time)).header("X-Service-Signature",ServiceCalls.sign(secret,"POST",PATH,time,body)).contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
            if(response==null||!response.path("data").isObject())throw new IllegalStateException("Invalid facts envelope");return json.treeToValue(response.path("data"),Response.class);
        }catch(com.fasterxml.jackson.core.JsonProcessingException ex){throw new IllegalStateException("Invalid facts response",ex);}
    }
    private void merge(Batch batch,Map<UUID,Fact> facts,Map<UUID,String> problems,List<Instant> captured){
        var response=batch.response();if(response==null||response.capturedAt()==null||response.facts()==null||response.problems()==null)throw new IllegalStateException("Incomplete facts projection");
        var expected=new HashMap<UUID,Binding>();batch.bindings().forEach(b->expected.put(b.paymentId(),b));var seen=new HashSet<UUID>();
        for(var fact:response.facts()){
            var binding=expected.get(fact.paymentId());if(binding==null||!seen.add(fact.paymentId())||!binding.bookingId().equals(fact.bookingId())||!Objects.equals(binding.memberId(),fact.memberId())||!binding.purpose().equals(fact.purpose())||fact.verified()&&(fact.payerId()==null||fact.paidAt()==null||fact.outcomeFingerprint()==null||!fact.outcomeFingerprint().matches("[a-f0-9]{64}")||!Set.of("REAL","DEMO","UNKNOWN").contains(fact.moneyClass())))throw new IllegalStateException("Facts returned a different requested binding");
            facts.put(fact.paymentId(),fact);
        }
        for(var problem:response.problems()){if(!expected.containsKey(problem.paymentId())||!seen.add(problem.paymentId()))throw new IllegalStateException("Unexpected facts problem binding");problems.put(problem.paymentId(),problem.reason());}
        expected.keySet().stream().filter(id->!seen.contains(id)).forEach(id->problems.put(id,"PAYMENT_FACT_MISSING"));captured.add(response.capturedAt());
    }
}
