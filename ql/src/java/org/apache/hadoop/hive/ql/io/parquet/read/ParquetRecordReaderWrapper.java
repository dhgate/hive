/**
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
package org.apache.hadoop.hive.ql.io.parquet.read;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.ql.exec.Utilities;
import org.apache.hadoop.hive.ql.io.IOConstants;
import org.apache.hadoop.hive.ql.io.parquet.FilterPredicateLeafBuilder;
import org.apache.hadoop.hive.ql.io.parquet.LeafFilterFactory;
import org.apache.hadoop.hive.ql.io.parquet.ProjectionPusher;
import org.apache.hadoop.hive.ql.io.sarg.ExpressionTree;
import org.apache.hadoop.hive.ql.io.sarg.PredicateLeaf;
import org.apache.hadoop.hive.ql.io.sarg.SearchArgument;
import org.apache.hadoop.hive.ql.io.sarg.SearchArgumentFactory;
import org.apache.hadoop.hive.ql.plan.TableScanDesc;
import org.apache.hadoop.hive.serde2.ColumnProjectionUtils;
import org.apache.hadoop.io.ArrayWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapred.FileSplit;
import org.apache.hadoop.mapred.InputSplit;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.RecordReader;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.TaskAttemptID;

import org.apache.parquet.filter2.compat.FilterCompat;
import org.apache.parquet.filter2.compat.RowGroupFilter;
import org.apache.parquet.filter2.predicate.FilterApi;
import org.apache.parquet.filter2.predicate.FilterPredicate;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.ParquetInputFormat;
import org.apache.parquet.hadoop.ParquetInputSplit;
import org.apache.parquet.hadoop.api.InitContext;
import org.apache.parquet.hadoop.api.ReadSupport.ReadContext;
import org.apache.parquet.hadoop.metadata.BlockMetaData;
import org.apache.parquet.hadoop.metadata.FileMetaData;
import org.apache.parquet.hadoop.metadata.ParquetMetadata;
import org.apache.parquet.hadoop.util.ContextUtil;
import org.apache.parquet.schema.MessageTypeParser;

import com.google.common.base.Strings;

public class ParquetRecordReaderWrapper  implements RecordReader<NullWritable, ArrayWritable> {
  public static final Log LOG = LogFactory.getLog(ParquetRecordReaderWrapper.class);

  private final long splitLen; // for getPos()

  private org.apache.hadoop.mapreduce.RecordReader<Void, ArrayWritable> realReader;
  // expect readReader return same Key & Value objects (common case)
  // this avoids extra serialization & deserialization of these objects
  private ArrayWritable valueObj = null;
  private boolean firstRecord = false;
  private boolean eof = false;
  private int schemaSize;
  private boolean skipTimestampConversion = false;
  private JobConf jobConf;
  private final ProjectionPusher projectionPusher;
  private List<BlockMetaData> filtedBlocks;

  public ParquetRecordReaderWrapper(
      final ParquetInputFormat<ArrayWritable> newInputFormat,
      final InputSplit oldSplit,
      final JobConf oldJobConf,
      final Reporter reporter)
          throws IOException, InterruptedException {
    this(newInputFormat, oldSplit, oldJobConf, reporter, new ProjectionPusher());
  }

  public ParquetRecordReaderWrapper(
      final ParquetInputFormat<ArrayWritable> newInputFormat,
      final InputSplit oldSplit,
      final JobConf oldJobConf,
      final Reporter reporter,
      final ProjectionPusher pusher)
          throws IOException, InterruptedException {
    this.splitLen = oldSplit.getLength();
    this.projectionPusher = pusher;

    jobConf = oldJobConf;
    final ParquetInputSplit split = getSplit(oldSplit, jobConf);

    TaskAttemptID taskAttemptID = TaskAttemptID.forName(jobConf.get(IOConstants.MAPRED_TASK_ID));
    if (taskAttemptID == null) {
      taskAttemptID = new TaskAttemptID();
    }

    // create a TaskInputOutputContext
    Configuration conf = jobConf;
    if (skipTimestampConversion ^ HiveConf.getBoolVar(
        conf, HiveConf.ConfVars.HIVE_PARQUET_TIMESTAMP_SKIP_CONVERSION)) {
      conf = new JobConf(oldJobConf);
      HiveConf.setBoolVar(conf,
        HiveConf.ConfVars.HIVE_PARQUET_TIMESTAMP_SKIP_CONVERSION, skipTimestampConversion);
    }

    final TaskAttemptContext taskContext = ContextUtil.newTaskAttemptContext(conf, taskAttemptID);
    if (split != null) {
      try {
        realReader = newInputFormat.createRecordReader(split, taskContext);
        realReader.initialize(split, taskContext);

        // read once to gain access to key and value objects
        if (realReader.nextKeyValue()) {
          firstRecord = true;
          valueObj = realReader.getCurrentValue();
        } else {
          eof = true;
        }
      } catch (final InterruptedException e) {
        throw new IOException(e);
      }
    } else {
      realReader = null;
      eof = true;
    }
    if (valueObj == null) { // Should initialize the value for createValue
      valueObj = new ArrayWritable(Writable.class, new Writable[schemaSize]);
    }
  }

  public FilterCompat.Filter setFilter(final JobConf conf) {
    String serializedPushdown = conf.get(TableScanDesc.FILTER_EXPR_CONF_STR);
    String columnNamesString =
      conf.get(ColumnProjectionUtils.READ_COLUMN_NAMES_CONF_STR);
    if (serializedPushdown == null || columnNamesString == null || serializedPushdown.isEmpty() ||
      columnNamesString.isEmpty()) {
      return null;
    }

    SearchArgument sarg =
        SearchArgumentFactory.create(Utilities.deserializeExpression
            (serializedPushdown));
    FilterPredicate p = toFilterPredicate(sarg);
    if (p != null) {
      LOG.debug("Predicate filter for parquet is " + p.toString());
      ParquetInputFormat.setFilterPredicate(conf, p);
      return FilterCompat.get(p);
    } else {
      LOG.debug("No predicate filter can be generated for " + TableScanDesc.FILTER_EXPR_CONF_STR +
        " with the value of " + serializedPushdown);
      return null;
    }
  }

  @Override
  public void close() throws IOException {
    if (realReader != null) {
      realReader.close();
    }
  }

  @Override
  public NullWritable createKey() {
    return null;
  }

  @Override
  public ArrayWritable createValue() {
    return valueObj;
  }

  @Override
  public long getPos() throws IOException {
    return (long) (splitLen * getProgress());
  }

  @Override
  public float getProgress() throws IOException {
    if (realReader == null) {
      return 1f;
    } else {
      try {
        return realReader.getProgress();
      } catch (final InterruptedException e) {
        throw new IOException(e);
      }
    }
  }

  @Override
  public boolean next(final NullWritable key, final ArrayWritable value) throws IOException {
    if (eof) {
      return false;
    }
    try {
      if (firstRecord) { // key & value are already read.
        firstRecord = false;
      } else if (!realReader.nextKeyValue()) {
        eof = true; // strictly not required, just for consistency
        return false;
      }

      final ArrayWritable tmpCurValue = realReader.getCurrentValue();
      if (value != tmpCurValue) {
        final Writable[] arrValue = value.get();
        final Writable[] arrCurrent = tmpCurValue.get();
        if (value != null && arrValue.length == arrCurrent.length) {
          System.arraycopy(arrCurrent, 0, arrValue, 0, arrCurrent.length);
        } else {
          if (arrValue.length != arrCurrent.length) {
            throw new IOException("DeprecatedParquetHiveInput : size of object differs. Value" +
              " size :  " + arrValue.length + ", Current Object size : " + arrCurrent.length);
          } else {
            throw new IOException("DeprecatedParquetHiveInput can not support RecordReaders that" +
              " don't return same key & value & value is null");
          }
        }
      }
      return true;
    } catch (final InterruptedException e) {
      throw new IOException(e);
    }
  }

  /**
   * gets a ParquetInputSplit corresponding to a split given by Hive
   *
   * @param oldSplit The split given by Hive
   * @param conf The JobConf of the Hive job
   * @return a ParquetInputSplit corresponding to the oldSplit
   * @throws IOException if the config cannot be enhanced or if the footer cannot be read from the file
   */
  @SuppressWarnings("deprecation")
  protected ParquetInputSplit getSplit(
      final InputSplit oldSplit,
      final JobConf conf
      ) throws IOException {
    ParquetInputSplit split;
    if (oldSplit instanceof FileSplit) {
      final Path finalPath = ((FileSplit) oldSplit).getPath();
      jobConf = projectionPusher.pushProjectionsAndFilters(conf, finalPath.getParent());
      FilterCompat.Filter filter = setFilter(jobConf);

      final ParquetMetadata parquetMetadata = ParquetFileReader.readFooter(jobConf, finalPath);
      final List<BlockMetaData> blocks = parquetMetadata.getBlocks();
      final FileMetaData fileMetaData = parquetMetadata.getFileMetaData();

      final ReadContext readContext = new DataWritableReadSupport().init(new InitContext(jobConf,
          null, fileMetaData.getSchema()));
      schemaSize = MessageTypeParser.parseMessageType(readContext.getReadSupportMetadata()
          .get(DataWritableReadSupport.HIVE_TABLE_AS_PARQUET_SCHEMA)).getFieldCount();
      final List<BlockMetaData> splitGroup = new ArrayList<BlockMetaData>();
      final long splitStart = ((FileSplit) oldSplit).getStart();
      final long splitLength = ((FileSplit) oldSplit).getLength();
      for (final BlockMetaData block : blocks) {
        final long firstDataPage = block.getColumns().get(0).getFirstDataPageOffset();
        if (firstDataPage >= splitStart && firstDataPage < splitStart + splitLength) {
          splitGroup.add(block);
        }
      }
      if (splitGroup.isEmpty()) {
        LOG.warn("Skipping split, could not find row group in: " + (FileSplit) oldSplit);
        return null;
      }

      if (filter != null) {
        filtedBlocks = RowGroupFilter.filterRowGroups(filter, splitGroup, fileMetaData.getSchema());
        if (filtedBlocks.isEmpty()) {
          LOG.debug("All row groups are dropped due to filter predicates");
          return null;
        }

        long droppedBlocks = splitGroup.size() - filtedBlocks.size();
        if (droppedBlocks > 0) {
          LOG.debug("Dropping " + droppedBlocks + " row groups that do not pass filter predicate");
        }
      } else {
        filtedBlocks = splitGroup;
      }

      if (HiveConf.getBoolVar(conf, HiveConf.ConfVars.HIVE_PARQUET_TIMESTAMP_SKIP_CONVERSION)) {
        skipTimestampConversion = !Strings.nullToEmpty(fileMetaData.getCreatedBy()).startsWith("parquet-mr");
      }
      split = new ParquetInputSplit(finalPath,
          splitStart,
          splitLength,
          ((FileSplit) oldSplit).getLocations(),
          filtedBlocks,
          readContext.getRequestedSchema().toString(),
          fileMetaData.getSchema().toString(),
          fileMetaData.getKeyValueMetaData(),
          readContext.getReadSupportMetadata());
      return split;
    } else {
      throw new IllegalArgumentException("Unknown split type: " + oldSplit);
    }
  }

  public List<BlockMetaData> getFiltedBlocks() {
    return filtedBlocks;
  }

  /**
   * Translate the search argument to the filter predicate parquet used
   * @return translate the sarg into a filter predicate
   */
  public static FilterPredicate toFilterPredicate(SearchArgument sarg) {
    return translate(sarg.getExpression(),
        sarg.getLeaves());
  }

  private static boolean isMultiLiteralsOperator(PredicateLeaf.Operator op) {
    return (op == PredicateLeaf.Operator.IN) ||
        (op == PredicateLeaf.Operator.BETWEEN);
  }

  private static FilterPredicate translate(ExpressionTree root,
                                           List<PredicateLeaf> leafs){
    FilterPredicate p = null;
    switch (root.getOperator()) {
      case OR:
        for(ExpressionTree child: root.getChildren()) {
          if (p == null) {
            p = translate(child, leafs);
          } else {
            FilterPredicate right = translate(child, leafs);
            // constant means no filter, ignore it when it is null
            if(right != null){
              p = FilterApi.or(p, right);
            }
          }
        }
        return p;
      case AND:
        for(ExpressionTree child: root.getChildren()) {
          if (p == null) {
            p = translate(child, leafs);
          } else {
            FilterPredicate right = translate(child, leafs);
            // constant means no filter, ignore it when it is null
            if(right != null){
              p = FilterApi.and(p, right);
            }
          }
        }
        return p;
      case NOT:
        FilterPredicate op = translate(root.getChildren().get(0), leafs);
        if (op != null) {
          return FilterApi.not(op);
        } else {
          return null;
        }
      case LEAF:
        return buildFilterPredicateFromPredicateLeaf(leafs.get(root.getLeaf()));
      case CONSTANT:
        return null;// no filter will be executed for constant
      default:
        throw new IllegalStateException("Unknown operator: " +
            root.getOperator());
    }
  }

  private static FilterPredicate buildFilterPredicateFromPredicateLeaf
          (PredicateLeaf leaf) {
    LeafFilterFactory leafFilterFactory = new LeafFilterFactory();
    FilterPredicateLeafBuilder builder;
    try {
      builder = leafFilterFactory
          .getLeafFilterBuilderByType(leaf.getType());
      if (builder == null) {
        return null;
      }
      if (isMultiLiteralsOperator(leaf.getOperator())) {
        return builder.buildPredicate(leaf.getOperator(),
            leaf.getLiteralList(),
            leaf.getColumnName());
      } else {
        return builder
            .buildPredict(leaf.getOperator(),
                leaf.getLiteral(),
                leaf.getColumnName());
      }
    } catch (Exception e) {
      LOG.error("fail to build predicate filter leaf with errors" + e, e);
      return null;
    }
  }


}
