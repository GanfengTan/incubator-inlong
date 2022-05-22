/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.inlong.sort.parser;

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.test.util.AbstractTestBase;
import org.apache.inlong.sort.formats.common.FloatFormatInfo;
import org.apache.inlong.sort.formats.common.IntFormatInfo;
import org.apache.inlong.sort.formats.common.LongFormatInfo;
import org.apache.inlong.sort.formats.common.StringFormatInfo;
import org.apache.inlong.sort.formats.common.TimestampFormatInfo;
import org.apache.inlong.sort.parser.impl.FlinkSqlParser;
import org.apache.inlong.sort.protocol.FieldInfo;
import org.apache.inlong.sort.protocol.GroupInfo;
import org.apache.inlong.sort.protocol.StreamInfo;
import org.apache.inlong.sort.protocol.node.Node;
import org.apache.inlong.sort.protocol.node.extract.MySqlExtractNode;
import org.apache.inlong.sort.protocol.node.extract.SqlServerExtractNode;
import org.apache.inlong.sort.protocol.node.format.JsonFormat;
import org.apache.inlong.sort.protocol.node.load.KafkaLoadNode;
import org.apache.inlong.sort.protocol.node.load.SqlServerLoadNode;
import org.apache.inlong.sort.protocol.transformation.FieldRelationShip;
import org.apache.inlong.sort.protocol.transformation.relation.NodeRelationShip;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Test for SqlServer{@link SqlServerLoadNode} SQL parser.
 */
public class SqlServerNodeSqlParseTest extends AbstractTestBase {

    /**
     * Build mysql extract node.
     */
    private MySqlExtractNode buildMySQLExtractNode(String id) {
        List<FieldInfo> fields = Arrays.asList(new FieldInfo("id", new LongFormatInfo()),
                new FieldInfo("name", new StringFormatInfo()));
        //if you hope hive load mode of append,please add this config.
        Map<String, String> map = new HashMap<>();
        map.put("append-mode", "true");
        return new MySqlExtractNode(id, "mysql", fields,
                null, map, "id",
                Collections.singletonList("work1"), "localhost", "root", "password",
                "inlong", null, null,
                null, null);
    }

    private SqlServerExtractNode buildSqlServerExtractNode(String id) {
        List<FieldInfo> fields = Arrays.asList(new FieldInfo("id", new LongFormatInfo()),
                new FieldInfo("val_char", new StringFormatInfo()));
        return new SqlServerExtractNode(id, "sqlserver_out", fields, null, null,
                null, "localhost", 1433, "SA", "INLONG*123",
                "column_type_test", "dbo", "full_types", null);
    }


    private KafkaLoadNode buildKafkaNode(String id) {
        List<FieldInfo> fields = Arrays.asList(new FieldInfo("id", new LongFormatInfo()),
                new FieldInfo("val_char", new StringFormatInfo()));
        List<FieldRelationShip> relations = Arrays
                .asList(new FieldRelationShip(new FieldInfo("id", new LongFormatInfo()),
                                new FieldInfo("id", new LongFormatInfo())),
                        new FieldRelationShip(new FieldInfo("val_char", new StringFormatInfo()),
                                new FieldInfo("val_char", new StringFormatInfo()))
                );
        return new KafkaLoadNode(id, "kafka_output", fields, relations, null, null,
                "sqlserver", "localhost:9092",
                new JsonFormat(), null,
                null, "id");
    }


    /**
     * Build sqlserver load node.
     */
    private SqlServerLoadNode buildSqlServerLoadNode(String id) {
        List<FieldInfo> fields = Arrays.asList(new FieldInfo("id", new LongFormatInfo()),
                new FieldInfo("name", new StringFormatInfo()));
        List<FieldRelationShip> relations = Arrays
                .asList(new FieldRelationShip(new FieldInfo("id", new LongFormatInfo()),
                                new FieldInfo("id", new LongFormatInfo())),
                        new FieldRelationShip(new FieldInfo("name", new StringFormatInfo()),
                                new FieldInfo("name", new StringFormatInfo()))
                );
        return new SqlServerLoadNode(id, "sqlserver_out", fields, relations, null, null, 1,
                null, "jdbc:sqlserver://localhost:1433", "SA",
                "INLONG*123", "column_type_test.dbo", "work1", null);
    }

    /**
     * Build node relation.
     */
    private NodeRelationShip buildNodeRelation(List<Node> inputs, List<Node> outputs) {
        List<String> inputIds = inputs.stream().map(Node::getId).collect(Collectors.toList());
        List<String> outputIds = outputs.stream().map(Node::getId).collect(Collectors.toList());
        return new NodeRelationShip(inputIds, outputIds);
    }

    /**
     * Test extract data from mysql and load data to sqlserver.
     */
    @Test
    public void testSqlServerLoad() throws Exception {
        EnvironmentSettings settings = EnvironmentSettings
                .newInstance()
                .useBlinkPlanner()
                .inStreamingMode()
                .build();
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        env.enableCheckpointing(10000);
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env, settings);
        Node mySqlExtractNode = buildMySQLExtractNode("1");
        Node sqlServerLoadNode = buildSqlServerLoadNode("2");
        StreamInfo streamInfoToHDFS = new StreamInfo("1L", Arrays.asList(mySqlExtractNode, sqlServerLoadNode),
                Collections.singletonList(buildNodeRelation(Collections.singletonList(mySqlExtractNode),
                        Collections.singletonList(sqlServerLoadNode))));
        GroupInfo groupInfoToHDFS = new GroupInfo("1", Collections.singletonList(streamInfoToHDFS));
        FlinkSqlParser parser = FlinkSqlParser.getInstance(tableEnv, groupInfoToHDFS);
        Assert.assertTrue(parser.parse().tryExecute());
    }

}
