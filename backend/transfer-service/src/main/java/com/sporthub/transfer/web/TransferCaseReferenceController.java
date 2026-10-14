package com.sporthub.transfer.web;
import com.sporthub.transfer.repository.TransferRepository;
import com.sporthub.common.security.RemoteIdentity;
import com.sporthub.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
/** Admin-only original references for UC-8.11, including historical acquisitions. */
@RestController @RequestMapping("/api/v1/transfers/admin/listings")
public class TransferCaseReferenceController {
 private final TransferRepository repo;private final RemoteIdentity identity;
 public TransferCaseReferenceController(TransferRepository repo,RemoteIdentity identity){this.repo=repo;this.identity=identity;}
 @GetMapping("/{id}/reference") @Transactional(readOnly=true) public ApiResponse<Map<String,Object>> reference(@PathVariable UUID id,@RequestParam(required=false) UUID acquisitionId,HttpServletRequest r){identity.current(r).requireRole("ADMIN");var listing=repo.find(id);var result=new LinkedHashMap<String,Object>();result.put("listingId",listing.id());result.put("bookingId",listing.bookingId());result.put("sellerId",listing.sellerId());result.put("listingState",listing.state());result.put("workflow",listing.workflow());if(acquisitionId!=null){var acquisition=repo.acquisition(acquisitionId);if(!acquisition.listingId().equals(id))throw new com.sporthub.common.exception.ResourceNotFoundException("Acquisition does not belong to the listing");result.put("acquisition",acquisition);}return ApiResponse.success(result);}
}
