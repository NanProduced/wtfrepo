package com.wtfrepo.backend.shared.id;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Lightweight ULID generator for ordered event identifiers. */
@Component
public class UlidGenerator {

  private static final char[] ENCODING =
      "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

  private final SecureRandom random = new SecureRandom();

  public String next() {
    long timestamp = System.currentTimeMillis();
    byte[] bytes = new byte[16];
    bytes[0] = (byte) (timestamp >>> 40);
    bytes[1] = (byte) (timestamp >>> 32);
    bytes[2] = (byte) (timestamp >>> 24);
    bytes[3] = (byte) (timestamp >>> 16);
    bytes[4] = (byte) (timestamp >>> 8);
    bytes[5] = (byte) timestamp;
    byte[] randomness = new byte[10];
    random.nextBytes(randomness);
    System.arraycopy(randomness, 0, bytes, 6, randomness.length);
    return encode(bytes);
  }

  private String encode(byte[] data) {
    char[] chars = new char[26];
    int index = 0;
    int buffer = data[0] & 0xFF;
    int bitsLeft = 8;
    int dataIndex = 1;

    while (index < chars.length) {
      if (bitsLeft < 5) {
        if (dataIndex < data.length) {
          buffer = (buffer << 8) | (data[dataIndex++] & 0xFF);
          bitsLeft += 8;
        } else {
          buffer <<= (5 - bitsLeft);
          bitsLeft = 5;
        }
      }
      int value = (buffer >> (bitsLeft - 5)) & 0x1F;
      bitsLeft -= 5;
      chars[index++] = ENCODING[value];
    }
    return new String(chars);
  }
}
