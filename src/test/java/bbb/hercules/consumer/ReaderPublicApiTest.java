package bbb.hercules.consumer;

import bbb.hercules.Reader;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Independent public consumer using Reader for immutable configuration queries. */
class ReaderPublicApiTest {

    record Settings(String host, int timeoutSeconds, String region, List<String> symbols) {
    }

    record Request(String endpoint, int timeoutSeconds, String instrument) {
    }

    @Test
    void buildsRequestBySharingTheSameSettingsEnvironment() {
        Reader<Settings, String> endpoint =
                Reader.asks(settings -> "https://" + settings.host() + "/prices");
        Reader<Settings, Integer> timeout = Reader.asks(Settings::timeoutSeconds);

        Reader<Settings, Request> request = endpoint.flatMap(url ->
                timeout.flatMap(seconds ->
                        Reader.<Settings, Request>asks(settings ->
                                new Request(url, seconds, settings.symbols().get(0)))));

        Settings poland = new Settings(
                "api.example.test", 15, "pl", List.of("BTCUSD", "ETHUSD"));
        Settings germany = new Settings(
                "eu.example.test", 30, "de", List.of("SOLUSD"));

        assertEquals(new Request("https://api.example.test/prices", 15, "BTCUSD"),
                request.run(poland));
        assertEquals(new Request("https://eu.example.test/prices", 30, "SOLUSD"),
                request.run(germany));
        assertEquals(poland, new Settings(
                "api.example.test", 15, "pl", List.of("BTCUSD", "ETHUSD")));
    }

    @Test
    void localOverridesConfigurationOnlyForOneReader() {
        Reader<Settings, String> currentRegion = Reader.asks(Settings::region);
        Reader<Settings, String> sandboxRegion = currentRegion.local(
                settings -> new Settings(
                        settings.host(), settings.timeoutSeconds(),
                        "sandbox", settings.symbols()));

        Settings original = new Settings("api.example.test", 10, "production", List.of("BTC"));
        assertEquals("sandbox", sandboxRegion.run(original));
        assertEquals("production", currentRegion.run(original));
        assertEquals("production", original.region());
    }

    @Test
    void composingReaderDoesNotAccessConfigurationUntilRun() {
        AtomicInteger calls = new AtomicInteger();
        Reader<Settings, Integer> selected = Reader.<Settings>ask()
                .map(settings -> {
                    calls.incrementAndGet();
                    return settings.timeoutSeconds();
                });

        assertEquals(0, calls.get());
        assertEquals(60, selected.run(
                new Settings("host", 60, "region", List.of())));
        assertEquals(1, calls.get());
    }
}
