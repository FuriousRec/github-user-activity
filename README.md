# GitHub User Activity CLI

A Java command-line tool that fetches a GitHub user's recent public activity and public repositories using the GitHub REST API.

This project was built as a backend/CLI practice project with Maven, Java's `HttpClient`, and Jackson for JSON parsing.

## Features

- Fetches recent public GitHub events for a username
- Lists public repositories with pagination
- Formats common event types like pushes, stars, forks, issues, pull requests, created refs, and deleted refs
- Handles missing users and both GitHub rate-limit responses (`403` and `429`)
- Validates usernames before making requests
- Bounds each HTTP request to 30 seconds, with a 10-second connection timeout

## Tech Stack

- Java 21
- Maven
- GitHub REST API
- Jackson Databind

## Run

```bash
mvn compile
mvn exec:java "-Dexec.mainClass=Main" "-Dexec.args=<username>"
```

Example:

```bash
mvn exec:java "-Dexec.mainClass=Main" "-Dexec.args=FuriousRec"
```

## Example Output

```text
GitHub username: FuriousRec
Response status code: 200

Public repositories:
- Algorithms-And-Data-Structure
- imperial-dominion-bot
- qr-decomposition-java
Total public repositories: 3

Recent public activity is available.
Number of recent events: 4
2026-08-03T20:55:04Z | Pushed commits to FuriousRec/imperial-dominion-bot on branch main
```

## Reliability and testing

Provide exactly one username. Invalid characters, repeated hyphens, and names longer
than 39 characters are rejected before any network request. Surrounding whitespace
is trimmed. A syntactically valid name may still refer to a nonexistent user.

Both activity and repository requests use the same headers and timeout policy.
Rate-limit responses include the server's retry delay or UTC reset time when GitHub
provides it through `Retry-After` or `X-RateLimit-Reset`. The CLI reports this guidance
but does not sleep or retry automatically. A timed-out request prints a friendly error.
The timeout applies to each request, not the total duration of a paginated repository scan.

Run the offline regression checks with Java 21 and a POSIX shell (Git Bash on Windows):

```bash
sh scripts/test-offline.sh
```

These checks need no GitHub credentials, network access, Maven or downloaded libraries.
They cover username boundaries and invalid input, connection timeout configuration,
request headers/timeouts for both API routes, and rate-limit status/header handling. They do not simulate a live network timeout.
GitHub Actions compiles the full application and runs the offline checks on pushes and PRs.

## Event formatting tests

Run `mvn test` to execute the JUnit formatter tests (Maven downloads dependencies
on the first run). They use local JSON fixtures and do not call GitHub.
The existing dependency-free request-policy checks still run with
`sh scripts/test-offline.sh`. CI runs both suites.

Pull requests use the payload's top-level number, with a fallback to the nested
pull-request object. An explicit `merged` action is displayed as “Merged”.
A `closed` action is also displayed as “Merged” when the nested `merged` flag is
true; without that evidence it stays “Closed”. Branch formatting removes only
the leading `refs/heads/` prefix and preserves the rest of the branch name.

Payload reference: [GitHub event types](https://docs.github.com/en/rest/using-the-rest-api/github-event-types).
