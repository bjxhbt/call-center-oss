import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Initializes only a fresh MySQL database. Never logs credentials or hashes. */
public class DemoDatabase {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: DemoDatabase.java schema.sql seed.sql");
        String url = required("OCH_DB_URL");
        if (!url.startsWith("jdbc:mysql:")) throw new IllegalArgumentException("This initializer supports MySQL only");
        String password = required("OCH_BOOTSTRAP_PASSWORD");
        if (password.length() < 12) throw new IllegalArgumentException("Admin password must have at least 12 characters");
        String schema = Files.readString(Path.of(args[0]));
        String seed = Files.readString(Path.of(args[1])).replace("__OCH_ADMIN_HASH__", new BCryptPasswordEncoder().encode(password));
        Class.forName("com.mysql.cj.jdbc.Driver");
        if (Boolean.parseBoolean(System.getenv("OCH_DB_CREATE"))) {
            String name = required("OCH_DB_NAME");
            if (!name.matches("[A-Za-z][A-Za-z0-9_]{0,63}") || List.of("mysql","sys","information_schema","performance_schema").contains(name.toLowerCase())) {
                throw new IllegalArgumentException("Use a dedicated demo database identifier");
            }
            URI target = URI.create(url.substring(5));
            if (!("/" + name).equals(target.getPath())) throw new IllegalArgumentException("Database name and JDBC URL disagree");
            String serverUrl = "jdbc:mysql://" + target.getRawAuthority() + "/" + (target.getRawQuery() == null ? "" : "?" + target.getRawQuery());
            try (Connection server = DriverManager.getConnection(serverUrl, required("OCH_DB_USER"), required("OCH_DB_PASSWORD"));
                 Statement create = server.createStatement()) {
                create.execute("CREATE DATABASE IF NOT EXISTS `" + name + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            }
        }
        try (Connection db = DriverManager.getConnection(url, required("OCH_DB_USER"), required("OCH_DB_PASSWORD"));
             Statement statement = db.createStatement()) {
            statement.execute("SET SESSION sql_mode = CONCAT(@@sql_mode, ',ANSI_QUOTES,PIPES_AS_CONCAT')");
            try (var lock = statement.executeQuery("SELECT GET_LOCK(CONCAT('xhbt_init_', DATABASE()), 0)")) {
                if (!lock.next() || lock.getInt(1) != 1) throw new IllegalStateException("Another initialization is running");
            }
            try (var tables = statement.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()")) {
                tables.next();
                if (tables.getInt(1) != 0) throw new IllegalStateException("Initialization requires an EMPTY database; no changes made");
            }
            // MySQL DDL commits implicitly. Never drop or overwrite a partial initialization.
            for (String sql : split(schema)) statement.execute(sql);
            db.setAutoCommit(false);
            try {
                for (String sql : split(seed)) statement.execute(sql);
                db.commit();
            } catch (Exception e) {
                db.rollback();
                throw e;
            }
            System.out.println("MySQL schema and synthetic demo configuration initialized; login user: admin");
        }
    }

    static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing environment variable: " + name);
        return value;
    }

    static List<String> split(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (quote == 0 && ch == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                while (i < sql.length() && sql.charAt(i) != '\n') i++;
                current.append('\n');
                continue;
            }
            if (quote != 0) {
                current.append(ch);
                if (ch == '\\' && i + 1 < sql.length()) current.append(sql.charAt(++i));
                else if (ch == quote) {
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) current.append(sql.charAt(++i));
                    else quote = 0;
                }
            } else if (ch == '\'' || ch == '"' || ch == '`') {
                quote = ch;
                current.append(ch);
            } else if (ch == ';') {
                if (!current.toString().isBlank()) statements.add(current.toString().trim());
                current.setLength(0);
            } else current.append(ch);
        }
        if (quote != 0) throw new IllegalArgumentException("Unterminated SQL quote");
        if (!current.toString().isBlank()) statements.add(current.toString().trim());
        return statements;
    }
}
