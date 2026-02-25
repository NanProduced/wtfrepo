package com.wtfrepo.backend.notifications.application;

import com.wtfrepo.backend.notifications.application.support.NotificationsConstants;
import com.wtfrepo.backend.notifications.application.support.NotificationsExceptions;
import com.wtfrepo.backend.shared.id.UlidGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Manages SSE streams and dispatches Redis pub/sub events to active connections. */
@Service
public class NotificationStreamService {

  private static final String CHANNEL_PAGER = "pager";
  private static final String CHANNEL_WALLET = "wallet";
  private static final String CHANNEL_NARRATOR = "narrator";
  private static final String CHANNEL_TICKER = "ticker";

  private static final String PREFIX_PAGER = "pager:";
  private static final String PREFIX_WALLET = "wallet:";
  private static final String PREFIX_NARRATOR = "narrator";
  private static final String PREFIX_TICKER = "ticker";

  private final Map<SseEmitter, StreamRegistration> registrations = new ConcurrentHashMap<>();
  private final Map<String, Set<SseEmitter>> pagerEmitters = new ConcurrentHashMap<>();
  private final Map<String, Set<SseEmitter>> walletEmitters = new ConcurrentHashMap<>();
  private final Set<SseEmitter> narratorEmitters = ConcurrentHashMap.newKeySet();
  private final Set<SseEmitter> tickerEmitters = ConcurrentHashMap.newKeySet();

  private final UlidGenerator ulidGenerator;

  public NotificationStreamService(UlidGenerator ulidGenerator) {
    this.ulidGenerator = ulidGenerator;
  }

  public SseEmitter openStream(String userId, Set<String> channels) {
    SseEmitter emitter = new SseEmitter(0L);
    StreamRegistration registration = new StreamRegistration(userId, channels);
    registrations.put(emitter, registration);
    registerEmitter(emitter, registration);

    emitter.onCompletion(() -> unregister(emitter));
    emitter.onTimeout(() -> unregister(emitter));
    emitter.onError(error -> unregister(emitter));
    return emitter;
  }

  public Set<String> resolveChannels(String rawChannels, boolean authenticated) {
    EnumSet<StreamChannel> allowed = authenticated ? StreamChannel.all() : StreamChannel.publicOnly();
    if (!StringUtils.hasText(rawChannels)) {
      return StreamChannel.toNames(allowed);
    }

    EnumSet<StreamChannel> requested = EnumSet.noneOf(StreamChannel.class);
    for (String token : rawChannels.split(",")) {
      if (!StringUtils.hasText(token)) {
        continue;
      }
      StreamChannel channel = StreamChannel.from(token.trim());
      if (channel == null) {
        throw NotificationsExceptions.invalidChannel(NotificationsConstants.Message.INVALID_CHANNEL);
      }
      requested.add(channel);
    }

    if (requested.isEmpty()) {
      return StreamChannel.toNames(allowed);
    }
    if (!allowed.containsAll(requested)) {
      throw NotificationsExceptions.invalidChannel(NotificationsConstants.Message.INVALID_CHANNEL);
    }
    return StreamChannel.toNames(requested);
  }

  public void dispatch(String channel, String payload) {
    if (!StringUtils.hasText(channel) || payload == null) {
      return;
    }
    String normalizedChannel = channel.trim();

    if (normalizedChannel.startsWith(PREFIX_PAGER)) {
      String userId = normalizedChannel.substring(PREFIX_PAGER.length());
      dispatchToUserChannel(pagerEmitters, userId, CHANNEL_PAGER, payload);
      return;
    }
    if (normalizedChannel.startsWith(PREFIX_WALLET)) {
      String userId = normalizedChannel.substring(PREFIX_WALLET.length());
      dispatchToUserChannel(walletEmitters, userId, CHANNEL_WALLET, payload);
      return;
    }
    if (normalizedChannel.startsWith(PREFIX_NARRATOR)) {
      dispatchToGlobalChannel(narratorEmitters, CHANNEL_NARRATOR, payload);
      return;
    }
    if (normalizedChannel.startsWith(PREFIX_TICKER)) {
      dispatchToGlobalChannel(tickerEmitters, CHANNEL_TICKER, payload);
      return;
    }
  }

  @Scheduled(fixedDelayString = "#{@notificationsPolicyProperties.getSseHeartbeatSeconds() * 1000}")
  public void sendHeartbeat() {
    if (registrations.isEmpty()) {
      return;
    }
    String eventId = nextEventId();
    String payload = "{\"ts\":\"" + Instant.now().toString() + "\"}";

    List<SseEmitter> failed = new ArrayList<>();
    for (SseEmitter emitter : registrations.keySet()) {
      try {
        emitter.send(SseEmitter.event().id(eventId).name("heartbeat").data(payload));
      } catch (Exception ex) {
        failed.add(emitter);
      }
    }
    failed.forEach(this::unregister);
  }

  private void registerEmitter(SseEmitter emitter, StreamRegistration registration) {
    for (String channel : registration.channels()) {
      switch (channel) {
        case CHANNEL_PAGER -> addToUserMap(pagerEmitters, registration.userId(), emitter);
        case CHANNEL_WALLET -> addToUserMap(walletEmitters, registration.userId(), emitter);
        case CHANNEL_NARRATOR -> narratorEmitters.add(emitter);
        case CHANNEL_TICKER -> tickerEmitters.add(emitter);
        default -> {}
      }
    }
  }

  private void unregister(SseEmitter emitter) {
    StreamRegistration registration = registrations.remove(emitter);
    if (registration == null) {
      return;
    }
    for (String channel : registration.channels()) {
      switch (channel) {
        case CHANNEL_PAGER -> removeFromUserMap(pagerEmitters, registration.userId(), emitter);
        case CHANNEL_WALLET -> removeFromUserMap(walletEmitters, registration.userId(), emitter);
        case CHANNEL_NARRATOR -> narratorEmitters.remove(emitter);
        case CHANNEL_TICKER -> tickerEmitters.remove(emitter);
        default -> {}
      }
    }
  }

  private void addToUserMap(Map<String, Set<SseEmitter>> map, String userId, SseEmitter emitter) {
    if (!StringUtils.hasText(userId)) {
      return;
    }
    map.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(emitter);
  }

  private void removeFromUserMap(Map<String, Set<SseEmitter>> map, String userId, SseEmitter emitter) {
    if (!StringUtils.hasText(userId)) {
      return;
    }
    Set<SseEmitter> emitters = map.get(userId);
    if (emitters == null) {
      return;
    }
    emitters.remove(emitter);
    if (emitters.isEmpty()) {
      map.remove(userId);
    }
  }

  private void dispatchToUserChannel(
      Map<String, Set<SseEmitter>> map, String userId, String eventName, String payload) {
    if (!StringUtils.hasText(userId)) {
      return;
    }
    Set<SseEmitter> targets = map.get(userId.trim());
    if (targets == null || targets.isEmpty()) {
      return;
    }
    sendToEmitters(targets, eventName, payload);
  }

  private void dispatchToGlobalChannel(Set<SseEmitter> targets, String eventName, String payload) {
    if (targets == null || targets.isEmpty()) {
      return;
    }
    sendToEmitters(targets, eventName, payload);
  }

  private void sendToEmitters(Set<SseEmitter> targets, String eventName, String payload) {
    List<SseEmitter> failed = new ArrayList<>();
    String eventId = nextEventId();
    for (SseEmitter emitter : targets) {
      try {
        emitter.send(SseEmitter.event().id(eventId).name(eventName).data(payload));
      } catch (Exception ex) {
        failed.add(emitter);
      }
    }
    failed.forEach(this::unregister);
  }

  private String nextEventId() {
    return ulidGenerator.next();
  }

  private record StreamRegistration(String userId, Set<String> channels) {}

  private enum StreamChannel {
    PAGER(CHANNEL_PAGER, true),
    WALLET(CHANNEL_WALLET, true),
    NARRATOR(CHANNEL_NARRATOR, false),
    TICKER(CHANNEL_TICKER, false);

    private final String value;
    private final boolean privateChannel;

    StreamChannel(String value, boolean privateChannel) {
      this.value = value;
      this.privateChannel = privateChannel;
    }

    static StreamChannel from(String raw) {
      if (!StringUtils.hasText(raw)) {
        return null;
      }
      String normalized = raw.trim().toLowerCase();
      for (StreamChannel channel : values()) {
        if (channel.value.equals(normalized)) {
          return channel;
        }
      }
      return null;
    }

    static EnumSet<StreamChannel> all() {
      return EnumSet.allOf(StreamChannel.class);
    }

    static EnumSet<StreamChannel> publicOnly() {
      EnumSet<StreamChannel> result = EnumSet.noneOf(StreamChannel.class);
      for (StreamChannel channel : values()) {
        if (!channel.privateChannel) {
          result.add(channel);
        }
      }
      return result;
    }

    static Set<String> toNames(EnumSet<StreamChannel> channels) {
      Set<String> names = ConcurrentHashMap.newKeySet();
      for (StreamChannel channel : channels) {
        names.add(channel.value);
      }
      return names;
    }
  }
}
