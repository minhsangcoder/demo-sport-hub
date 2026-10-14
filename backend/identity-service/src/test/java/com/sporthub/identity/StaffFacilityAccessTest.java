package com.sporthub.identity;
import com.sporthub.identity.service.StaffFacilityAccess;
import com.sporthub.identity.security.AuthenticatedUser;
import com.sporthub.identity.domain.Role;
import com.sporthub.common.exception.ForbiddenException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class StaffFacilityAccessTest {
 private HttpServer server;private StaffFacilityAccess access;private int status;private String body;
 private final AtomicInteger calls=new AtomicInteger();private UUID facility,owner;
 @BeforeEach void start() throws Exception {
  facility=UUID.randomUUID();owner=UUID.randomUUID();status=200;body="{\"data\":{\"id\":\""+facility+"\",\"ownerId\":\""+owner+"\"}}";
  server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",e->{calls.incrementAndGet();assertThat(e.getRequestURI().getPath()).isEqualTo("/api/v1/owner/facilities/"+facility);assertThat(e.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer synthetic");byte[] content=body.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().add("Content-Type","application/json");e.sendResponseHeaders(status,content.length);e.getResponseBody().write(content);e.close();});server.start();access=new StaffFacilityAccess("http://127.0.0.1:"+server.getAddress().getPort());
 }
 @AfterEach void stop(){server.stop(0);}
 private AuthenticatedUser actor(Role role){return new AuthenticatedUser(owner,"synthetic@example.test",Set.of(role),UUID.randomUUID());}
 @Test void ownerMustMatchFacilityAuthorityResponse(){assertThatCode(()->access.owner(facility,actor(Role.OWNER),"Bearer synthetic")).doesNotThrowAnyException();body="{\"data\":{\"id\":\""+facility+"\",\"ownerId\":\""+UUID.randomUUID()+"\"}}";assertThatThrownBy(()->access.owner(facility,actor(Role.OWNER),"Bearer synthetic")).isInstanceOf(ForbiddenException.class);}
 @Test void customerStaffAdminAndMissingBearerNeverCallFacility(){for(var role:List.of(Role.CUSTOMER,Role.STAFF,Role.ADMIN))assertThatThrownBy(()->access.owner(facility,actor(role),"Bearer synthetic")).isInstanceOf(ForbiddenException.class);assertThatThrownBy(()->access.owner(facility,actor(Role.OWNER),null)).isInstanceOf(ForbiddenException.class);assertThat(calls).hasValue(0);}
 @Test void missingForeignOrMalformedPayloadNeverAuthorizesOwner(){for(var value:List.of("{}","{\"data\":{}}","{\"data\":{\"id\":\""+UUID.randomUUID()+"\",\"ownerId\":\""+owner+"\"}}")){body=value;assertThatThrownBy(()->access.owner(facility,actor(Role.OWNER),"Bearer synthetic")).isInstanceOf(ForbiddenException.class);}body="not-json";assertThatThrownBy(()->access.owner(facility,actor(Role.OWNER),"Bearer synthetic")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);}
 @Test void dependencyFailuresDenyAndDoNotLeakResponseSecrets(){status=403;body="{\"password\":\"secret-response\"}";assertThatThrownBy(()->access.owner(facility,actor(Role.OWNER),"Bearer synthetic")).isInstanceOf(ForbiddenException.class).hasMessageNotContaining("secret-response");status=500;assertThatThrownBy(()->access.owner(facility,actor(Role.OWNER),"Bearer synthetic")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageNotContaining("secret-response");}
}
