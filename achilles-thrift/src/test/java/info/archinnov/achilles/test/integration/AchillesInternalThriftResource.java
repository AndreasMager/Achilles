/**
 *
 * Copyright (C) 2012-2013 DuyHai DOAN
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
package info.archinnov.achilles.test.integration;

import info.archinnov.achilles.consistency.ThriftConsistencyLevelPolicy;
import info.archinnov.achilles.dao.ThriftCounterDao;
import info.archinnov.achilles.dao.ThriftGenericEntityDao;
import info.archinnov.achilles.dao.ThriftGenericWideRowDao;
import info.archinnov.achilles.embedded.ThriftEmbeddedServer;
import info.archinnov.achilles.entity.manager.ThriftPersistenceManager;
import info.archinnov.achilles.entity.manager.ThriftPersistenceManagerFactory;

import java.awt.Composite;

public class AchillesInternalThriftResource extends AchillesTestResource {

	private static final String ACHILLES_ENTITY_PACKAGES = "info.archinnov.achilles.test.integration.entity";

	private final ThriftEmbeddedServer server;

	private final Cluster cluster;

	private final Keyspace keyspace;

	private final ThriftConsistencyLevelPolicy policy;

	private final ThriftPersistenceManagerFactory pmf;

	private final ThriftPersistenceManager manager;

	/**
	 * Initialize a new embedded Cassandra server
	 * 
	 * @param tables
	 *            list of tables to truncate before and after tests
	 */
	public AchillesInternalThriftResource(String... tables) {
		super(tables);
		final ImmutableMap<String, Object> config = ImmutableMap.<String, Object> of(CLEAN_CASSANDRA_DATA_FILES, true,
				ENTITY_PACKAGES, ACHILLES_ENTITY_PACKAGES, KEYSPACE_NAME, DEFAULT_ACHILLES_TEST_KEYSPACE_NAME,
				KEYSPACE_DURABLE_WRITE, false);

		server = new ThriftEmbeddedServer(config);
		cluster = server.getCluster();
		keyspace = server.getKeyspace();
		policy = server.getConsistencyPolicy();
		pmf = server.getPersistenceManagerFactory();
		manager = server.getPersistenceManager();
	}

	/**
	 * Initialize a new embedded Cassandra server
	 * 
	 * @param cleanUpSteps
	 *            when to truncate tables for clean up. Possible values are :
	 *            Steps.BEFORE_TEST, Steps.AFTER_TEST and Steps.BOTH (Default
	 *            value) <br/>
	 * <br/>
	 * @param tables
	 *            list of tables to truncate before, after or before and after
	 *            tests, depending on the 'cleanUpSteps' parameters
	 */
	public AchillesInternalThriftResource(Steps cleanUpSteps, String... tables) {
		super(cleanUpSteps, tables);
		final ImmutableMap<String, Object> config = ImmutableMap.<String, Object> of(CLEAN_CASSANDRA_DATA_FILES, true,
				ENTITY_PACKAGES, ACHILLES_ENTITY_PACKAGES, KEYSPACE_NAME, DEFAULT_ACHILLES_TEST_KEYSPACE_NAME,
				KEYSPACE_DURABLE_WRITE, false);

		server = new ThriftEmbeddedServer(config);
		cluster = server.getCluster();
		keyspace = server.getKeyspace();
		policy = server.getConsistencyPolicy();
		pmf = server.getPersistenceManagerFactory();
		manager = server.getPersistenceManager();
	}

	/**
	 * Return the native Hector cluster
	 * 
	 * @return native Hector cluster
	 */
	public Cluster getCluster() {
		return cluster;
	}

	/**
	 * Return the native Hector keyspace
	 * 
	 * @return native Hector keyspace
	 */
	public Keyspace getKeyspace() {
		return keyspace;
	}

	/**
	 * Return a singleton ThriftPersistenceManagerFactory
	 * 
	 * @return ThriftPersistenceManagerFactory singleton
	 */
	public ThriftPersistenceManagerFactory getPersistenceManagerFactory() {
		return pmf;
	}

	/**
	 * Return a singleton ThriftPersistenceManager
	 * 
	 * @return ThriftPersistenceManager singleton
	 */
	public ThriftPersistenceManager getPersistenceManager() {
		return manager;
	}

	/**
	 * Return a singleton ThriftConsistencyLevelPolicy
	 * 
	 * @return ThriftConsistencyLevelPolicy singleton
	 */
	public ThriftConsistencyLevelPolicy getConsistencyPolicy() {
		return policy;
	}

	@Override
	protected void truncateTables() {
		if (tables != null) {
			for (String table : tables) {
				cluster.truncate(keyspace.getKeyspaceName(), table);
			}
		}
	}

	public <K> ThriftGenericEntityDao getEntityDao(String columnFamily, Class<K> keyClass) {
		return new ThriftGenericEntityDao(cluster, keyspace, columnFamily, policy, Pair.create(keyClass, String.class));
	}

	public <K, V> ThriftGenericWideRowDao getColumnFamilyDao(String columnFamily, Class<K> keyClass, Class<V> valueClass) {

		return new ThriftGenericWideRowDao(cluster, keyspace, columnFamily, policy, Pair.create(keyClass, valueClass));
	}

	public ThriftCounterDao getCounterDao() {
		Pair<Class<Composite>, Class<Long>> rowkeyAndValueClasses = Pair.create(Composite.class, Long.class);
		return new ThriftCounterDao(cluster, keyspace, policy, rowkeyAndValueClasses);
	}

}
