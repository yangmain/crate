/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.planner.node;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import io.crate.metadata.*;
import io.crate.operator.aggregation.impl.CountAggregation;
import io.crate.planner.projection.GroupProjection;
import io.crate.planner.projection.TopNProjection;
import io.crate.planner.symbol.Aggregation;
import io.crate.planner.symbol.Reference;
import io.crate.planner.symbol.Symbol;
import org.cratedb.DataType;
import org.elasticsearch.common.io.stream.BytesStreamInput;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.junit.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class MergeNodeTest {


    @Test
    public void testSerialization() throws Exception {
        MergeNode node = new MergeNode("merge", 2);
        node.contextId(UUID.randomUUID());
        node.executionNodes(Sets.newHashSet("node1", "node2"));
        node.inputTypes(Arrays.asList(DataType.NULL, DataType.STRING));

        Reference nameRef = TestingHelpers.createReference("name", DataType.STRING);
        GroupProjection groupProjection = new GroupProjection();
        groupProjection.keys(Arrays.<Symbol>asList(nameRef));
        groupProjection.values(Arrays.asList(
                new Aggregation(
                        new FunctionIdent(CountAggregation.NAME, ImmutableList.<DataType>of()),
                        ImmutableList.<Symbol>of(),
                        Aggregation.Step.PARTIAL,
                        Aggregation.Step.FINAL
                )
        ));
        TopNProjection topNProjection = new TopNProjection(10, 0);

        node.projections(Arrays.asList(groupProjection, topNProjection));

        BytesStreamOutput output = new BytesStreamOutput();
        node.writeTo(output);


        BytesStreamInput input = new BytesStreamInput(output.bytes());
        MergeNode node2 = new MergeNode();
        node2.readFrom(input);

        assertThat(node.numUpstreams(), is(node2.numUpstreams()));
        assertThat(node.executionNodes(), is(node2.executionNodes()));
        assertThat(node.contextId(), is(node2.contextId()));
        assertThat(node.inputTypes(), is(node2.inputTypes()));
    }
}
