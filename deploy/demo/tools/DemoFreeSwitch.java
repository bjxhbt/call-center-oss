import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.io.BufferedInputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;

/** Configures only this package's managed demo node; never logs secrets. */
public class DemoFreeSwitch {
    static final String NODE = "xhbt-docker-freeswitch";

    public static void main(String[] args) {
        try {
            if (args.length == 1 && "--check".equals(args[0])) check();
            else if (args.length == 0) setup();
            else throw new IllegalArgumentException("Unsupported arguments");
        } catch (Exception e) {
            System.err.println("FreeSWITCH setup/check failed (" + e.getClass().getSimpleName()
                    + "). Check the dedicated demo database, secrets and service health; no credentials were logged.");
            System.exit(1);
        }
    }

    static Connection database() throws Exception {
        String name = DemoDatabase.required("OCH_DB_NAME");
        String url = DemoDatabase.required("OCH_DB_URL");
        if (!name.startsWith("xhbt_demo") || !url.startsWith("jdbc:mysql://")
                || !("/" + name).equals(java.net.URI.create(url.substring(5)).getPath())) {
            throw new IllegalArgumentException("Only a dedicated xhbt_demo database is supported");
        }
        return DriverManager.getConnection(url, DemoDatabase.required("OCH_DB_USER"), DemoDatabase.required("OCH_DB_PASSWORD"));
    }

    static String secret(String name) {
        String value = DemoDatabase.required(name);
        if (!value.matches("[A-Za-z0-9_-]{24,64}")) throw new IllegalArgumentException("Invalid deployment secret: " + name);
        return value;
    }

    static void setup() throws Exception {
        String host = DemoDatabase.required("OCH_FS_ESL_HOST");
        if (!host.equals("freeswitch")) throw new IllegalArgumentException("Expected the Compose FreeSWITCH service");
        String password = secret("OCH_FS_ESL_PASSWORD");
        try (Connection db = database()) {
            try (Statement s = db.createStatement(); var lock = s.executeQuery("SELECT GET_LOCK('xhbt_demo_fs_setup', 0)")) {
                if (!lock.next() || lock.getInt(1) != 1) throw new IllegalStateException("Another setup is running");
            }
            db.setAutoCommit(false);
            try {
                boolean fresh;
                try (var query = db.prepareStatement("SELECT name, group_name, ip, del_flag FROM fs_config FOR UPDATE"); var rows = query.executeQuery()) {
                    fresh = !rows.next();
                    if (!fresh && (!NODE.equals(rows.getString(1)) || !"public-demo".equals(rows.getString(2))
                            || !host.equals(rows.getString(3)) || rows.getInt(4) != 0 || rows.next())) {
                        throw new IllegalStateException("Refusing to overwrite an unmanaged FreeSWITCH configuration");
                    }
                }
                if (fresh) {
                    for (String table : List.of("ko_subscriber", "fs_dialplan", "fs_acl")) {
                        try (Statement query = db.createStatement(); var rows = query.executeQuery("SELECT COUNT(*) FROM " + table)) {
                            rows.next();
                            if (rows.getInt(1) != 0) throw new IllegalStateException("Initial demo SIP setup requires empty configuration tables");
                        }
                    }
                    execute(db, "INSERT INTO fs_config (name,group_name,ip,port,user_name,password,status,out_time,create_time,del_flag) VALUES (?,'public-demo',?,8021,'esl',?,1,10,NOW(),0)", NODE, host, password);
                    for (String user : List.of("1001", "1002")) {
                        String sipPassword = secret("OCH_FS_SIP_PASSWORD_" + user);
                        execute(db, "INSERT INTO ko_subscriber (username,domain,password,status,create_time) VALUES (?, ?, ?, 0, NOW())",
                                user, DemoDatabase.required("OCH_FS_PUBLIC_IP"), sipPassword);
                    }
                    for (String context : List.of("public", "default")) {
                        execute(db, "INSERT INTO fs_dialplan (group_id,name,type,context_name,content,create_time,del_flag) VALUES (0,?,'xml',?,?,NOW(),0)",
                                "demo-internal-extensions", context,
                                "<extension><condition field=\"destination_number\" expression=\"^(100[12])$\"><action application=\"bridge\" data=\"user/$1@$${domain}\"/></condition></extension>");
                        execute(db, "INSERT INTO fs_dialplan (group_id,name,type,context_name,content,create_time,del_flag) VALUES (0,?,'xml',?,?,NOW(),0)",
                                "demo-echo-test", context,
                                "<extension><condition field=\"destination_number\" expression=\"^9196$\"><action application=\"answer\"/><action application=\"echo\"/></condition></extension>");
                    }
                    execute(db, "INSERT INTO fs_acl (name,default_type,list_id,create_time,del_flag) VALUES ('domains','deny',0,NOW(),0)");
                    // Candidate ACL only; never used as a SIP authentication bypass.
                    execute(db, "INSERT INTO fs_acl (name,default_type,list_id,create_time,del_flag) VALUES ('wan','allow',0,NOW(),0)");
                } else {
                    execute(db, "UPDATE fs_config SET password=?,port=8021,out_time=10,status=1,update_time=NOW() WHERE name=? AND group_name='public-demo'", password, NODE);
                }
                db.commit();
                System.out.println("Managed FreeSWITCH ESL configuration ready; demo extensions: 1001 / 1002; echo: 9196");
            } catch (Exception e) {
                db.rollback();
                throw e;
            }
        }
    }

    static void execute(Connection db, String sql, String... values) throws Exception {
        try (PreparedStatement statement = db.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setString(i + 1, values[i]);
            statement.executeUpdate();
        }
    }

    static String frame(BufferedInputStream in) throws Exception {
        StringBuilder header = new StringBuilder();
        int ch;
        while ((ch = in.read()) != -1) {
            header.append((char) ch);
            if (header.length() > 8192) throw new IllegalStateException("Oversized ESL header");
            if (header.toString().endsWith("\n\n") || header.toString().endsWith("\r\n\r\n")) break;
        }
        if (ch == -1) throw new IllegalStateException("Closed ESL connection");
        int length = 0;
        for (String line : header.toString().split("\r?\n")) {
            if (line.toLowerCase().startsWith("content-length:")) length = Integer.parseInt(line.substring(15).trim());
        }
        if (length < 0 || length > 65536) throw new IllegalStateException("Oversized ESL body");
        byte[] body = in.readNBytes(length);
        if (body.length != length) throw new IllegalStateException("Incomplete ESL body");
        return header + new String(body, StandardCharsets.UTF_8);
    }

    static void check() throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(DemoDatabase.required("OCH_FS_ESL_HOST"), 8021), 10000);
            socket.setSoTimeout(10000);
            var in = new BufferedInputStream(socket.getInputStream());
            if (!frame(in).contains("auth/request")) throw new IllegalStateException("Not an ESL service");
            socket.getOutputStream().write(("auth " + secret("OCH_FS_ESL_PASSWORD") + "\n\n").getBytes(StandardCharsets.UTF_8));
            if (!frame(in).contains("+OK accepted")) throw new IllegalStateException("ESL authentication failed");
            for (String command : List.of("status", "sofia status")) {
                socket.getOutputStream().write(("api " + command + "\n\n").getBytes(StandardCharsets.UTF_8));
                String response = frame(in);
                if (response.contains("-ERR") || (command.equals("status") ? !response.contains("UP ")
                        : !java.util.regex.Pattern.compile("(?m)^\\s*internal\\s+profile\\s+\\S+\\s+RUNNING").matcher(response).find())) {
                    throw new IllegalStateException("FreeSWITCH profile not ready");
                }
            }
        }
        long deadline = System.nanoTime() + Duration.ofSeconds(180).toNanos();
        do {
            try (Connection db = database(); var query = db.prepareStatement("SELECT status FROM fs_config WHERE name=? AND del_flag=0")) {
                query.setString(1, NODE);
                try (var row = query.executeQuery()) {
                    if (row.next() && row.getInt(1) == 0) {
                        System.out.println("FreeSWITCH ESL authenticated, SIP profile running, backend node online");
                        return;
                    }
                }
            }
            Thread.sleep(3000);
        } while (System.nanoTime() < deadline);
        throw new IllegalStateException("Backend ESL connection did not become online");
    }
}
