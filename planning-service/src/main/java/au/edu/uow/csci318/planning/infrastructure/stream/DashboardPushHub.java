package au.edu.uow.csci318.planning.infrastructure.stream;

import au.edu.uow.csci318.planning.application.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Authenticated SSE updates. No token is placed in a URL; one-minute connections require fresh
 * reauthentication.
 */
@Component
public class DashboardPushHub {
  private final DashboardQueryService queries;
  private final Clock clock;
  private final Map<UUID, Set<Client>> clients = new ConcurrentHashMap<>();

  public DashboardPushHub(DashboardQueryService queries, Clock clock) {
    this.queries = queries;
    this.clock = clock;
  }

  public SseEmitter subscribe(UUID ownerId, ZoneId zone) {
    SseEmitter emitter = new SseEmitter(60_000L);
    Client client = new Client(ownerId, zone, emitter);
    Set<Client> subscriptions =
        clients.computeIfAbsent(ownerId, ignored -> ConcurrentHashMap.newKeySet());
    if (subscriptions.size() >= 5)
      throw new IllegalStateException("Too many live dashboard connections");
    subscriptions.add(client);
    Runnable remove =
        () -> {
          subscriptions.remove(client);
          if (subscriptions.isEmpty()) clients.remove(ownerId, subscriptions);
        };
    emitter.onCompletion(remove);
    emitter.onTimeout(
        () -> {
          remove.run();
          emitter.complete();
        });
    emitter.onError(error -> remove.run());
    send(client);
    return emitter;
  }

  @TransactionalEventListener
  public void changed(ProjectionChanged event) {
    clients.getOrDefault(event.ownerId(), Set.of()).forEach(this::send);
  }

  @Scheduled(fixedDelay = 20_000)
  public void heartbeat() {
    clients
        .values()
        .forEach(
            set ->
                set.forEach(
                    client -> {
                      LocalDate today = LocalDate.now(clock.withZone(client.zone));
                      if (!today.equals(client.lastDate)) {
                        send(client);
                        return;
                      }
                      synchronized (client) {
                        try {
                          client.emitter.send(SseEmitter.event().comment("keepalive"));
                        } catch (Exception exception) {
                          client.emitter.completeWithError(exception);
                        }
                      }
                    }));
  }

  private void send(Client client) {
    synchronized (client) {
      try {
        client.emitter.send(
            SseEmitter.event()
                .name("dashboard")
                .data(queries.snapshot(client.ownerId, client.zone)));
        client.lastDate = LocalDate.now(clock.withZone(client.zone));
      } catch (Exception exception) {
        client.emitter.completeWithError(exception);
      }
    }
  }

  private static final class Client {
    final UUID ownerId;
    final ZoneId zone;
    final SseEmitter emitter;
    volatile LocalDate lastDate;

    Client(UUID owner, ZoneId zone, SseEmitter emitter) {
      ownerId = owner;
      this.zone = zone;
      this.emitter = emitter;
    }
  }
}
