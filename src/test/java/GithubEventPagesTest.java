import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GithubEventPagesTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void combinesPagesAndRemovesShiftedDuplicateEvents() throws Exception {
        List<JsonNode> events = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        GithubEventPages.appendUnique(
                mapper.readTree("[{\"id\":\"3\"},{\"id\":\"2\"}]"),
                events,
                seen);
        GithubEventPages.appendUnique(
                mapper.readTree("[{\"id\":\"2\"},{\"id\":\"1\"}]"),
                events,
                seen);

        assertEquals(
                List.of("3", "2", "1"),
                events.stream()
                        .map(event -> event.path("id").asText())
                        .toList());
    }

    @Test
    void retainsEventsWithoutIdsInsteadOfSilentlyDroppingThem() throws Exception {
        List<JsonNode> events = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        JsonNode page = mapper.readTree(
                "[{\"type\":\"FutureEvent\"},{\"type\":\"FutureEvent\"}]");

        GithubEventPages.appendUnique(page, events, seen);

        assertEquals(2, events.size());
    }

    @Test
    void rejectsUnexpectedNonArrayResponses() throws Exception {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> GithubEventPages.appendUnique(
                        mapper.readTree("{\"message\":\"unexpected\"}"),
                        new ArrayList<>(),
                        new HashSet<>()));

        assertEquals(
                "GitHub event response must be an array.",
                exception.getMessage());
    }
}
