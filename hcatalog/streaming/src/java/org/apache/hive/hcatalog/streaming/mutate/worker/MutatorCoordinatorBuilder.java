package org.apache.hive.hcatalog.streaming.mutate.worker;

import java.io.IOException;

import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.IMetaStoreClient;
import org.apache.hadoop.hive.metastore.api.MetaException;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hive.hcatalog.common.HCatUtil;
import org.apache.hive.hcatalog.streaming.mutate.HiveConfFactory;
import org.apache.hive.hcatalog.streaming.mutate.UgiMetaStoreClientFactory;
import org.apache.hive.hcatalog.streaming.mutate.client.AcidTable;

/** Convenience class for building {@link MutatorCoordinator} instances. */
public class MutatorCoordinatorBuilder {

  private HiveConf configuration;
  private MutatorFactory mutatorFactory;
  private UserGroupInformation authenticatedUser;
  private String metaStoreUri;
  private AcidTable table;
  private boolean deleteDeltaIfExists;

  public MutatorCoordinatorBuilder configuration(HiveConf configuration) {
    this.configuration = configuration;
    return this;
  }

  public MutatorCoordinatorBuilder authenticatedUser(UserGroupInformation authenticatedUser) {
    this.authenticatedUser = authenticatedUser;
    return this;
  }

  public MutatorCoordinatorBuilder metaStoreUri(String metaStoreUri) {
    this.metaStoreUri = metaStoreUri;
    return this;
  }

  /** Set the destination ACID table for this client. */
  public MutatorCoordinatorBuilder table(AcidTable table) {
    this.table = table;
    return this;
  }

  /**
   * If the delta file already exists, delete it. THis is useful in a MapReduce setting where a number of task retries
   * will attempt to write the same delta file.
   */
  public MutatorCoordinatorBuilder deleteDeltaIfExists() {
    this.deleteDeltaIfExists = true;
    return this;
  }

  public MutatorCoordinatorBuilder mutatorFactory(MutatorFactory mutatorFactory) {
    this.mutatorFactory = mutatorFactory;
    return this;
  }

  public MutatorCoordinator build() throws WorkerException, MetaException {
    String user = authenticatedUser == null ? System.getProperty("user.name") : authenticatedUser.getShortUserName();
    boolean secureMode = authenticatedUser == null ? false : authenticatedUser.hasKerberosCredentials();

    configuration = HiveConfFactory.newInstance(configuration, this.getClass(), metaStoreUri);

    IMetaStoreClient metaStoreClient;
    try {
      metaStoreClient = new UgiMetaStoreClientFactory(metaStoreUri, configuration, authenticatedUser, user, secureMode)
          .newInstance(HCatUtil.getHiveMetastoreClient(configuration));
    } catch (IOException e) {
      throw new WorkerException("Could not create meta store client.", e);
    }

    return new MutatorCoordinator(metaStoreClient, configuration, mutatorFactory, table, deleteDeltaIfExists);
  }

}
