package com.sporthub.booking.web;
import com.sporthub.booking.service.BookingDemoFinance;
import com.sporthub.common.dto.ApiResponse;
import com.sporthub.common.security.RemoteIdentity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;
@RestController @RequestMapping("/api/v1/admin/bookings/demo-finance")
public class DemoFinanceMonitoringController {
 private final BookingDemoFinance finance;private final RemoteIdentity identity;
 public DemoFinanceMonitoringController(BookingDemoFinance finance,RemoteIdentity identity){this.finance=finance;this.identity=identity;}
 @GetMapping public ApiResponse<Map<String,Object>> read(@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to,HttpServletRequest r){return ApiResponse.success(finance.monitor(from,to,identity.current(r)));}
}
