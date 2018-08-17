/*
 * Copyright DataStax, Inc.
 *
 * This software is subject to the below license agreement.
 * DataStax may make changes to the agreement from time to time,
 * and will post the amended terms at
 * https://www.datastax.com/terms/datastax-dse-bulk-utility-license-terms.
 */
package com.datastax.kafkaconnector;

import static com.datastax.oss.protocol.internal.ProtocolConstants.DataType.ASCII;
import static com.datastax.oss.protocol.internal.ProtocolConstants.DataType.VARCHAR;

import com.datastax.kafkaconnector.util.SinkUtil;
import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.cql.ColumnDefinitions;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.type.DataType;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.core.type.codec.TypeCodec;
import com.datastax.oss.driver.api.core.type.reflect.GenericType;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.apache.kafka.common.config.ConfigException;

/**
 * Maps {@link Record}s into {@link BoundStatement}s, applying any necessary transformations via
 * codecs.
 */
public class RecordMapper {
  private final PreparedStatement insertStatement;
  private final List<Integer> pkIndices;
  private final Mapping mapping;
  private final RecordMetadata recordMetadata;
  private final boolean allowExtraFields;
  private final boolean allowMissingFields;

  /** Whether to map null input to "unset" */
  private final boolean nullToUnset;

  RecordMapper(
      PreparedStatement insertStatement,
      Mapping mapping,
      RecordMetadata recordMetadata,
      boolean nullToUnset,
      boolean allowExtraFields,
      boolean allowMissingFields) {
    this.insertStatement = insertStatement;
    this.pkIndices = insertStatement.getPartitionKeyIndices();
    this.mapping = mapping;
    this.recordMetadata = recordMetadata;
    this.nullToUnset = nullToUnset;
    this.allowExtraFields = allowExtraFields;
    this.allowMissingFields = allowMissingFields;
  }

  public BoundStatement map(Record record) {
    Object raw;
    DataType cqlType;
    if (!allowMissingFields) {
      ensureAllFieldsPresent(record.fields());
    }
    BoundStatementBuilder builder = insertStatement.boundStatementBuilder();
    ColumnDefinitions variableDefinitions = insertStatement.getVariableDefinitions();
    for (String field : record.fields()) {
      Collection<CqlIdentifier> columns = mapping.fieldToColumns(CqlIdentifier.fromInternal(field));
      if ((columns == null || columns.isEmpty()) && !allowExtraFields) {
        throw new ConfigException(
            "Extraneous field '"
                + getExternalName(field)
                + "' was found in record. "
                + "Please declare it explicitly in the mapping.");
      }
      if (columns != null) {
        for (CqlIdentifier column : columns) {
          cqlType = variableDefinitions.get(column).getType();
          GenericType<?> fieldType = recordMetadata.getFieldType(field, cqlType);
          if (fieldType != null) {
            raw = record.getFieldValue(field);
            bindColumn(builder, column, raw, cqlType, fieldType);
          }
        }
      }
    }
    if (record.getTimestamp() != null) {
      bindColumn(
          builder,
          CqlIdentifier.fromInternal(SinkUtil.TIMESTAMP_VARNAME),
          record.getTimestamp() * 1000,
          DataTypes.BIGINT,
          GenericType.LONG);
    }
    BoundStatement bs = builder.build();
    ensurePartitionKeySet(bs);
    return bs;
  }

  private static String getExternalName(String field) {
    if (field.endsWith(RawData.FIELD_NAME)) {
      // e.g. value.__self => value
      return field.substring(0, field.length() - RawData.FIELD_NAME.length() - 1);
    }
    return field;
  }

  private <T> void bindColumn(
      BoundStatementBuilder builder,
      CqlIdentifier variable,
      T raw,
      DataType cqlType,
      GenericType<? extends T> javaType) {
    TypeCodec<T> codec = mapping.codec(variable, cqlType, javaType);
    ByteBuffer bb = codec.encode(raw, builder.protocolVersion());
    // Account for nullToUnset.
    if (isNull(bb, cqlType)) {
      if (isPartitionKey(variable)) {
        throw new ConfigException(
            "Partition key column "
                + variable.asCql(true)
                + " cannot be mapped to null. "
                + "Check that your mapping setting matches your dataset contents.");
      }
      if (nullToUnset) {
        return;
      }
    }
    builder.setBytesUnsafe(variable, bb);
  }

  private boolean isNull(ByteBuffer bb, DataType cqlType) {
    if (bb == null) {
      return true;
    }
    if (bb.hasRemaining()) {
      return false;
    }
    switch (cqlType.getProtocolCode()) {
      case VARCHAR:
      case ASCII:
        // empty strings are encoded as zero-length buffers,
        // and should not be considered as nulls.
        return false;
      default:
        return true;
    }
  }

  private boolean isPartitionKey(CqlIdentifier variable) {
    return pkIndices.contains(insertStatement.getVariableDefinitions().firstIndexOf(variable));
  }

  private void ensureAllFieldsPresent(Set<String> recordFields) {
    ColumnDefinitions variables = insertStatement.getVariableDefinitions();
    for (int i = 0; i < variables.size(); i++) {
      CqlIdentifier variable = variables.get(i).getName();
      if (variable.asInternal().equals(SinkUtil.TIMESTAMP_VARNAME)) {
        // Not a real field; it's just the bound variable for the timestamp.
        continue;
      }
      CqlIdentifier field = mapping.columnToField(variable);
      if (!recordFields.contains(field.asInternal())) {
        throw new ConfigException(
            "Required field '"
                + getExternalName(field.asInternal())
                + "' (mapped to column "
                + variable.asCql(true)
                + ") was missing from record. "
                + "Please remove it from the mapping.");
      }
    }
  }

  private void ensurePartitionKeySet(BoundStatement bs) {
    if (pkIndices != null) {
      for (int pkIndex : pkIndices) {
        if (!bs.isSet(pkIndex)) {
          CqlIdentifier variable = insertStatement.getVariableDefinitions().get(pkIndex).getName();
          throw new ConfigException(
              "Partition key column "
                  + variable.asCql(true)
                  + " cannot be left unmapped. "
                  + "Check that your mapping setting matches your dataset contents.");
        }
      }
    }
  }
}