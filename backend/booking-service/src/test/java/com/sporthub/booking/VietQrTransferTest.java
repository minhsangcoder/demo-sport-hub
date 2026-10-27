package com.sporthub.booking;

import static org.assertj.core.api.Assertions.*;

import com.google.zxing.*;
import com.google.zxing.common.*;
import com.sporthub.booking.service.*;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class VietQrTransferTest {
  @Test
  void crcMatchesIndependentCheckVectorAndAccountTransferHasCorrectNestedFields() throws Exception {
    assertThat(VietQrTransfer.crc("123456789")).isEqualTo("29B1");
    String payload =
        new VietQrTransfer().encode("970468", "0011009950446", new BigDecimal("95000"), "SH PA 1");
    // Published NAPAS 1.5.2 account-transfer example, independent of our encoder.
    assertThat(payload).contains("38570010A00000072701270006970468011300110099504460208QRIBFTTA");
    var tags = parse(payload);
    assertThat(tags.get("54")).isEqualTo("95000");
    assertThat(tags.get("53")).isEqualTo("704");
    assertThat(tags.get("58")).isEqualTo("VN");
    var merchant = parse(tags.get("38"));
    assertThat(merchant.get("00")).isEqualTo("A000000727");
    assertThat(merchant.get("02")).isEqualTo("QRIBFTTA");
    var beneficiary = parse(merchant.get("01"));
    assertThat(beneficiary).containsEntry("00", "970468").containsEntry("01", "0011009950446");
    assertThat(parse(tags.get("62")).get("08")).isEqualTo("SH PA 1");
    var image =
        ImageIO.read(
            new ByteArrayInputStream(
                Base64.getDecoder().decode(new CheckinQr().pngDataUrl(payload).split(",", 2)[1])));
    int w = image.getWidth(), h = image.getHeight();
    int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
    String decoded =
        new MultiFormatReader()
            .decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(w, h, pixels))))
            .getText();
    assertThat(decoded).isEqualTo(payload);
    assertThat(VietQrTransfer.crc(decoded.substring(0, decoded.length() - 4)))
        .isEqualTo(tags.get("63"));
  }

  @Test
  void invalidOrZeroInputsNeverRenderAnOutgoingQr() {
    var encoder = new VietQrTransfer();
    assertThatThrownBy(() -> encoder.encode("970436", "001", BigDecimal.ZERO, "SH PA 1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> encoder.encode("970436", "1e10", BigDecimal.ONE, "SH PA 1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> encoder.encode("970436", "001", BigDecimal.ONE, "Hoàn tiền"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private Map<String, String> parse(String payload) {
    var result = new LinkedHashMap<String, String>();
    for (int index = 0; index < payload.length(); ) {
      String tag = payload.substring(index, index + 2);
      int size = Integer.parseInt(payload.substring(index + 2, index + 4));
      assertThat(result).doesNotContainKey(tag);
      result.put(tag, payload.substring(index + 4, index + 4 + size));
      index += 4 + size;
    }
    return result;
  }
}
