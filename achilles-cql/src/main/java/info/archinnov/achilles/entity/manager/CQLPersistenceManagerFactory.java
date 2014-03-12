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
package info.archinnov.achilles.entity.manager;

import static info.archinnov.achilles.configuration.CQLConfigurationParameters.KEYSPACE_NAME_PARAM;
import static info.archinnov.achilles.configuration.ConfigurationParameters.ENTITY_PACKAGES_PARAM;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import info.archinnov.achilles.configuration.ArgumentExtractor;
import info.archinnov.achilles.configuration.CQLArgumentExtractor;
import info.archinnov.achilles.consistency.AchillesConsistencyLevelPolicy;
import info.archinnov.achilles.consistency.CQLConsistencyLevelPolicy;
import info.archinnov.achilles.context.CQLDaoContext;
import info.archinnov.achilles.context.CQLDaoContextBuilder;
import info.archinnov.achilles.context.CQLPersistenceContextFactory;
import info.archinnov.achilles.context.ConfigurationContext.Impl;
import info.archinnov.achilles.proxy.ProxyClassFactory;
import info.archinnov.achilles.table.CQLTableCreator;
import info.archinnov.achilles.type.ConsistencyLevel;

public class CQLPersistenceManagerFactory extends PersistenceManagerFactory {
	private static final Logger log = LoggerFactory.getLogger(CQLPersistenceManagerFactory.class);

	private Cluster cluster;

	private Session session;

	private CQLDaoContext daoContext;

	private CQLPersistenceContextFactory contextFactory;

	private ProxyClassFactory proxyClassFactory = new ProxyClassFactory();

	/**
	 * Create a new CQLPersistenceManagerFactory with a configuration map
	 *
	 * @param configurationMap Check documentation for more details on configuration
	 *                         parameters
	 */
	public CQLPersistenceManagerFactory(Map<String, Object> configurationMap) {
		super(configurationMap, new CQLArgumentExtractor());
		configContext.setImpl(Impl.CQL);

		CQLArgumentExtractor extractor = new CQLArgumentExtractor();
		cluster = extractor.initCluster(configurationMap);
		session = extractor.initSession(cluster, configurationMap);

		boolean hasSimpleCounter = false;
		if (StringUtils.isNotBlank((String) configurationMap.get(ENTITY_PACKAGES_PARAM))) {
			hasSimpleCounter = bootstrap();
		}

		new CQLTableCreator(cluster, session, (String) configurationMap.get(KEYSPACE_NAME_PARAM))
				.validateOrCreateTables(entityMetaMap, configContext, hasSimpleCounter);

		daoContext = CQLDaoContextBuilder.builder(session).build(entityMetaMap, hasSimpleCounter);
		contextFactory = new CQLPersistenceContextFactory(daoContext, configContext, entityMetaMap);
		registerShutdownHook(cluster);

		warmUpProxies(extractor, configurationMap);
	}

	private void warmUpProxies(CQLArgumentExtractor extractor, Map<String, Object> configurationMap) {
		if (extractor.initProxyWarmUp(configurationMap)) {
			long start = System.nanoTime();
			for (Class<?> clazz : entityMetaMap.keySet()) {
				proxyClassFactory.createProxyClass(clazz);
			}
			long end = System.nanoTime();
			long duration = (end - start) / 1000000;
			log.info("Entity proxies warm up took {} millisecs for {} entities", duration, entityMetaMap.size());
		}
	}

	/**
	 * Create a new CQLPersistenceManager. This instance of
	 * CQLPersistenceManager is <strong>thread-safe</strong>
	 *
	 * @return CQLPersistenceManager
	 */
	public CQLPersistenceManager createPersistenceManager() {
		return new CQLPersistenceManager(entityMetaMap, contextFactory, daoContext, configContext);
	}

	/**
	 * Create a new state-full PersistenceManager for batch handling <br/>
	 * <br/>
	 * <p/>
	 * <strong>WARNING : This PersistenceManager is state-full and not
	 * thread-safe. In case of exception, you MUST not re-use it but create
	 * another one</strong>
	 *
	 * @return a new state-full PersistenceManager
	 */
	public CQLBatchingPersistenceManager createBatchingPersistenceManager() {
		return new CQLBatchingPersistenceManager(entityMetaMap, contextFactory, daoContext, configContext);
	}

	@Override
	protected AchillesConsistencyLevelPolicy initConsistencyLevelPolicy(Map<String, Object> configurationMap,
	                                                                    ArgumentExtractor argumentExtractor) {
		log.info("Initializing new Achilles Configurable Consistency Level Policy from arguments {}",
		         configurationMap);

		ConsistencyLevel defaultReadConsistencyLevel = argumentExtractor
				.initDefaultReadConsistencyLevel(configurationMap);
		ConsistencyLevel defaultWriteConsistencyLevel = argumentExtractor
				.initDefaultWriteConsistencyLevel(configurationMap);
		Map<String, ConsistencyLevel> readConsistencyMap = argumentExtractor.initReadConsistencyMap(configurationMap);
		Map<String, ConsistencyLevel> writeConsistencyMap = argumentExtractor.initWriteConsistencyMap
				(configurationMap);

		return new CQLConsistencyLevelPolicy(defaultReadConsistencyLevel, defaultWriteConsistencyLevel,
		                                     readConsistencyMap, writeConsistencyMap);
	}

	private void registerShutdownHook(final Cluster cluster) {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				cluster.shutdown();
			}
		});
	}

	void setProxyClassFactory(ProxyClassFactory proxyClassFactory) {
		this.proxyClassFactory = proxyClassFactory;
	}
}
