package com.sporthub.identity;

import com.sporthub.identity.service.LoginAttemptLimiter;
import com.sporthub.identity.exception.IdentityException;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class LoginAttemptLimiterIntegrationTest {
    static GenericContainer<?> container;
    static LettuceConnectionFactory connections;
    static StringRedisTemplate redis;
    @BeforeAll static void start() {
        String externalPort=System.getenv("SPORTHUB_SECURITY_TEST_REDIS_PORT");
        String host="127.0.0.1"; int port;
        if(externalPort!=null) port=Integer.parseInt(externalPort);
        else {container=new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);container.start();host=container.getHost();port=container.getMappedPort(6379);}
        connections=new LettuceConnectionFactory(host,port);connections.afterPropertiesSet();connections.start();
        redis=new StringRedisTemplate(connections);
    }
    @AfterAll static void close() { if(connections!=null)connections.destroy();if(container!=null)container.stop(); }

    @Test void concurrentInstancesShareOneAtomicBudgetAndSuccessfulLoginResetsIt() throws Exception {
        String identifier="parallel-security-"+UUID.randomUUID()+"@test.invalid";
        var first=new LoginAttemptLimiter(redis,5,60);var second=new LoginAttemptLimiter(redis,5,60);
        var ready=new CountDownLatch(20);var go=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(20);
        try {
            var futures=new ArrayList<Future<Boolean>>();
            for(int i=0;i<20;i++){var instance=i%2==0?first:second;futures.add(pool.submit(()->{ready.countDown();go.await();try{instance.reserve(identifier);return true;}catch(IdentityException ex){assertThat(ex.getStatus().value()).isEqualTo(429);return false;}}));}
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();go.countDown();int allowed=0;
            for(var f:futures)if(f.get(15,TimeUnit.SECONDS))allowed++;
            assertThat(allowed).isEqualTo(5);
            first.succeeded(identifier.toUpperCase(Locale.ROOT));
            assertThatCode(()->second.reserve(identifier)).doesNotThrowAnyException();
        }finally{go.countDown();pool.shutdownNow();}
    }
    @Test void budgetExpiresAndAnotherIdentifierIsIndependent() throws Exception {
        var limiter=new LoginAttemptLimiter(redis,1,1);String identifier=UUID.randomUUID().toString();
        limiter.reserve(identifier);assertThatThrownBy(()->limiter.reserve(identifier)).isInstanceOf(IdentityException.class);
        assertThatCode(()->limiter.reserve(UUID.randomUUID().toString())).doesNotThrowAnyException();
        Thread.sleep(1200);assertThatCode(()->limiter.reserve(identifier)).doesNotThrowAnyException();
    }
    @Test void redisFailureCannotPermitUnmeteredLoginOrLeakItsException() {
        var unavailable=org.mockito.Mockito.mock(StringRedisTemplate.class);
        org.mockito.Mockito.when(unavailable.execute(org.mockito.ArgumentMatchers.any(org.springframework.data.redis.core.script.RedisScript.class),
                org.mockito.ArgumentMatchers.anyList(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("private redis credential"));
        var limiter=new LoginAttemptLimiter(unavailable,5,60);
        assertThatThrownBy(()->limiter.reserve("test"))
                .isInstanceOfSatisfying(IdentityException.class,ex->assertThat(ex.getStatus().value()).isEqualTo(503))
                .hasMessageNotContaining("private redis credential");
    }
}
