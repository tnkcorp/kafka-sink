/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datastax.oss.kafka.sink.ccm;

import static com.datastax.oss.dsbulk.tests.ccm.CCMCluster.Type.DSE;
import static org.assertj.core.api.Assertions.assertThat;

import com.datastax.dse.driver.api.core.data.geometry.LineString;
import com.datastax.dse.driver.api.core.data.geometry.Point;
import com.datastax.dse.driver.api.core.data.geometry.Polygon;
import com.datastax.dse.driver.api.core.data.time.DateRange;
import com.datastax.dse.driver.internal.core.data.geometry.DefaultLineString;
import com.datastax.dse.driver.internal.core.data.geometry.DefaultPoint;
import com.datastax.dse.driver.internal.core.data.geometry.DefaultPolygon;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.core.type.UserDefinedType;
import com.datastax.oss.driver.api.core.type.reflect.GenericType;
import com.datastax.oss.driver.internal.core.type.DefaultTupleType;
import com.datastax.oss.driver.internal.core.type.UserDefinedTypeBuilder;
import com.datastax.oss.driver.shaded.guava.common.collect.ImmutableList;
import com.datastax.oss.driver.shaded.guava.common.collect.ImmutableMap;
import com.datastax.oss.dsbulk.tests.ccm.CCMCluster;
import com.datastax.oss.protocol.internal.util.Bytes;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.sink.SinkRecord;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("medium")
class StructEndToEndCCMIT extends EndToEndCCMITBase {

  StructEndToEndCCMIT(CCMCluster ccm, CqlSession session) {
    super(ccm, session);
  }

  @Test
  void struct_value_only() throws ParseException {
    // We skip testing the following datatypes, since in Kafka messages, values for these
    // types would simply be strings or numbers, and we'd just pass these right through to
    // the ExtendedCodecRegistry for encoding:
    //
    // ascii
    // date
    // decimal
    // duration
    // inet
    // time
    // timestamp
    // timeuuid
    // uuid
    // varint

    String withDateRange = hasDateRange ? "daterangecol=value.daterange, " : "";
    String withGeotypes =
        ccm.getClusterType() == DSE
            ? "pointcol=value.point, linestringcol=value.linestring, polygoncol=value.polygon, "
            : "";

    conn.start(
        makeConnectorProperties(
            "bigintcol=value.bigint, "
                + "booleancol=value.boolean, "
                + "doublecol=value.double, "
                + "floatcol=value.float, "
                + "intcol=value.int, "
                + "smallintcol=value.smallint, "
                + "textcol=value.text, "
                + "tinyintcol=value.tinyint, "
                + "mapcol=value.map, "
                + "mapnestedcol=value.mapnested, "
                + "listcol=value.list, "
                + "listnestedcol=value.listnested, "
                + "setcol=value.set, "
                + "setnestedcol=value.setnested, "
                + "tuplecol=value.tuple, "
                + "udtcol=value.udt, "
                + "udtfromlistcol=value.udtfromlist, "
                + "booleanudtcol=value.booleanudt, "
                + "booleanudtfromlistcol=value.booleanudtfromlist, "
                + withGeotypes
                + withDateRange
                + "blobcol=value.blob"));

    Schema schema =
        SchemaBuilder.struct()
            .name("Kafka")
            .field("bigint", Schema.INT64_SCHEMA)
            .field("boolean", Schema.BOOLEAN_SCHEMA)
            .field("double", Schema.FLOAT64_SCHEMA)
            .field("float", Schema.FLOAT32_SCHEMA)
            .field("int", Schema.INT32_SCHEMA)
            .field("smallint", Schema.INT16_SCHEMA)
            .field("text", Schema.STRING_SCHEMA)
            .field("tinyint", Schema.INT8_SCHEMA)
            .field("map", SchemaBuilder.map(Schema.STRING_SCHEMA, Schema.INT32_SCHEMA).build())
            .field(
                "mapnested",
                SchemaBuilder.map(
                        Schema.STRING_SCHEMA,
                        SchemaBuilder.map(Schema.INT32_SCHEMA, Schema.STRING_SCHEMA).build())
                    .build())
            .field("list", SchemaBuilder.array(Schema.INT32_SCHEMA).build())
            .field(
                "listnested",
                SchemaBuilder.array(SchemaBuilder.array(Schema.INT32_SCHEMA).build()).build())
            .field("set", SchemaBuilder.array(Schema.INT32_SCHEMA).build())
            .field(
                "setnested",
                SchemaBuilder.array(SchemaBuilder.array(Schema.INT32_SCHEMA).build()).build())
            .field("tuple", SchemaBuilder.array(Schema.INT32_SCHEMA).build())
            .field("udt", SchemaBuilder.map(Schema.STRING_SCHEMA, Schema.INT32_SCHEMA).build())
            .field("udtfromlist", SchemaBuilder.array(Schema.INT32_SCHEMA).build())
            .field(
                "booleanudt",
                SchemaBuilder.map(Schema.STRING_SCHEMA, Schema.BOOLEAN_SCHEMA).build())
            .field("booleanudtfromlist", SchemaBuilder.array(Schema.BOOLEAN_SCHEMA).build())
            .field("blob", Schema.BYTES_SCHEMA)
            .field("point", Schema.STRING_SCHEMA)
            .field("linestring", Schema.STRING_SCHEMA)
            .field("polygon", Schema.STRING_SCHEMA)
            .field("daterange", Schema.STRING_SCHEMA)
            .build();

    Map<String, Integer> mapValue =
        ImmutableMap.<String, Integer>builder().put("sub1", 37).put("sub2", 96).build();

    Map<String, Map<Integer, String>> nestedMapValue =
        ImmutableMap.<String, Map<Integer, String>>builder()
            .put(
                "sub1",
                ImmutableMap.<Integer, String>builder()
                    .put(37, "sub1sub1")
                    .put(96, "sub1sub2")
                    .build())
            .put(
                "sub2",
                ImmutableMap.<Integer, String>builder()
                    .put(47, "sub2sub1")
                    .put(90, "sub2sub2")
                    .build())
            .build();

    List<Integer> listValue = Arrays.asList(37, 96, 90);

    List<Integer> list2 = Arrays.asList(3, 2);
    List<List<Integer>> nestedListValue = Arrays.asList(listValue, list2);

    Map<String, Integer> udtValue =
        ImmutableMap.<String, Integer>builder().put("udtmem1", 47).put("udtmem2", 90).build();

    Map<String, Boolean> booleanUdtValue =
        ImmutableMap.<String, Boolean>builder().put("udtmem1", true).put("udtmem2", false).build();

    byte[] blobValue = new byte[] {12, 22, 32};

    Long baseValue = 98761234L;
    Struct value =
        new Struct(schema)
            .put("bigint", baseValue)
            .put("boolean", (baseValue.intValue() & 1) == 1)
            .put("double", (double) baseValue + 0.123)
            .put("float", baseValue.floatValue() + 0.987f)
            .put("int", baseValue.intValue())
            .put("smallint", baseValue.shortValue())
            .put("text", baseValue.toString())
            .put("tinyint", baseValue.byteValue())
            .put("map", mapValue)
            .put("mapnested", nestedMapValue)
            .put("list", listValue)
            .put("listnested", nestedListValue)
            .put("set", listValue)
            .put("setnested", nestedListValue)
            .put("tuple", listValue)
            .put("udt", udtValue)
            .put("udtfromlist", udtValue.values())
            .put("booleanudt", booleanUdtValue)
            .put("booleanudtfromlist", booleanUdtValue.values())
            .put("blob", blobValue)
            .put("point", "POINT (32.0 64.0)")
            .put("linestring", "LINESTRING (32.0 64.0, 48.5 96.5)")
            .put("polygon", "POLYGON ((0.0 0.0, 20.0 0.0, 25.0 25.0, 0.0 25.0, 0.0 0.0))")
            .put("daterange", "[* TO 2014-12-01]");

    runTaskWithRecords(new SinkRecord("mytopic", 0, null, null, null, value, 1234L));

    // Verify that the record was inserted properly in the database.
    List<Row> results = session.execute("SELECT * FROM types").all();
    assertThat(results.size()).isEqualTo(1);
    Row row = results.get(0);
    assertThat(row.getLong("bigintcol")).isEqualTo(baseValue);
    assertThat(row.getBoolean("booleancol")).isEqualTo((baseValue.intValue() & 1) == 1);
    assertThat(row.getDouble("doublecol")).isEqualTo((double) baseValue + 0.123);
    assertThat(row.getFloat("floatcol")).isEqualTo(baseValue.floatValue() + 0.987f);
    assertThat(row.getInt("intcol")).isEqualTo(baseValue.intValue());
    assertThat(row.getShort("smallintcol")).isEqualTo(baseValue.shortValue());
    assertThat(row.getString("textcol")).isEqualTo(baseValue.toString());
    assertThat(row.getByte("tinyintcol")).isEqualTo(baseValue.byteValue());
    assertThat(row.getMap("mapcol", String.class, Integer.class)).isEqualTo(mapValue);
    assertThat(row.getMap("mapnestedcol", String.class, Map.class)).isEqualTo(nestedMapValue);
    assertThat(row.getList("listcol", Integer.class)).isEqualTo(listValue);
    assertThat(row.getList("listnestedcol", Set.class))
        .isEqualTo(
            new ArrayList<Set>(Arrays.asList(new HashSet<>(listValue), new HashSet<>(list2))));
    assertThat(row.getSet("setcol", Integer.class)).isEqualTo(new HashSet<>(listValue));
    assertThat(row.getSet("setnestedcol", List.class)).isEqualTo(new HashSet<>(nestedListValue));

    DefaultTupleType tupleType =
        new DefaultTupleType(
            ImmutableList.of(DataTypes.SMALLINT, DataTypes.INT, DataTypes.INT),
            session.getContext());
    assertThat(row.getTupleValue("tuplecol")).isEqualTo(tupleType.newValue((short) 37, 96, 90));

    UserDefinedType udt =
        new UserDefinedTypeBuilder(keyspaceName, "myudt")
            .withField("udtmem1", DataTypes.INT)
            .withField("udtmem2", DataTypes.TEXT)
            .build();
    udt.attach(session.getContext());
    assertThat(row.getUdtValue("udtcol")).isEqualTo(udt.newValue(47, "90"));
    assertThat(row.getUdtValue("udtfromlistcol")).isEqualTo(udt.newValue(47, "90"));

    UserDefinedType booleanUdt =
        new UserDefinedTypeBuilder(keyspaceName, "mybooleanudt")
            .withField("udtmem1", DataTypes.BOOLEAN)
            .withField("udtmem2", DataTypes.TEXT)
            .build();
    booleanUdt.attach(session.getContext());
    assertThat(row.getUdtValue("booleanudtcol")).isEqualTo(booleanUdt.newValue(true, "false"));
    assertThat(row.getUdtValue("booleanudtfromlistcol"))
        .isEqualTo(booleanUdt.newValue(true, "false"));

    ByteBuffer blobcol = row.getByteBuffer("blobcol");
    assertThat(blobcol).isNotNull();
    assertThat(Bytes.getArray(blobcol)).isEqualTo(blobValue);
    if (ccm.getClusterType() == DSE) {
      assertThat(row.get("pointcol", GenericType.of(Point.class)))
          .isEqualTo(new DefaultPoint(32.0, 64.0));
      assertThat(row.get("linestringcol", GenericType.of(LineString.class)))
          .isEqualTo(
              new DefaultLineString(new DefaultPoint(32.0, 64.0), new DefaultPoint(48.5, 96.5)));
      assertThat(row.get("polygoncol", GenericType.of(Polygon.class)))
          .isEqualTo(
              new DefaultPolygon(
                  new DefaultPoint(0, 0),
                  new DefaultPoint(20, 0),
                  new DefaultPoint(25, 25),
                  new DefaultPoint(0, 25),
                  new DefaultPoint(0, 0)));
    }
    if (hasDateRange) {
      assertThat(row.get("daterangecol", GenericType.of(DateRange.class)))
          .isEqualTo(DateRange.parse("[* TO 2014-12-01]"));
    }
  }

  @Test
  void struct_value_struct_field() {
    conn.start(
        makeConnectorProperties(
            "bigintcol=value.bigint, "
                + "udtcol=value.struct, "
                + "booleanudtcol=value.booleanstruct"));

    Schema fieldSchema =
        SchemaBuilder.struct()
            .field("udtmem1", Schema.INT32_SCHEMA)
            .field("udtmem2", Schema.STRING_SCHEMA)
            .build();
    Struct fieldValue = new Struct(fieldSchema).put("udtmem1", 42).put("udtmem2", "the answer");

    Schema booleanFieldSchema =
        SchemaBuilder.struct()
            .field("udtmem1", Schema.BOOLEAN_SCHEMA)
            .field("udtmem2", Schema.STRING_SCHEMA)
            .build();
    Struct booleanFieldValue =
        new Struct(booleanFieldSchema).put("udtmem1", true).put("udtmem2", "the answer");

    Schema schema =
        SchemaBuilder.struct()
            .name("Kafka")
            .field("bigint", Schema.INT64_SCHEMA)
            .field("struct", fieldSchema)
            .field("booleanstruct", booleanFieldSchema)
            .build();

    Struct value =
        new Struct(schema)
            .put("bigint", 1234567L)
            .put("struct", fieldValue)
            .put("booleanstruct", booleanFieldValue);

    runTaskWithRecords(new SinkRecord("mytopic", 0, null, null, null, value, 1234L));

    // Verify that the record was inserted properly in the database.
    List<Row> results = session.execute("SELECT * FROM types").all();
    assertThat(results.size()).isEqualTo(1);
    Row row = results.get(0);
    assertThat(row.getLong("bigintcol")).isEqualTo(1234567L);

    UserDefinedType udt =
        new UserDefinedTypeBuilder(keyspaceName, "myudt")
            .withField("udtmem1", DataTypes.INT)
            .withField("udtmem2", DataTypes.TEXT)
            .build();
    udt.attach(session.getContext());
    assertThat(row.getUdtValue("udtcol")).isEqualTo(udt.newValue(42, "the answer"));

    UserDefinedType booleanUdt =
        new UserDefinedTypeBuilder(keyspaceName, "mybooleanudt")
            .withField("udtmem1", DataTypes.BOOLEAN)
            .withField("udtmem2", DataTypes.TEXT)
            .build();
    booleanUdt.attach(session.getContext());
    assertThat(row.getUdtValue("booleanudtcol")).isEqualTo(booleanUdt.newValue(true, "the answer"));
  }

  @Test
  void struct_optional_fields_missing() {
    conn.start(
        makeConnectorProperties(
            "bigintcol=value.bigint, intcol=value.int, smallintcol=value.smallint"));

    Schema schema =
        SchemaBuilder.struct()
            .name("Kafka")
            .field("bigint", Schema.OPTIONAL_INT64_SCHEMA)
            .field("boolean", Schema.OPTIONAL_BOOLEAN_SCHEMA)
            .field("double", Schema.OPTIONAL_FLOAT64_SCHEMA)
            .field("float", Schema.OPTIONAL_FLOAT32_SCHEMA)
            .field("int", Schema.OPTIONAL_INT32_SCHEMA)
            .field("smallint", Schema.OPTIONAL_INT16_SCHEMA)
            .field("text", Schema.OPTIONAL_STRING_SCHEMA)
            .field("tinyint", Schema.OPTIONAL_INT8_SCHEMA)
            .field("blob", Schema.OPTIONAL_BYTES_SCHEMA)
            .build();

    Long baseValue = 98761234L;
    Struct value = new Struct(schema).put("bigint", baseValue).put("int", baseValue.intValue());

    runTaskWithRecords(new SinkRecord("mytopic", 0, null, null, null, value, 1234L));

    // Verify that the record was inserted properly in the database.
    List<Row> results = session.execute("SELECT * FROM types").all();
    assertThat(results.size()).isEqualTo(1);
    Row row = results.get(0);
    assertThat(row.getLong("bigintcol")).isEqualTo(baseValue);
    assertThat(row.getInt("intcol")).isEqualTo(baseValue.intValue());
  }

  @Test
  void struct_optional_fields_with_values() {
    conn.start(
        makeConnectorProperties(
            "bigintcol=value.bigint, "
                + "booleancol=value.boolean, "
                + "doublecol=value.double, "
                + "floatcol=value.float, "
                + "intcol=value.int, "
                + "smallintcol=value.smallint, "
                + "textcol=value.text, "
                + "tinyintcol=value.tinyint, "
                + "blobcol=value.blob"));

    Schema schema =
        SchemaBuilder.struct()
            .name("Kafka")
            .field("bigint", Schema.OPTIONAL_INT64_SCHEMA)
            .field("boolean", Schema.OPTIONAL_BOOLEAN_SCHEMA)
            .field("double", Schema.OPTIONAL_FLOAT64_SCHEMA)
            .field("float", Schema.OPTIONAL_FLOAT32_SCHEMA)
            .field("int", Schema.OPTIONAL_INT32_SCHEMA)
            .field("smallint", Schema.OPTIONAL_INT16_SCHEMA)
            .field("text", Schema.OPTIONAL_STRING_SCHEMA)
            .field("tinyint", Schema.OPTIONAL_INT8_SCHEMA)
            .field("blob", Schema.OPTIONAL_BYTES_SCHEMA)
            .build();

    byte[] blobValue = new byte[] {12, 22, 32};

    Long baseValue = 98761234L;
    Struct value =
        new Struct(schema)
            .put("bigint", baseValue)
            .put("boolean", (baseValue.intValue() & 1) == 1)
            .put("double", (double) baseValue + 0.123)
            .put("float", baseValue.floatValue() + 0.987f)
            .put("int", baseValue.intValue())
            .put("smallint", baseValue.shortValue())
            .put("text", baseValue.toString())
            .put("tinyint", baseValue.byteValue())
            .put("blob", blobValue);

    runTaskWithRecords(new SinkRecord("mytopic", 0, null, null, null, value, 1234L));

    // Verify that the record was inserted properly in the database.
    List<Row> results = session.execute("SELECT * FROM types").all();
    assertThat(results.size()).isEqualTo(1);
    Row row = results.get(0);
    assertThat(row.getLong("bigintcol")).isEqualTo(baseValue);
    assertThat(row.getBoolean("booleancol")).isEqualTo((baseValue.intValue() & 1) == 1);
    assertThat(row.getDouble("doublecol")).isEqualTo((double) baseValue + 0.123);
    assertThat(row.getFloat("floatcol")).isEqualTo(baseValue.floatValue() + 0.987f);
    assertThat(row.getInt("intcol")).isEqualTo(baseValue.intValue());
    assertThat(row.getShort("smallintcol")).isEqualTo(baseValue.shortValue());
    assertThat(row.getString("textcol")).isEqualTo(baseValue.toString());
    assertThat(row.getByte("tinyintcol")).isEqualTo(baseValue.byteValue());
    ByteBuffer blobcol = row.getByteBuffer("blobcol");
    assertThat(blobcol).isNotNull();
    assertThat(Bytes.getArray(blobcol)).isEqualTo(blobValue);
  }

  @Test
  void struct_optional_field_with_default_value() {
    conn.start(makeConnectorProperties("bigintcol=value.bigint, intcol=value.int"));

    Schema schema =
        SchemaBuilder.struct()
            .name("Kafka")
            .field("bigint", Schema.OPTIONAL_INT64_SCHEMA)
            .field("int", SchemaBuilder.int32().optional().defaultValue(42).build())
            .build();

    Long baseValue = 98761234L;
    Struct value = new Struct(schema).put("bigint", baseValue);

    runTaskWithRecords(new SinkRecord("mytopic", 0, null, null, null, value, 1234L));

    // Verify that the record was inserted properly in the database.
    List<Row> results = session.execute("SELECT * FROM types").all();
    assertThat(results.size()).isEqualTo(1);
    Row row = results.get(0);
    assertThat(row.getLong("bigintcol")).isEqualTo(baseValue);
    assertThat(row.getInt("intcol")).isEqualTo(42);
  }

  @Test
  void raw_udt_value_from_struct() {
    conn.start(makeConnectorProperties("bigintcol=key, udtcol=value"));

    Schema schema =
        SchemaBuilder.struct()
            .name("Kafka")
            .field("udtmem1", Schema.INT32_SCHEMA)
            .field("udtmem2", Schema.STRING_SCHEMA)
            .build();
    Struct value = new Struct(schema).put("udtmem1", 42).put("udtmem2", "the answer");

    SinkRecord record = new SinkRecord("mytopic", 0, null, 98761234L, null, value, 1234L);
    runTaskWithRecords(record);

    // Verify that the record was inserted properly in the database.
    List<Row> results = session.execute("SELECT bigintcol, udtcol FROM types").all();
    assertThat(results.size()).isEqualTo(1);
    Row row = results.get(0);
    assertThat(row.getLong("bigintcol")).isEqualTo(98761234L);

    UserDefinedType udt =
        new UserDefinedTypeBuilder(keyspaceName, "myudt")
            .withField("udtmem1", DataTypes.INT)
            .withField("udtmem2", DataTypes.TEXT)
            .build();
    udt.attach(session.getContext());
    assertThat(row.getUdtValue("udtcol")).isEqualTo(udt.newValue(42, "the answer"));
  }

  @Test
  void raw_udt_value_and_cherry_pick_from_struct() {
    conn.start(makeConnectorProperties("bigintcol=key, udtcol=value, intcol=value.udtmem1"));

    Schema schema =
        SchemaBuilder.struct()
            .name("Kafka")
            .field("udtmem1", Schema.INT32_SCHEMA)
            .field("udtmem2", Schema.STRING_SCHEMA)
            .build();
    Struct value = new Struct(schema).put("udtmem1", 42).put("udtmem2", "the answer");

    SinkRecord record = new SinkRecord("mytopic", 0, null, 98761234L, null, value, 1234L);
    runTaskWithRecords(record);

    // Verify that the record was inserted properly in the database.
    List<Row> results = session.execute("SELECT bigintcol, udtcol, intcol FROM types").all();
    assertThat(results.size()).isEqualTo(1);
    Row row = results.get(0);
    assertThat(row.getLong("bigintcol")).isEqualTo(98761234L);

    UserDefinedType udt =
        new UserDefinedTypeBuilder(keyspaceName, "myudt")
            .withField("udtmem1", DataTypes.INT)
            .withField("udtmem2", DataTypes.TEXT)
            .build();
    udt.attach(session.getContext());
    assertThat(row.getUdtValue("udtcol")).isEqualTo(udt.newValue(42, "the answer"));
    assertThat(row.getInt("intcol")).isEqualTo(42);
  }

  @Test
  void multiple_records_multiple_topics() {
    conn.start(
        makeConnectorProperties(
            "bigintcol=value.bigint, doublecol=value.double",
            ImmutableMap.of(
                String.format("topic.yourtopic.%s.types.mapping", keyspaceName),
                "bigintcol=key, intcol=value")));

    // Set up records for "mytopic"
    Schema schema =
        SchemaBuilder.struct()
            .name("Kafka")
            .field("bigint", Schema.INT64_SCHEMA)
            .field("double", Schema.FLOAT64_SCHEMA)
            .build();
    Struct value1 = new Struct(schema).put("bigint", 1234567L).put("double", 42.0);
    Struct value2 = new Struct(schema).put("bigint", 9876543L).put("double", 21.0);

    SinkRecord record1 = new SinkRecord("mytopic", 0, null, null, null, value1, 1234L);
    SinkRecord record2 = new SinkRecord("mytopic", 0, null, null, null, value2, 1235L);

    // Set up a record for "yourtopic"
    SinkRecord record3 = new SinkRecord("yourtopic", 0, null, 5555L, null, 3333, 1000L);

    runTaskWithRecords(record1, record2, record3);

    // Verify that the record was inserted properly in the database.
    List<Row> results = session.execute("SELECT bigintcol, doublecol, intcol FROM types").all();
    assertThat(results.size()).isEqualTo(3);
    for (Row row : results) {
      if (row.getLong("bigintcol") == 1234567L) {
        assertThat(row.getDouble("doublecol")).isEqualTo(42.0);
        assertThat(row.getObject("intcol")).isNull();
      } else if (row.getLong("bigintcol") == 9876543L) {
        assertThat(row.getDouble("doublecol")).isEqualTo(21.0);
        assertThat(row.getObject("intcol")).isNull();
      } else if (row.getLong("bigintcol") == 5555L) {
        assertThat(row.getObject("doublecol")).isNull();
        assertThat(row.getInt("intcol")).isEqualTo(3333);
      }
    }
  }

  @Test
  void single_record_multiple_tables() {
    conn.start(
        makeConnectorProperties(
            "bigintcol=value.bigint, booleancol=value.boolean, intcol=value.int",
            ImmutableMap.of(
                String.format("topic.mytopic.%s.small_simple.mapping", keyspaceName),
                "bigintcol=value.bigint, intcol=value.int")));

    // Set up records for "mytopic"
    Schema schema =
        SchemaBuilder.struct()
            .name("Kafka")
            .field("bigint", Schema.INT64_SCHEMA)
            .field("boolean", Schema.BOOLEAN_SCHEMA)
            .field("int", Schema.INT32_SCHEMA)
            .build();
    Struct value = new Struct(schema).put("bigint", 1234567L).put("boolean", true).put("int", 5725);
    SinkRecord record = new SinkRecord("mytopic", 0, null, null, null, value, 1234L);

    runTaskWithRecords(record);

    // Verify that a record was inserted in each of small_simple and types tables.
    {
      List<Row> results = session.execute("SELECT * FROM small_simple").all();
      assertThat(results.size()).isEqualTo(1);
      Row row = results.get(0);
      assertThat(row.getLong("bigintcol")).isEqualTo(1234567L);
      assertThat(row.get("booleancol", GenericType.BOOLEAN)).isNull();
      assertThat(row.getInt("intcol")).isEqualTo(5725);
    }
    {
      List<Row> results = session.execute("SELECT * FROM types").all();
      assertThat(results.size()).isEqualTo(1);
      Row row = results.get(0);
      assertThat(row.getLong("bigintcol")).isEqualTo(1234567L);
      assertThat(row.getBoolean("booleancol")).isTrue();
      assertThat(row.getInt("intcol")).isEqualTo(5725);
    }
  }

  /** Test for KAF-83 (case-sensitive fields and columns). */
  @Test
  void single_map_quoted_fields_to_quoted_columns() {
    session.execute(
        SimpleStatement.builder(
                "CREATE TABLE \"CASE_SENSITIVE\" ("
                    + "\"bigint col\" bigint, "
                    + "\"boolean-col\" boolean, "
                    + "\"INT COL\" int,"
                    + "\"TEXT.COL\" text,"
                    + "PRIMARY KEY (\"bigint col\", \"boolean-col\")"
                    + ")")
            .setTimeout(Duration.ofSeconds(10))
            .build());
    conn.start(
        makeConnectorProperties(
            "\"bigint col\" = \"key.bigint field\", "
                + "\"boolean-col\" = \"key.boolean-field\", "
                + "\"INT COL\" = \"value.INT FIELD\", "
                + "\"TEXT.COL\" = \"value.TEXT.FIELD\"",
            "CASE_SENSITIVE",
            null));

    // Set up records for "mytopic"
    Struct key =
        new Struct(
                SchemaBuilder.struct()
                    .name("Kafka")
                    .field("bigint field", Schema.INT64_SCHEMA)
                    .field("boolean-field", Schema.BOOLEAN_SCHEMA)
                    .build())
            .put("bigint field", 1234567L)
            .put("boolean-field", true);
    Struct value =
        new Struct(
                SchemaBuilder.struct()
                    .name("Kafka")
                    .field("INT FIELD", Schema.INT32_SCHEMA)
                    .field("TEXT.FIELD", Schema.STRING_SCHEMA)
                    .build())
            .put("INT FIELD", 5725)
            .put("TEXT.FIELD", "foo");

    // Note: with the current mapping grammar, it is not possible to distinguish f1.f2 (i.e. a field
    // "f1" containing a nested field "f2") from a field named "f1.f2".

    SinkRecord record = new SinkRecord("mytopic", 0, null, key, null, value, 1234L);

    runTaskWithRecords(record);

    // Verify that a record was inserted
    List<Row> results = session.execute("SELECT * FROM \"CASE_SENSITIVE\"").all();
    assertThat(results.size()).isEqualTo(1);
    Row row = results.get(0);
    assertThat(row.getLong("\"bigint col\"")).isEqualTo(1234567L);
    assertThat(row.getBoolean("\"boolean-col\"")).isTrue();
    assertThat(row.getInt("\"INT COL\"")).isEqualTo(5725);
    assertThat(row.getString("\"TEXT.COL\"")).isEqualTo("foo");
  }
}
