package com.sporthub.common.security;

import com.fasterxml.jackson.databind.*;
import java.util.TreeSet;

/** Stable command fingerprints across JSONB round trips, map implementations and JVM restarts. */
public final class CanonicalCommandJson {
  private CanonicalCommandJson() {}

  public static String write(ObjectMapper json, Object value) {
    return ordered(json, json.valueToTree(value)).toString();
  }

  private static JsonNode ordered(ObjectMapper json, JsonNode value) {
    if (value.isObject()) {
      var result = json.createObjectNode();
      var names = new TreeSet<String>();
      value.fieldNames().forEachRemaining(names::add);
      names.forEach(name -> result.set(name, ordered(json, value.get(name))));
      return result;
    }
    if (value.isArray()) {
      var result = json.createArrayNode();
      value.forEach(item -> result.add(ordered(json, item)));
      return result;
    }
    return value;
  }
}
