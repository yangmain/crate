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

package io.crate.planner.projection;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import io.crate.planner.symbol.Symbol;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TopNProjection extends Projection {

    public static final ProjectionFactory<TopNProjection> FACTORY = new ProjectionFactory<TopNProjection>() {
        @Override
        public TopNProjection newInstance() {
            return new TopNProjection();
        }
    };


    private int limit;
    private int offset;

    List<Symbol> outputs = ImmutableList.of();

    List<Symbol> orderBy;
    boolean[] reverseFlags;

    public TopNProjection() {
        super();
    }

    public TopNProjection(int limit, int offset) {
        this.limit = limit;
        this.offset = offset;
    }

    public TopNProjection(int limit, int offset, List<Symbol> orderBy, boolean[] reverseFlags) {
        this(limit, offset);
        Preconditions.checkArgument(orderBy.size() == reverseFlags.length,
                "reverse flags length does not match orderBy items count");
        this.orderBy = orderBy;
        this.reverseFlags = reverseFlags;
    }

    @Override
    public ImmutableList<Symbol> outputs() {
        return ImmutableList.copyOf(outputs);
    }

    public void outputs(List<Symbol> outputs) {
        this.outputs = outputs;
    }

    public int limit() {
        return limit;
    }

    public int offset() {
        return offset;
    }

    public List<Symbol> orderBy() {
        return orderBy;
    }

    public boolean[] reverseFlags() {
        return reverseFlags;
    }


    public boolean isOrdered() {
        return reverseFlags != null && reverseFlags.length > 0;
    }


    @Override
    public ProjectionType projectionType() {
        return ProjectionType.TOPN;
    }

    @Override
    public <C, R> R accept(ProjectionVisitor<C, R> visitor, C context) {
        return visitor.visitTopNProjection(this, context);
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        offset = in.readVInt();
        limit = in.readVInt();

        int numOutputs = in.readVInt();
        outputs = new ArrayList(numOutputs);
        for (int i = 0; i < numOutputs; i++) {
            outputs.add(Symbol.fromStream(in));
        }

        int numOrderBy = in.readVInt();

        if (numOrderBy > 0) {
            reverseFlags = new boolean[numOrderBy];

            for (int i = 0; i < reverseFlags.length; i++) {
                reverseFlags[i] = in.readBoolean();
            }

            orderBy = new ArrayList(numOrderBy);
            for (int i = 0; i < reverseFlags.length; i++) {
                orderBy.add(Symbol.fromStream(in));
            }
        }

    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeVInt(offset);
        out.writeVInt(limit);
        out.writeVInt(outputs.size());
        for (Symbol symbol : outputs) {
            Symbol.toStream(symbol, out);
        }
        if (isOrdered()) {
            out.writeVInt(reverseFlags.length);
            for (boolean reverseFlag : reverseFlags) {
                out.writeBoolean(reverseFlag);
            }
            for (Symbol symbol : orderBy) {
                Symbol.toStream(symbol, out);
            }
        } else {
            out.writeVInt(0);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TopNProjection that = (TopNProjection) o;

        if (limit != that.limit) return false;
        if (offset != that.offset) return false;
        if (orderBy != null ? !orderBy.equals(that.orderBy) : that.orderBy != null) return false;
        if (outputs != null ? !outputs.equals(that.outputs) : that.outputs != null) return false;
        if (!Arrays.equals(reverseFlags, that.reverseFlags)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = limit;
        result = 31 * result + offset;
        result = 31 * result + (outputs != null ? outputs.hashCode() : 0);
        result = 31 * result + (orderBy != null ? orderBy.hashCode() : 0);
        result = 31 * result + (reverseFlags != null ? Arrays.hashCode(reverseFlags) : 0);
        return result;
    }

    @Override
    public String toString() {
        return "TopNProjection{" +
                "outputs=" + outputs +
                ", limit=" + limit +
                ", offset=" + offset +
                ", orderBy=" + orderBy +
                ", reverseFlags=" + Arrays.toString(reverseFlags) +
                '}';
    }
}
