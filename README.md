# GitHub User Activity CLI

A Java command-line tool that fetches a GitHub user's recent public activity and public repositories using the GitHub REST API.

This project was built as a backend/CLI practice project with Maven, Java's `HttpClient`, and Jackson for JSON parsing.

## Features

- Fetches recent public GitHub events for a username
- Lists public repositories using GitHub's server-provided pagination links
- Formats common event types like pushes, stars, forks, issues, pull requests, created refs, and deleted refs
- Handles missing users and distinguishes rate-limited responses from ordinary `403 Forbidden` errors
- Validates usernames before making requests
- Bounds each HTTP request to 30 seconds, with a 10-second connection timeout
- Supports optional authenticated requests through a non-logged `GITHUB_TOKEN` environment variable

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

For authenticated requests, set a token in the environment instead of passing it
as a command-line argument:

```bash
export GITHUB_TOKEN='your-token'
mvn exec:java "-Dexec.mainClass=Main" "-Dexec.args=FuriousRec"
```

The token is sent only in the `Authorization` header. The CLI does not print it.
Public-only use remains the default when the variable is absent or blank.

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

Both activity and repository requests use the same headers, pinned GitHub API
version, optional bearer token, and timeout policy. Repository pagination follows
GitHub's `Link: rel="next"` response instead of guessing from page length. Every
request and pagination link is restricted to the trusted `https://api.github.com`
origin before an optional token can be attached.
A `429` response is always treated as rate limiting. A `403` is treated as rate limiting only when GitHub reports exhausted quota or a retry delay, avoiding misleading advice for ordinary permission failures. Rate-limit responses include the server's retry delay or UTC reset time when GitHub
provides it through `Retry-After` or `X-RateLimit-Reset`. The CLI reports this guidance
but does not sleep or retry automatically. A timed-out request prints a friendly error.
The timeout applies to each request, not the total duration of a paginated repository scan.

Run the offline regression checks with Java 21 and a POSIX shell (Git Bash on Windows):

```bash
sh scripts/test-offline.sh
```

These checks need no GitHub credentials, network access, Maven or downloaded libraries.
They cover username boundaries and invalid input, connection timeout configuration,
request headers/timeouts for both API routes, trusted-origin enforcement, safe
`Link` pagination, optional token validation and header injection, and rate-limit
versus ordinary-forbidden status/header handling. They do not simulate a live network timeout.
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
