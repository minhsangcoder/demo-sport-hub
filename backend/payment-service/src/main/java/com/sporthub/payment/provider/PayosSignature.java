package com.sporthub.payment.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.sporthub.common.exception.ForbiddenException;
import com.sporthub.common.security.Signatures;
import java.util.*;

/** Native payOS alphabetic canonicalization; never maps a bank webhook into DEMO fields. */
public final class PayosSignature {
  private PayosSignature() {}

  public static String sign(JsonNode data, String key) {
    return Signatures.hmac(key, canonical(data));
  }

  public static void verify(JsonNode data, String signature, String key) {
    if (signature == null
        || !signature.matches("[a-fA-F0-9]{64}")
        || !Signatures.matches(sign(data, key), signature.toLowerCase(Locale.ROOT)))
      throw new ForbiddenException("Chữ ký payOS không hợp lệ.");
  }

  public static String canonical(JsonNode data) {
    if (data == null || !data.isObject())
      throw new IllegalArgumentException("Dữ liệu payOS không hợp lệ.");
    var names = new TreeSet<String>();
    data.fieldNames().forEachRemaining(names::add);
    var fields = new ArrayList<String>();
    for (String name : names) {
      JsonNode value = data.get(name);
      String text;
      if (value == null
          || value.isNull()
          || value.isTextual() && Set.of("null", "undefined").contains(value.asText())) text = "";
      else if (value.isArray()) {
        var sorted = JsonNodeFactory.instance.arrayNode();
        for (JsonNode item : value) sorted.add(sortObject(item));
        text = sorted.toString();
      } else if (value.isObject())
        throw new IllegalArgumentException("Cấu trúc chữ ký payOS chưa được hỗ trợ.");
      else text = value.asText();
      fields.add(name + "=" + text);
    }
    return String.join("&", fields);
  }

  private static JsonNode sortObject(JsonNode item) {
    if (!item.isObject()) return item;
    var result = JsonNodeFactory.instance.objectNode();
    var names = new TreeSet<String>();
    item.fieldNames().forEachRemaining(names::add);
    names.forEach(name -> result.set(name, item.get(name)));
    return result;
  }
}
