package app.services;

import app.models.ConsoleMessage;
import app.models.QueryResult;
import app.models.QueryResultColumn;
import app.models.QueryResultRow;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class QueryResultMockService {

    private static final QueryResultMockService INSTANCE = new QueryResultMockService();
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private QueryResultMockService() {
    }

    public static QueryResultMockService getInstance() {
        return INSTANCE;
    }

    public QueryResult generateDefaultQueryResult() {
        List<QueryResultColumn> columns = List.of(
            new QueryResultColumn("id", "integer"),
            new QueryResultColumn("username", "varchar(50)"),
            new QueryResultColumn("email", "varchar(255)"),
            new QueryResultColumn("total_orders", "bigint"),
            new QueryResultColumn("total_spent", "numeric(10,2)"),
            new QueryResultColumn("status", "varchar(20)"),
            new QueryResultColumn("created_at", "timestamp")
        );

        String[] firstNames = {"alex", "sarah", "john", "linda", "michael", "emily", "david", "jessica", "james", "olivia", "daniel", "sophia"};
        String[] lastNames = {"smith", "johnson", "williams", "brown", "jones", "garcia", "miller", "davis", "wilson", "taylor", "anderson", "thomas"};
        String[] domains = {"gmail.com", "company.org", "outlook.com", "enterprise.io", "tech.dev"};
        String[] statuses = {"active", "active", "active", "pending", "vip", "suspended"};

        Random rng = new Random(42); // deterministic seed for test consistency
        List<QueryResultRow> rows = new ArrayList<>();

        for (int i = 1; i <= 50; i++) {
            String fn = firstNames[rng.nextInt(firstNames.length)];
            String ln = lastNames[rng.nextInt(lastNames.length)];
            String uname = fn + "_" + ln + (rng.nextInt(90) + 10);
            String email = uname + "@" + domains[rng.nextInt(domains.length)];
            int orders = rng.nextInt(65) + 1;
            double spent = Math.round((orders * (rng.nextDouble() * 85.0 + 15.0)) * 100.0) / 100.0;
            String status = statuses[rng.nextInt(statuses.length)];
            String date = LocalDateTime.now().minusDays(rng.nextInt(365)).minusHours(rng.nextInt(24)).format(TIME_FORMATTER);

            rows.add(new QueryResultRow(i, List.of(
                1000 + i,
                uname,
                email,
                orders,
                String.format("%.2f", spent),
                status,
                date
            )));
        }

        return new QueryResult(
            "SELECT u.id, u.username, u.email, COUNT(o.id) AS total_orders, COALESCE(SUM(o.total_amount), 0.00) AS total_spent FROM users u ...",
            columns,
            rows,
            42,
            LocalDateTime.now().format(TIME_FORMATTER)
        );
    }

    public List<ConsoleMessage> generateInitialMessages() {
        List<ConsoleMessage> messages = new ArrayList<>();
        messages.add(new ConsoleMessage(ConsoleMessage.Level.INFO, "Connected to PostgreSQL 16.2 on localhost:5432 (SSL enabled)", "12ms"));
        messages.add(new ConsoleMessage(ConsoleMessage.Level.INFO, "Database context set to 'postgres', schema 'public'", "3ms"));
        messages.add(new ConsoleMessage(ConsoleMessage.Level.SUCCESS, "Schemas and object tree catalog cached (14 objects loaded)", "28ms"));
        messages.add(new ConsoleMessage(ConsoleMessage.Level.SUCCESS, "[dev_db] 50 rows retrieved in 42ms (execution: 18ms, fetching: 24ms)", "42ms"));
        return messages;
    }
}

