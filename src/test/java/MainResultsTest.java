import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(Resources.SYSTEM_OUT)
class MainResultsTest {
    private static final String REPOS = "https://api.github.com/users/example/repos";
    private static final String EVENTS = "https://api.github.com/users/example/events?per_page=100";
    private static final String EVENT = """
            {"id":"1","type":"WatchEvent","repo":{"name":"example/project"},
             "created_at":"2026-11-24T12:00:00Z"}
            """;

    @Test
    void repositoryNetworkFailureStillDisplaysFetchedActivity() throws Exception {
        ScriptedClient client = new ScriptedClient(new IOException("connection reset"));
        String output = output(() -> display(client, activity(Map.of())));
        assertTrue(output.contains("Could not load repositories:"), output);
        assertActivity(output);
        assertEquals(1, client.requests.size());
    }

    @Test
    void repositoryTimeoutStillDisplaysFetchedActivity() throws Exception {
        ScriptedClient client = new ScriptedClient(new HttpTimeoutException("timed out"));
        String output = output(() -> display(client, activity(Map.of())));
        assertTrue(output.contains("Could not load repositories: GitHub request timed out."), output);
        assertActivity(output);
    }

    @Test
    void malformedLaterRepositoryPageDoesNotDiscardActivityOrClaimCompleteTotal() throws Exception {
        ScriptedClient client = new ScriptedClient(
                reply("[{\"id\":10,\"name\":\"first-repo\"}]", next(REPOS + "?page=2")),
                reply("not JSON", Map.of()));
        String output = output(() -> display(client, activity(Map.of())));
        assertTrue(output.contains("- first-repo"), output);
        assertTrue(output.contains("Could not load repositories:"), output);
        assertFalse(output.contains("Total public repositories:"), output);
        assertActivity(output);
        assertEquals(2, client.requests.size());
    }

    @Test
    void untrustedRepositoryLinkIsNotRequestedAndActivityRemainsAvailable() throws Exception {
        ScriptedClient client = new ScriptedClient(
                reply("[{\"id\":10,\"name\":\"first-repo\"}]", next("https://untrusted.example/")));
        String output = output(() -> display(client, activity(Map.of())));
        assertTrue(output.contains("trusted https://api.github.com origin"), output);
        assertActivity(output);
        assertEquals(1, client.requests.size());
        assertFalse(output.contains("dummy-test-token"), output);
    }

    @Test
    void successfulRepositoryAndActivityPaginationStillShareRequestPolicy() throws Exception {
        String nextEvents = EVENTS + "&page=2";
        ScriptedClient client = new ScriptedClient(
                reply("[]", Map.of()),
                reply("[" + EVENT + ", {\"id\":\"2\",\"type\":\"WatchEvent\","
                        + "\"repo\":{\"name\":\"example/second\"}}]", Map.of()));
        String output = output(() -> display(client, activity(next(nextEvents))));
        assertTrue(output.contains("No public repositories found."), output);
        assertTrue(output.contains("Number of recent events: 2"), output);
        assertTrue(output.contains("Starred example/second"), output);
        assertEquals(List.of(REPOS + "?per_page=100", nextEvents),
                client.requests.stream().map(request -> request.uri().toString()).toList());
        for (HttpRequest request : client.requests) {
            assertEquals("Bearer dummy-test-token", request.headers().firstValue("Authorization").orElseThrow());
            assertEquals(GithubRequests.REQUEST_TIMEOUT, request.timeout().orElseThrow());
        }
        assertFalse(output.contains("dummy-test-token"), output);
    }

    @Test
    void repositoryInterruptionPropagatesWithoutContinuingActivity() throws Exception {
        ScriptedClient client = new ScriptedClient(new InterruptedException("cancelled"));
        String output = output(() -> assertThrows(InterruptedException.class,
                () -> display(client, activity(Map.of()))));
        assertFalse(output.contains("Recent public activity is available."), output);
        assertEquals(1, client.requests.size());
    }

    @Test
    void activityPaginationFailureStillPropagatesAsAnActivityFailure() throws Exception {
        ScriptedClient client = new ScriptedClient(reply("[]", Map.of()), new IOException("activity failed"));
        String output = output(() -> assertThrows(IOException.class,
                () -> display(client, activity(next(EVENTS + "&page=2")))));
        assertTrue(output.contains("No public repositories found."), output);
        assertFalse(output.contains("Number of recent events:"), output);
        assertFalse(output.contains("Could not load repositories:"), output);
    }

    private static void display(ScriptedClient client, HttpResponse<String> activity) throws Exception {
        Main.displayResults(client, activity, REPOS, "example", "dummy-test-token");
    }

    private static void assertActivity(String output) {
        assertTrue(output.contains("Number of recent events: 1"), output);
        assertTrue(output.contains("Starred example/project"), output);
    }

    private static HttpResponse<String> activity(Map<String, List<String>> headers) {
        return new Response(GithubRequests.get(EVENTS), "[" + EVENT + "]", headers);
    }

    private static Map<String, List<String>> next(String url) {
        return Map.of("Link", List.of("<" + url + ">; rel=\"next\""));
    }

    private static Reply reply(String body, Map<String, List<String>> headers) {
        return new Reply(body, headers);
    }

    private static String output(ThrowingAction action) throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            action.run();
        } finally {
            System.setOut(original);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @FunctionalInterface private interface ThrowingAction { void run() throws Exception; }
    private record Reply(String body, Map<String, List<String>> headers) { }

    private record Response(HttpRequest request, String body,
            Map<String, List<String>> headerValues) implements HttpResponse<String> {
        public int statusCode() { return 200; }
        public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        public HttpHeaders headers() { return HttpHeaders.of(headerValues, (name, value) -> true); }
        public Optional<SSLSession> sslSession() { return Optional.empty(); }
        public URI uri() { return request.uri(); }
        public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }

    /** Scripted transport: no sockets, live API calls or credentials are used. */
    private static final class ScriptedClient extends HttpClient {
        final Deque<Object> steps;
        final List<HttpRequest> requests = new ArrayList<>();
        ScriptedClient(Object... steps) { this.steps = new ArrayDeque<>(List.of(steps)); }

        @Override @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
                throws IOException, InterruptedException {
            requests.add(request);
            if (steps.isEmpty()) throw new AssertionError("Unexpected request: " + request.uri());
            Object step = steps.removeFirst();
            if (step instanceof IOException failure) throw failure;
            if (step instanceof InterruptedException failure) throw failure;
            Reply reply = (Reply) step;
            return (HttpResponse<T>) new Response(request, reply.body(), reply.headers());
        }
        public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        public Optional<Duration> connectTimeout() { return Optional.empty(); }
        public Redirect followRedirects() { return Redirect.NEVER; }
        public Optional<Authenticator> authenticator() { return Optional.empty(); }
        public Optional<ProxySelector> proxy() { return Optional.empty(); }
        public Version version() { return Version.HTTP_1_1; }
        public Optional<Executor> executor() { return Optional.empty(); }
        public SSLParameters sslParameters() { return new SSLParameters(); }
        public SSLContext sslContext() { throw new UnsupportedOperationException(); }
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                HttpResponse.BodyHandler<T> handler) { throw new UnsupportedOperationException(); }
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                HttpResponse.BodyHandler<T> handler, HttpResponse.PushPromiseHandler<T> push)
                { throw new UnsupportedOperationException(); }
    }
}
