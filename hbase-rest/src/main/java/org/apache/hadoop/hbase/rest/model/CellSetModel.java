/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.rest.model;

import static org.apache.hadoop.hbase.rest.model.CellModel.MAGIC_LENGTH;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.rest.ProtobufMessageHandler;
import org.apache.hadoop.hbase.rest.RestUtil;
import org.apache.yetus.audience.InterfaceAudience;

import org.apache.hbase.thirdparty.com.google.protobuf.CodedInputStream;
import org.apache.hbase.thirdparty.com.google.protobuf.Message;
import org.apache.hbase.thirdparty.com.google.protobuf.UnsafeByteOperations;

import org.apache.hadoop.hbase.shaded.rest.protobuf.generated.CellMessage.Cell;
import org.apache.hadoop.hbase.shaded.rest.protobuf.generated.CellSetMessage.CellSet;

/**
 * Representation of a grouping of cells. May contain cells from more than one row. Encapsulates
 * RowModel and CellModel models.
 *
 * <pre>
 * &lt;complexType name="CellSet"&gt;
 *   &lt;sequence&gt;
 *     &lt;element name="row" type="tns:Row" maxOccurs="unbounded"
 *       minOccurs="1"&gt;&lt;/element&gt;
 *   &lt;/sequence&gt;
 * &lt;/complexType&gt;
 *
 * &lt;complexType name="Row"&gt;
 *   &lt;sequence&gt;
 *     &lt;element name="key" type="base64Binary"&gt;&lt;/element&gt;
 *     &lt;element name="cell" type="tns:Cell"
 *       maxOccurs="unbounded" minOccurs="1"&gt;&lt;/element&gt;
 *   &lt;/sequence&gt;
 * &lt;/complexType&gt;
 *
 * &lt;complexType name="Cell"&gt;
 *   &lt;sequence&gt;
 *     &lt;element name="value" maxOccurs="1" minOccurs="1"&gt;
 *       &lt;simpleType&gt;
 *         &lt;restriction base="base64Binary"/&gt;
 *       &lt;/simpleType&gt;
 *     &lt;/element&gt;
 *   &lt;/sequence&gt;
 *   &lt;attribute name="column" type="base64Binary" /&gt;
 *   &lt;attribute name="timestamp" type="int" /&gt;
 * &lt;/complexType&gt;
 * </pre>
 */
@XmlRootElement(name = "CellSet")
@XmlAccessorType(XmlAccessType.NONE)
@InterfaceAudience.Private
public class CellSetModel implements Serializable, ProtobufMessageHandler {
  private static final long serialVersionUID = 1L;

  @XmlElement(name = "Row")
  private List<RowModel> rows;

  /**
   * Constructor
   */
  public CellSetModel() {
    this.rows = new ArrayList<>();
  }

  /**
   * @param rows the rows
   */
  public CellSetModel(List<RowModel> rows) {
    super();
    this.rows = rows;
  }

  /**
   * Add a row to this cell set
   * @param row the row
   */
  public void addRow(RowModel row) {
    rows.add(row);
  }

  /** Returns the rows */
  public List<RowModel> getRows() {
    return rows;
  }

  @Override
  public Message messageFromObject() {
    CellSet.Builder builder = CellSet.newBuilder();
    for (RowModel row : getRows()) {
      CellSet.Row.Builder rowBuilder = CellSet.Row.newBuilder();
      if (row.getKeyLength() == MAGIC_LENGTH) {
        rowBuilder.setKey(UnsafeByteOperations.unsafeWrap(row.getKey()));
      } else {
        rowBuilder.setKey(UnsafeByteOperations.unsafeWrap(row.getKeyArray(), row.getKeyOffset(),
          row.getKeyLength()));
      }
      for (CellModel cell : row.getCells()) {
        Cell.Builder cellBuilder = Cell.newBuilder();
        cellBuilder.setColumn(UnsafeByteOperations.unsafeWrap(cell.getColumn()));
        if (cell.getValueLength() == MAGIC_LENGTH) {
          cellBuilder.setData(UnsafeByteOperations.unsafeWrap(cell.getValue()));
        } else {
          cellBuilder.setData(UnsafeByteOperations.unsafeWrap(cell.getValueArray(),
            cell.getValueOffset(), cell.getValueLength()));
        }
        if (cell.hasUserTimestamp()) {
          cellBuilder.setTimestamp(cell.getTimestamp());
        }
        rowBuilder.addValues(cellBuilder);
      }
      builder.addRows(rowBuilder);
    }
    return builder.build();
  }

  @Override
  public ProtobufMessageHandler getObjectFromMessage(CodedInputStream cis) throws IOException {
    CellSet.Builder builder = CellSet.newBuilder();
    RestUtil.mergeFrom(builder, cis);
    for (CellSet.Row row : builder.getRowsList()) {
      RowModel rowModel = new RowModel(row.getKey().toByteArray());
      for (Cell cell : row.getValuesList()) {
        long timestamp = HConstants.LATEST_TIMESTAMP;
        if (cell.hasTimestamp()) {
          timestamp = cell.getTimestamp();
        }
        rowModel.addCell(
          new CellModel(cell.getColumn().toByteArray(), timestamp, cell.getData().toByteArray()));
      }
      addRow(rowModel);
    }
    return this;
  }
}
