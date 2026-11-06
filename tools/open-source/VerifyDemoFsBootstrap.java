import java.sql.Connection;
import java.sql.Statement;

/** Run only against the isolated native validation database, with empty SIP tables. */
public class VerifyDemoFsBootstrap {
    static int count(Connection db, String table) throws Exception {
        try (Statement q = db.createStatement(); var rows = q.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    static void require(boolean condition) {
        if (!condition) throw new IllegalStateException("Bootstrap assertion failed");
    }

    public static void main(String[] args) throws Exception {
        if (!"127.0.0.1".equals(java.net.URI.create(DemoDatabase.required("OCH_DB_URL").substring(5)).getHost())) {
            throw new IllegalArgumentException("Only an isolated localhost database may be tested");
        }
        try (Connection db = DemoFreeSwitch.database(); Statement s = db.createStatement()) {
            for (String table : new String[]{"fs_config", "fs_dialplan", "fs_acl", "ko_subscriber"}) require(count(db, table) == 0);
            try {
                DemoFreeSwitch.setup();
                require(count(db, "fs_config") == 1 && count(db, "ko_subscriber") == 2);
                require(count(db, "fs_dialplan") == 4 && count(db, "fs_acl") == 2);
                s.executeUpdate("UPDATE ko_subscriber SET password='test-edited-sip-secret-only' WHERE username='1001'");
                DemoFreeSwitch.setup();
                require(count(db, "fs_config") == 1 && count(db, "fs_dialplan") == 4);
                try (var rows = s.executeQuery("SELECT password FROM ko_subscriber WHERE username='1001'")) {
                    require(rows.next() && "test-edited-sip-secret-only".equals(rows.getString(1)));
                }
                s.executeUpdate("UPDATE fs_config SET name='test-unmanaged-fs-node'");
                boolean refused = false;
                try { DemoFreeSwitch.setup(); } catch (IllegalStateException expected) { refused = true; }
                require(refused && count(db, "fs_config") == 1 && count(db, "fs_dialplan") == 4);
                System.out.println("PASS: MySQL SIP bootstrap, idempotency, user-edited SIP preservation and unmanaged-node refusal");
            } finally {
                s.executeUpdate("DELETE FROM fs_config WHERE name IN ('xhbt-docker-freeswitch','test-unmanaged-fs-node')");
                s.executeUpdate("DELETE FROM ko_subscriber WHERE username IN ('1001','1002') AND domain='192.0.2.10'");
                s.executeUpdate("DELETE FROM fs_dialplan WHERE name IN ('demo-internal-extensions','demo-echo-test') AND group_id=0");
                s.executeUpdate("DELETE FROM fs_acl WHERE name IN ('domains','wan') AND list_id=0");
            }
            for (String table : new String[]{"fs_config", "fs_dialplan", "fs_acl", "ko_subscriber"}) require(count(db, table) == 0);
        }
    }
}
