package app.services;

import app.models.DatabaseNode;
import app.models.DatabaseNodeType;

public class DatabaseMockService {

    private static final DatabaseMockService INSTANCE = new DatabaseMockService();

    private DatabaseMockService() {
    }

    public static DatabaseMockService getInstance() {
        return INSTANCE;
    }

    public DatabaseNode createMockRoot() {
        DatabaseNode root = new DatabaseNode("Database Explorer", DatabaseNodeType.ROOT, null, "mdi2d-database");

        root.addChild(createPostgresConnection());
        root.addChild(createMySqlConnection());
        root.addChild(createSqliteConnection());

        return root;
    }

    private DatabaseNode createPostgresConnection() {
        DatabaseNode conn = new DatabaseNode(
            "PostgreSQL: dev-local",
            DatabaseNodeType.CONNECTION,
            "localhost:5432 • online",
            "fth-server"
        );

        DatabaseNode db = new DatabaseNode("postgres", DatabaseNodeType.DATABASE, "PostgreSQL 16.2");
        conn.addChild(db);

        // Schema: public
        DatabaseNode publicSchema = new DatabaseNode("public", DatabaseNodeType.SCHEMA, "default");
        db.addChild(publicSchema);

        // Tables Category
        DatabaseNode tablesCat = new DatabaseNode("Tables (4)", DatabaseNodeType.CATEGORY_TABLES);
        publicSchema.addChild(tablesCat);

        // Table: users
        DatabaseNode usersTable = new DatabaseNode("users", DatabaseNodeType.TABLE, "1,450 rows");
        usersTable.addChild(new DatabaseNode("id", DatabaseNodeType.COLUMN, "integer", null, true, false));
        usersTable.addChild(new DatabaseNode("email", DatabaseNodeType.COLUMN, "varchar(255)"));
        usersTable.addChild(new DatabaseNode("username", DatabaseNodeType.COLUMN, "varchar(50)"));
        usersTable.addChild(new DatabaseNode("status", DatabaseNodeType.COLUMN, "varchar(20)"));
        usersTable.addChild(new DatabaseNode("created_at", DatabaseNodeType.COLUMN, "timestamp"));
        tablesCat.addChild(usersTable);

        // Table: orders
        DatabaseNode ordersTable = new DatabaseNode("orders", DatabaseNodeType.TABLE, "8,920 rows");
        ordersTable.addChild(new DatabaseNode("id", DatabaseNodeType.COLUMN, "integer", null, true, false));
        ordersTable.addChild(new DatabaseNode("user_id", DatabaseNodeType.COLUMN, "integer", null, false, true));
        ordersTable.addChild(new DatabaseNode("total_amount", DatabaseNodeType.COLUMN, "numeric(10,2)"));
        ordersTable.addChild(new DatabaseNode("status", DatabaseNodeType.COLUMN, "varchar(20)"));
        ordersTable.addChild(new DatabaseNode("order_date", DatabaseNodeType.COLUMN, "timestamp"));
        tablesCat.addChild(ordersTable);

        // Table: order_items
        DatabaseNode orderItemsTable = new DatabaseNode("order_items", DatabaseNodeType.TABLE, "24,510 rows");
        orderItemsTable.addChild(new DatabaseNode("id", DatabaseNodeType.COLUMN, "integer", null, true, false));
        orderItemsTable.addChild(new DatabaseNode("order_id", DatabaseNodeType.COLUMN, "integer", null, false, true));
        orderItemsTable.addChild(new DatabaseNode("product_id", DatabaseNodeType.COLUMN, "integer", null, false, true));
        orderItemsTable.addChild(new DatabaseNode("quantity", DatabaseNodeType.COLUMN, "integer"));
        orderItemsTable.addChild(new DatabaseNode("unit_price", DatabaseNodeType.COLUMN, "numeric(10,2)"));
        tablesCat.addChild(orderItemsTable);

        // Table: products
        DatabaseNode productsTable = new DatabaseNode("products", DatabaseNodeType.TABLE, "320 rows");
        productsTable.addChild(new DatabaseNode("id", DatabaseNodeType.COLUMN, "integer", null, true, false));
        productsTable.addChild(new DatabaseNode("sku", DatabaseNodeType.COLUMN, "varchar(50)"));
        productsTable.addChild(new DatabaseNode("name", DatabaseNodeType.COLUMN, "varchar(150)"));
        productsTable.addChild(new DatabaseNode("price", DatabaseNodeType.COLUMN, "numeric(10,2)"));
        productsTable.addChild(new DatabaseNode("stock_quantity", DatabaseNodeType.COLUMN, "integer"));
        tablesCat.addChild(productsTable);

        // Views Category
        DatabaseNode viewsCat = new DatabaseNode("Views (2)", DatabaseNodeType.CATEGORY_VIEWS);
        publicSchema.addChild(viewsCat);
        viewsCat.addChild(new DatabaseNode("v_active_users", DatabaseNodeType.VIEW, "view"));
        viewsCat.addChild(new DatabaseNode("v_daily_sales_summary", DatabaseNodeType.VIEW, "view"));

        // Functions Category
        DatabaseNode funcsCat = new DatabaseNode("Functions (2)", DatabaseNodeType.CATEGORY_FUNCTIONS);
        publicSchema.addChild(funcsCat);
        funcsCat.addChild(new DatabaseNode("get_user_order_count(user_id int)", DatabaseNodeType.FUNCTION, "returns integer"));
        funcsCat.addChild(new DatabaseNode("calculate_discount(amount numeric)", DatabaseNodeType.FUNCTION, "returns numeric"));

        // Procedures Category
        DatabaseNode procsCat = new DatabaseNode("Procedures (1)", DatabaseNodeType.CATEGORY_PROCEDURES);
        publicSchema.addChild(procsCat);
        procsCat.addChild(new DatabaseNode("sp_cleanup_expired_sessions()", DatabaseNodeType.PROCEDURE, "void"));

        // Schema: audit
        DatabaseNode auditSchema = new DatabaseNode("audit", DatabaseNodeType.SCHEMA, "audit tables");
        db.addChild(auditSchema);
        DatabaseNode auditTablesCat = new DatabaseNode("Tables (2)", DatabaseNodeType.CATEGORY_TABLES);
        auditSchema.addChild(auditTablesCat);
        auditTablesCat.addChild(new DatabaseNode("audit_logs", DatabaseNodeType.TABLE, "15,800 rows"));
        auditTablesCat.addChild(new DatabaseNode("login_history", DatabaseNodeType.TABLE, "4,120 rows"));

        return conn;
    }

    private DatabaseNode createMySqlConnection() {
        DatabaseNode conn = new DatabaseNode(
            "MySQL: production-replica",
            DatabaseNodeType.CONNECTION,
            "10.0.1.20:3306 • replica",
            "fth-server"
        );

        DatabaseNode db = new DatabaseNode("analytics_dw", DatabaseNodeType.DATABASE, "MySQL 8.0");
        conn.addChild(db);

        DatabaseNode tablesCat = new DatabaseNode("Tables (3)", DatabaseNodeType.CATEGORY_TABLES);
        db.addChild(tablesCat);

        DatabaseNode pageviewsTable = new DatabaseNode("fact_pageviews", DatabaseNodeType.TABLE, "1,240,000 rows");
        pageviewsTable.addChild(new DatabaseNode("id", DatabaseNodeType.COLUMN, "bigint", null, true, false));
        pageviewsTable.addChild(new DatabaseNode("user_pseudo_id", DatabaseNodeType.COLUMN, "varchar(100)"));
        pageviewsTable.addChild(new DatabaseNode("page_url", DatabaseNodeType.COLUMN, "varchar(500)"));
        pageviewsTable.addChild(new DatabaseNode("event_time", DatabaseNodeType.COLUMN, "timestamp"));
        tablesCat.addChild(pageviewsTable);

        DatabaseNode cohortsTable = new DatabaseNode("dim_user_cohorts", DatabaseNodeType.TABLE, "50 rows");
        cohortsTable.addChild(new DatabaseNode("cohort_id", DatabaseNodeType.COLUMN, "int", null, true, false));
        cohortsTable.addChild(new DatabaseNode("cohort_name", DatabaseNodeType.COLUMN, "varchar(100)"));
        cohortsTable.addChild(new DatabaseNode("created_date", DatabaseNodeType.COLUMN, "date"));
        tablesCat.addChild(cohortsTable);

        DatabaseNode sourcesTable = new DatabaseNode("dim_traffic_sources", DatabaseNodeType.TABLE, "18 rows");
        sourcesTable.addChild(new DatabaseNode("source_id", DatabaseNodeType.COLUMN, "int", null, true, false));
        sourcesTable.addChild(new DatabaseNode("source_name", DatabaseNodeType.COLUMN, "varchar(100)"));
        sourcesTable.addChild(new DatabaseNode("medium", DatabaseNodeType.COLUMN, "varchar(50)"));
        tablesCat.addChild(sourcesTable);

        DatabaseNode viewsCat = new DatabaseNode("Views (1)", DatabaseNodeType.CATEGORY_VIEWS);
        db.addChild(viewsCat);
        viewsCat.addChild(new DatabaseNode("v_monthly_retention", DatabaseNodeType.VIEW, "view"));

        return conn;
    }

    private DatabaseNode createSqliteConnection() {
        DatabaseNode conn = new DatabaseNode(
            "SQLite: embedded-local",
            DatabaseNodeType.CONNECTION,
            "local.db • read/write",
            "fth-server"
        );

        DatabaseNode db = new DatabaseNode("main", DatabaseNodeType.DATABASE, "SQLite 3.45");
        conn.addChild(db);

        DatabaseNode tablesCat = new DatabaseNode("Tables (2)", DatabaseNodeType.CATEGORY_TABLES);
        db.addChild(tablesCat);

        DatabaseNode migrationsTable = new DatabaseNode("flyway_schema_history", DatabaseNodeType.TABLE, "14 rows");
        migrationsTable.addChild(new DatabaseNode("installed_rank", DatabaseNodeType.COLUMN, "int", null, true, false));
        migrationsTable.addChild(new DatabaseNode("version", DatabaseNodeType.COLUMN, "varchar(50)"));
        migrationsTable.addChild(new DatabaseNode("description", DatabaseNodeType.COLUMN, "varchar(200)"));
        tablesCat.addChild(migrationsTable);

        DatabaseNode scratchTable = new DatabaseNode("scratchpad", DatabaseNodeType.TABLE, "5 rows");
        scratchTable.addChild(new DatabaseNode("id", DatabaseNodeType.COLUMN, "integer", null, true, false));
        scratchTable.addChild(new DatabaseNode("note_text", DatabaseNodeType.COLUMN, "text"));
        tablesCat.addChild(scratchTable);

        return conn;
    }
}
