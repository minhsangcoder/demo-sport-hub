package com.sporthub.booking;

import com.sporthub.booking.service.BookingDependencies;
import com.sporthub.common.security.RemoteIdentity.Caller;
import com.sporthub.common.exception.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class BookingDependenciesSecurityTest {
    @Test void eachPermissionIsExplicitAndFacilityScopedAndRolesCannotBeSpoofedByBindings() {
        UUID facility=UUID.randomUUID(),other=UUID.randomUUID();
        var dependencies=new BookingDependencies("http://unused.invalid","http://unused.invalid");
        for(String permission:List.of("BOOKING_READ","BOOKING_CREATE_COUNTER","BOOKING_CHECK_IN","BOOKING_COMPLETE","SCHEDULE_READ")) {
            var staff=new Caller(UUID.randomUUID(),"Staff",Set.of("STAFF"),Map.of(facility,Set.of(permission)));
            assertThatCode(()->dependencies.facilityPermission(facility,staff,"token",permission)).doesNotThrowAnyException();
            assertThatThrownBy(()->dependencies.facilityPermission(other,staff,"token",permission)).isInstanceOf(ForbiddenException.class);
            for(String role:List.of("CUSTOMER","ADMIN")) {
                var wrongRole=new Caller(staff.id(),role,Set.of(role),staff.facilityBindings());
                assertThatThrownBy(()->dependencies.facilityPermission(facility,wrongRole,"token",permission)).isInstanceOf(ForbiddenException.class);
            }
            String otherPermission=permission.equals("BOOKING_READ")?"BOOKING_COMPLETE":"BOOKING_READ";
            assertThatThrownBy(()->dependencies.facilityPermission(facility,staff,"token",otherPermission)).isInstanceOf(ForbiddenException.class);
        }
    }

    @Test void ownerRequiresRealFacilityAccessAndAnInvalidSessionCannotFallBackToStaff() throws Exception {
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        var status=new java.util.concurrent.atomic.AtomicInteger(200);
        server.createContext("/api/v1/owner/facilities/",exchange->{
            int response="Bearer synthetic-owner".equals(exchange.getRequestHeaders().getFirst("Authorization"))?status.get():401;
            exchange.sendResponseHeaders(response,-1);exchange.close();
        });server.start();
        try {
            String base="http://127.0.0.1:"+server.getAddress().getPort();var dependencies=new BookingDependencies(base,base);UUID facility=UUID.randomUUID();
            var owner=new Caller(UUID.randomUUID(),"Owner",Set.of("OWNER"),Map.of());
            assertThatCode(()->dependencies.facilityPermission(facility,owner,"Bearer synthetic-owner","BOOKING_READ")).doesNotThrowAnyException();
            status.set(403);assertThatThrownBy(()->dependencies.facilityPermission(facility,owner,"Bearer synthetic-owner","BOOKING_READ")).isInstanceOf(ForbiddenException.class);
            var dual=new Caller(owner.id(),"Owner+Staff",Set.of("OWNER","STAFF"),Map.of(facility,Set.of("BOOKING_READ")));
            assertThatCode(()->dependencies.facilityPermission(facility,dual,"Bearer synthetic-owner","BOOKING_READ")).doesNotThrowAnyException();
            assertThatThrownBy(()->dependencies.facilityPermission(facility,dual,"Bearer invalid","BOOKING_READ")).isInstanceOf(UnauthorizedException.class);
        }finally{server.stop(0);}
    }
}
