import java.io.InputStream;
import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;
import com.alibaba.druid.pool.DruidDataSource;
import com.och.ai.asr.config.DatabaseVendorConfig;
import com.och.calltask.domain.entity.CustomerCrowdRel;
import com.och.calltask.mapper.CustomerCrowdRelMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

/** Local regression check: mapper selects MySQL upsert and preserves unique relations. */
public class VerifyMysqlMappers {
    public static void main(String[] args) throws Exception {
        try (DruidDataSource ds = new DruidDataSource()) {
            ds.setUrl(System.getenv("OCH_DB_URL"));
            ds.setUsername(System.getenv("OCH_DB_USER"));
            ds.setPassword(System.getenv("OCH_DB_PASSWORD"));
            ds.setConnectionInitSqls(List.of("SET SESSION sql_mode = CONCAT(@@sql_mode, ',ANSI_QUOTES,PIPES_AS_CONCAT')"));
            Configuration config = new Configuration(new Environment("mysql-check",new JdbcTransactionFactory(),ds));
            config.setDatabaseId(new DatabaseVendorConfig().databaseIdProvider().getDatabaseId(ds));
            if (!"mysql".equals(config.getDatabaseId())) throw new AssertionError("Wrong MyBatis database ID");
            String resource = "mapper/CustomerCrowdRelMapper.xml";
            try (InputStream xml = CustomerCrowdRelMapper.class.getClassLoader().getResourceAsStream(resource)) {
                new XMLMapperBuilder(xml,config,resource,config.getSqlFragments()).parse();
            }
            try (var session = new SqlSessionFactoryBuilder().build(config).openSession()) {
                CustomerCrowdRel item = new CustomerCrowdRel();
                item.setCrowdId(-100L);
                item.setCustomerId(-100L);
                var mapper = session.getMapper(CustomerCrowdRelMapper.class);
                mapper.batchUpsert(List.of(item));
                mapper.batchUpsert(List.of(item));
                try (var statement = session.getConnection().createStatement();
                     var count = statement.executeQuery("SELECT COUNT(*) FROM customer_crowd_rel WHERE crowd_id=-100 AND customer_id=-100")) {
                    count.next();
                    if (count.getInt(1) != 1) throw new AssertionError("Upsert duplicated a customer relation");
                }
                session.rollback();
            }
        }
        System.out.println("PASS MyBatis MySQL vendor detection, named collection, duplicate-key upsert and rollback");
    }
}
