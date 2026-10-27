package com.sporthub.booking.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.sporthub.booking.service.BookingQuotes;
import com.sporthub.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookings/quote")
public class BookingQuoteController {
  private final BookingQuotes quotes;

  public BookingQuoteController(BookingQuotes quotes) {
    this.quotes = quotes;
  }

  @PostMapping
  public ApiResponse<JsonNode> quote(@Valid @RequestBody BookingDtos.QuoteInput input) {
    return ApiResponse.success(quotes.quote(input));
  }
}
