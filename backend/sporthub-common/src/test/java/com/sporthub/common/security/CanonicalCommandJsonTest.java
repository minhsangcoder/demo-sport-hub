package com.sporthub.common.security;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;

class CanonicalCommandJsonTest {
  @Test
  void differentMapAndJsonbFieldOrdersHaveTheSameCommandIdentityButArraysRemainOrdered()
      throws Exception {
    var json = new ObjectMapper();
    var first = new LinkedHashMap<String, Object>();
    first.put("receipt", Map.of("hash", "a", "ids", List.of("1", "2")));
    first.put("input", Map.of("version", 0, "amount", "80000"));
    var reordered =
        json.readTree(
            "{\"input\":{\"amount\":\"80000\",\"version\":0},\"receipt\":{\"ids\":[\"1\",\"2\"],\"hash\":\"a\"}}");
    assertEquals(
        CanonicalCommandJson.write(json, first), CanonicalCommandJson.write(json, reordered));
    assertNotEquals(
        CanonicalCommandJson.write(json, first),
        CanonicalCommandJson.write(
            json, json.readTree(reordered.toString().replace("[\"1\",\"2\"]", "[\"2\",\"1\"]"))));
  }
}
