/**
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

package org.apache.hadoop.hive.ql.hooks;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.SetUtils;
import org.apache.commons.io.output.StringBuilderWriter;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.api.FieldSchema;
import org.apache.hadoop.hive.metastore.api.Table;
import org.apache.hadoop.hive.ql.QueryPlan;
import org.apache.hadoop.hive.ql.exec.ColumnInfo;
import org.apache.hadoop.hive.ql.exec.SelectOperator;
import org.apache.hadoop.hive.ql.exec.TaskRunner;
import org.apache.hadoop.hive.ql.exec.Utilities;
import org.apache.hadoop.hive.ql.hooks.HookContext.HookType;
import org.apache.hadoop.hive.ql.hooks.LineageInfo.BaseColumnInfo;
import org.apache.hadoop.hive.ql.hooks.LineageInfo.Dependency;
import org.apache.hadoop.hive.ql.hooks.LineageInfo.Predicate;
import org.apache.hadoop.hive.ql.optimizer.lineage.LineageCtx.Index;
import org.apache.hadoop.hive.ql.plan.HiveOperation;
import org.apache.hadoop.hive.ql.session.SessionState;
import org.apache.hadoop.hive.ql.session.SessionState.LogHelper;

import com.google.common.collect.Lists;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.gson.stream.JsonWriter;

/**
 * Implementation of a post execute hook that logs lineage info to a log file.
 */
public class LineageLogger implements ExecuteWithHookContext {

  private static final Log LOG = LogFactory.getLog(LineageLogger.class);

  private static final HashSet<String> OPERATION_NAMES = new HashSet<String>();

  static {
    OPERATION_NAMES.add(HiveOperation.QUERY.getOperationName());
    OPERATION_NAMES.add(HiveOperation.CREATETABLE_AS_SELECT.getOperationName());
    OPERATION_NAMES.add(HiveOperation.ALTERVIEW_AS.getOperationName());
    OPERATION_NAMES.add(HiveOperation.CREATEVIEW.getOperationName());
  }

  private static final String FORMAT_VERSION = "1.0";

  final static class Edge {
    public static enum Type {
      PROJECTION, PREDICATE
    }

    private Set<Vertex> sources;
    private Set<Vertex> targets;
    private String expr;
    private Type type;

    Edge(Set<Vertex> sources, Set<Vertex> targets, String expr, Type type) {
      this.sources = sources;
      this.targets = targets;
      this.expr = expr;
      this.type = type;
    }
  }

  final static class Vertex {
    public static enum Type {
      COLUMN, TABLE
    }
    private Type type;
    private String label;
    private int id;

    Vertex(String label) {
      this(label, Type.COLUMN);
    }

    Vertex(String label, Type type) {
      this.label = label;
      this.type = type;
    }

    @Override
    public int hashCode() {
      return label.hashCode() + type.hashCode() * 3;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof Vertex)) {
        return false;
      }
      Vertex vertex = (Vertex) obj;
      return label.equals(vertex.label) && type == vertex.type;
    }
  }

  @Override
  public void run(HookContext hookContext) {
    assert(hookContext.getHookType() == HookType.POST_EXEC_HOOK);
    QueryPlan plan = hookContext.getQueryPlan();
    Index index = hookContext.getIndex();
    SessionState ss = SessionState.get();
    if (ss != null && index != null
        && OPERATION_NAMES.contains(plan.getOperationName())) {
      try {
        StringBuilderWriter out = new StringBuilderWriter(1024);
        JsonWriter writer = new JsonWriter(out);
        writer.setIndent("  ");

        out.append("POSTHOOK: LINEAGE: ");
        String queryStr = plan.getQueryStr().trim();
        writer.beginObject();
        writer.name("version").value(FORMAT_VERSION);
        HiveConf conf = ss.getConf();
        boolean testMode = conf.getBoolVar(HiveConf.ConfVars.HIVE_IN_TEST);
        if (!testMode) {
          // Don't emit user/timestamp info in test mode,
          // so that the test golden output file is fixed.
          long queryTime = plan.getQueryStartTime().longValue();
          writer.name("user").value(hookContext.getUgi().getUserName());
          writer.name("timestamp").value(queryTime/1000);
          writer.name("jobIds");
          writer.beginArray();
          List<TaskRunner> tasks = hookContext.getCompleteTaskList();
          if (tasks != null && !tasks.isEmpty()) {
            for (TaskRunner task: tasks) {
              String jobId = task.getTask().getJobID();
              if (jobId != null) {
                writer.value(jobId);
              }
            }
          }
          writer.endArray();
        }
        writer.name("engine").value(
          HiveConf.getVar(conf, HiveConf.ConfVars.HIVE_EXECUTION_ENGINE));
        writer.name("hash").value(getQueryHash(queryStr));
        writer.name("queryText").value(queryStr);

        List<Edge> edges = getEdges(plan, index);
        Set<Vertex> vertices = getVertices(edges);
        writeEdges(writer, edges);
        writeVertices(writer, vertices);
        writer.endObject();
        writer.close();

        // Log the lineage info
        String lineage = out.toString();
        if (testMode) {
          // Log to console
          log(lineage);
        } else {
          // In none test mode, emit to a log file,
          // which can be different from the normal hive.log.
          // For example, using NoDeleteRollingFileAppender to
          // log to some file with different rolling policy.
          LOG.info(lineage);
        }
      } catch (Throwable t) {
        // Don't fail the query just because of any lineage issue.
        log("Failed to log lineage graph, query is not affected\n"
          + org.apache.hadoop.util.StringUtils.stringifyException(t));
      }
    }
  }

  /**
   * Log an error to console if available.
   */
  private void log(String error) {
    LogHelper console = SessionState.getConsole();
    if (console != null) {
      console.printError(error);
    }
  }

  /**
   * Based on the final select operator, find out all the target columns.
   * For each target column, find out its sources based on the dependency index.
   */
  private List<Edge> getEdges(QueryPlan plan, Index index) {
    List<FieldSchema> fieldSchemas = plan.getResultSchema().getFieldSchemas();
    int fields = fieldSchemas == null ? 0 : fieldSchemas.size();
    SelectOperator finalSelOp = index.getFinalSelectOp();
    List<Edge> edges = new ArrayList<Edge>();
    if (finalSelOp != null && fields > 0) {
      Map<ColumnInfo, Dependency> colMap = index.getDependencies(finalSelOp);
      List<Dependency> dependencies = colMap != null ? Lists.newArrayList(colMap.values()) : null;
      if (dependencies == null || dependencies.size() != fields) {
        log("Result schema has " + fields
          + " fields, but we don't get as many dependencies");
      } else {
        String destTableName = null;
        List<String> colNames = null;
        // Based on the plan outputs, find out the target table name and column names.
        for (WriteEntity output : plan.getOutputs()) {
          if (output.getType() == Entity.Type.TABLE) {
            org.apache.hadoop.hive.ql.metadata.Table t = output.getTable();
            destTableName = t.getDbName() + "." + t.getTableName();
            List<FieldSchema> cols = t.getCols();
            if (cols != null && !cols.isEmpty()) {
              colNames = Utilities.getColumnNamesFromFieldSchema(cols);
            }
            break;
          }
        }

        // Go through each target column, generate the lineage edges.
        Set<Vertex> allTargets = new LinkedHashSet<Vertex>();
        Map<String, Vertex> allSources = new LinkedHashMap<String, Vertex>();
        for (int i = 0; i < fields; i++) {
          Vertex target = new Vertex(
            getTargetFieldName(i, destTableName, colNames, fieldSchemas));
          allTargets.add(target);
          Dependency dep = dependencies.get(i);
          String expr = dep.getExpr();
          Set<Vertex> sources = createSourceVertices(allSources, dep.getBaseCols());
          Edge edge = findSimilarEdgeBySources(edges, sources, expr, Edge.Type.PROJECTION);
          if (edge == null) {
            Set<Vertex> targets = new LinkedHashSet<Vertex>();
            targets.add(target);
            edges.add(new Edge(sources, targets, expr, Edge.Type.PROJECTION));
          } else {
            edge.targets.add(target);
          }
        }
        Set<Predicate> conds = index.getPredicates(finalSelOp);
        if (conds != null && !conds.isEmpty()) {
          for (Predicate cond: conds) {
            String expr = cond.getExpr();
            Set<Vertex> sources = createSourceVertices(allSources, cond.getBaseCols());
            Edge edge = findSimilarEdgeByTargets(edges, allTargets, expr, Edge.Type.PREDICATE);
            if (edge == null) {
              edges.add(new Edge(sources, allTargets, expr, Edge.Type.PREDICATE));
            } else {
              edge.sources.addAll(sources);
            }
          }
        }
      }
    }
    return edges;
  }

  /**
   * Convert a list of columns to a set of vertices.
   * Use cached vertices if possible.
   */
  private Set<Vertex> createSourceVertices(
      Map<String, Vertex> srcVertexCache, Collection<BaseColumnInfo> baseCols) {
    Set<Vertex> sources = new LinkedHashSet<Vertex>();
    if (baseCols != null && !baseCols.isEmpty()) {
      for(BaseColumnInfo col: baseCols) {
        Table table = col.getTabAlias().getTable();
        if (table.isTemporary()) {
          // Ignore temporary tables
          continue;
        }
        Vertex.Type type = Vertex.Type.TABLE;
        String tableName = table.getDbName() + "." + table.getTableName();
        FieldSchema fieldSchema = col.getColumn();
        String label = tableName;
        if (fieldSchema != null) {
          type = Vertex.Type.COLUMN;
          label = tableName + "." + fieldSchema.getName();
        }
        sources.add(getOrCreateVertex(srcVertexCache, label, type));
      }
    }
    return sources;
  }

  /**
   * Find a vertex from a cache, or create one if not.
   */
  private Vertex getOrCreateVertex(
      Map<String, Vertex> vertices, String label, Vertex.Type type) {
    Vertex vertex = vertices.get(label);
    if (vertex == null) {
      vertex = new Vertex(label, type);
      vertices.put(label, vertex);
    }
    return vertex;
  }

  /**
   * Find an edge that has the same type, expression, and sources.
   */
  private Edge findSimilarEdgeBySources(
      List<Edge> edges, Set<Vertex> sources, String expr, Edge.Type type) {
    for (Edge edge: edges) {
      if (edge.type == type && StringUtils.equals(edge.expr, expr)
          && SetUtils.isEqualSet(edge.sources, sources)) {
        return edge;
      }
    }
    return null;
  }

  /**
   * Find an edge that has the same type, expression, and targets.
   */
  private Edge findSimilarEdgeByTargets(
      List<Edge> edges, Set<Vertex> targets, String expr, Edge.Type type) {
    for (Edge edge: edges) {
      if (edge.type == type && StringUtils.equals(edge.expr, expr)
          && SetUtils.isEqualSet(edge.targets, targets)) {
        return edge;
      }
    }
    return null;
  }

  /**
   * Generate normalized name for a given target column.
   */
  private String getTargetFieldName(int fieldIndex,
      String destTableName, List<String> colNames, List<FieldSchema> fieldSchemas) {
    String fieldName = fieldSchemas.get(fieldIndex).getName();
    String[] parts = fieldName.split("\\.");
    if (destTableName != null) {
      String colName = parts[parts.length - 1];
      if (colNames != null && !colNames.contains(colName)) {
        colName = colNames.get(fieldIndex);
      }
      return destTableName + "." + colName;
    }
    if (parts.length == 2 && parts[0].startsWith("_u")) {
      return parts[1];
    }
    return fieldName;
  }

  /**
   * Get all the vertices of all edges. Targets at first,
   * then sources. Assign id to each vertex.
   */
  private Set<Vertex> getVertices(List<Edge> edges) {
    Set<Vertex> vertices = new LinkedHashSet<Vertex>();
    for (Edge edge: edges) {
      vertices.addAll(edge.targets);
    }
    for (Edge edge: edges) {
      vertices.addAll(edge.sources);
    }

    // Assign ids to all vertices,
    // targets at first, then sources.
    int id = 0;
    for (Vertex vertex: vertices) {
      vertex.id = id++;
    }
    return vertices;
  }

  /**
   * Write out an JSON array of edges.
   */
  private void writeEdges(JsonWriter writer, List<Edge> edges) throws IOException {
    writer.name("edges");
    writer.beginArray();
    for (Edge edge: edges) {
      writer.beginObject();
      writer.name("sources");
      writer.beginArray();
      for (Vertex vertex: edge.sources) {
        writer.value(vertex.id);
      }
      writer.endArray();
      writer.name("targets");
      writer.beginArray();
      for (Vertex vertex: edge.targets) {
        writer.value(vertex.id);
      }
      writer.endArray();
      if (edge.expr != null) {
        writer.name("expression").value(edge.expr);
      }
      writer.name("edgeType").value(edge.type.name());
      writer.endObject();
    }
    writer.endArray();
  }

  /**
   * Write out an JSON array of vertices.
   */
  private void writeVertices(JsonWriter writer, Set<Vertex> vertices) throws IOException {
    writer.name("vertices");
    writer.beginArray();
    for (Vertex vertex: vertices) {
      writer.beginObject();
      writer.name("id").value(vertex.id);
      writer.name("vertexType").value(vertex.type.name());
      writer.name("vertexId").value(vertex.label);
      writer.endObject();
    }
    writer.endArray();
  }

  /**
   * Generate query string md5 hash.
   */
  private String getQueryHash(String queryStr) {
    Hasher hasher = Hashing.md5().newHasher();
    hasher.putString(queryStr);
    return hasher.hash().toString();
  }
}
