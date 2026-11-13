import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GithubRepositoryPagesTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void removesRepositoriesRepeatedByShiftedPages() throws Exception {
        Set<String> seen = new HashSet<>();

        List<JsonNode> firstPage = GithubRepositoryPages.unique(
                mapper.readTree("[{\"id\":3,\"name\":\"three\"},{\"id\":2,\"name\":\"two\"}]"),
                seen);
        List<JsonNode> secondPage = GithubRepositoryPages.unique(
                mapper.readTree("[{\"id\":2,\"name\":\"two\"},{\"id\":1,\"name\":\"one\"}]"),
                seen);

        assertEquals(
                List.of("three", "two", "one"),
                java.util.stream.Stream.concat(firstPage.stream(), secondPage.stream())
                        .map(repository -> repository.path("name").asText())
                        .toList());
    }

    @Test
    void retainsRepositoriesWithoutIds() throws Exception {
        JsonNode page = mapper.readTree(
                "[{\"name\":\"future-one\"},{\"name\":\"future-two\"}]");

        assertEquals(2, GithubRepositoryPages.unique(page, new HashSet<>()).size());
    }

    @Test
    void rejectsUnexpectedNonArrayResponses() throws Exception {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> GithubRepositoryPages.unique(
                        mapper.readTree("{\"message\":\"unexpected\"}"),
                        new HashSet<>()));

        assertEquals(
                "GitHub repository response must be an array.",
                exception.getMessage());
    }
}
