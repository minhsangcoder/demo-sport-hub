package com.sporthub.booking.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** NAPAS account transfer profile only: EMV TLV, QRIBFTTA and CRC16/CCITT-FALSE. */
@Component
public class VietQrTransfer {
  public String encode(String bin, String account, BigDecimal amount, String memo) {
    if (bin == null
        || !bin.matches("[0-9]{6}")
        || account == null
        || !account.matches("[0-9]{1,19}")
        || amount == null
        || amount.signum() <= 0
        || amount.stripTrailingZeros().scale() > 0
        || amount.precision() > 20
        || memo == null
        || !memo.matches("[A-Z0-9 ]{1,25}"))
      throw new IllegalArgumentException("Thông tin VietQR chuyển khoản không hợp lệ.");
    String beneficiary = tlv("00", bin) + tlv("01", account);
    String merchant = tlv("00", "A000000727") + tlv("01", beneficiary) + tlv("02", "QRIBFTTA");
    String payload =
        tlv("00", "01")
            + tlv("01", "12")
            + tlv("38", merchant)
            + tlv("53", "704")
            + tlv("54", amount.toBigIntegerExact().toString())
            + tlv("58", "VN")
            + tlv("62", tlv("08", memo))
            + "6304";
    return payload + crc(payload);
  }

  private String tlv(String tag, String value) {
    int length = value.getBytes(StandardCharsets.US_ASCII).length;
    if (length > 99) throw new IllegalArgumentException("VietQR field too long");
    return tag + String.format(Locale.ROOT, "%02d", length) + value;
  }

  public static String crc(String value) {
    int crc = 0xffff;
    for (byte b : value.getBytes(StandardCharsets.US_ASCII)) {
      crc ^= (b & 0xff) << 8;
      for (int bit = 0; bit < 8; bit++)
        crc = ((crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1) & 0xffff;
    }
    return String.format(Locale.ROOT, "%04X", crc);
  }
}
