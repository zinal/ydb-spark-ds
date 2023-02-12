package tech.ydb.spark.connector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.spark.sql.connector.catalog.Table;
import org.apache.spark.sql.connector.catalog.TableCapability;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import tech.ydb.table.description.TableColumn;
import tech.ydb.table.description.TableDescription;

/**
 *
 * @author mzinal
 */
public class YdbTable implements Table {

    static final Set<TableCapability> CAPABILITIES;
    static {
        final Set<TableCapability> c = new HashSet<>();
        c.add(TableCapability.BATCH_READ);
        c.add(TableCapability.MICRO_BATCH_READ);
        c.add(TableCapability.CONTINUOUS_READ);
        c.add(TableCapability.BATCH_WRITE);
        c.add(TableCapability.STREAMING_WRITE);
        c.add(TableCapability.OVERWRITE_BY_FILTER);
        c.add(TableCapability.V1_BATCH_WRITE);
        CAPABILITIES = Collections.unmodifiableSet(c);
    }

    private final YdbConnector connector;
    private final String fullName;
    private final TableDescription td;

    protected YdbTable(YdbConnector connector, String fullName, TableDescription td) {
        this.connector = connector;
        this.fullName = fullName;
        this.td = td;
    }

    @Override
    public String name() {
        return fullName;
    }

    @Override
    public StructType schema() {
        return new StructType(mapFields(td.getColumns()));
    }

    @Override
    public Map<String, String> properties() {
        return Table.super.properties(); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/OverriddenMethodBody
    }

    @Override
    public Set<TableCapability> capabilities() {
        return CAPABILITIES;
    }

    private StructField[] mapFields(List<TableColumn> columns) {
        final List<StructField> fields = new ArrayList<>();
        for (TableColumn tc : columns) {
            final DataType dataType = YdbTypes.mapType(tc.getType());
            if (dataType != null)
                fields.add(mapField(tc, dataType));
        }
        return fields.toArray(new StructField[0]);
    }

    private StructField mapField(TableColumn tc, DataType dataType) {
        // TODO: mapping dictionary support (specifically for dates).
        return new StructField(tc.getName(), dataType, YdbTypes.mapNullable(tc.getType()), null);
    }

}
