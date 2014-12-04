/*
 * Copyright (C) 2012-2014 DuyHai DOAN
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package info.archinnov.achilles.persistence;

import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import info.archinnov.achilles.async.AchillesFuture;
import info.archinnov.achilles.exception.AchillesStaleObjectStateException;
import info.archinnov.achilles.internal.context.ConfigurationContext;
import info.archinnov.achilles.internal.context.DaoContext;
import info.archinnov.achilles.internal.context.PersistenceContextFactory;
import info.archinnov.achilles.internal.metadata.holder.EntityMeta;
import info.archinnov.achilles.internal.validation.Validator;
import info.archinnov.achilles.query.cql.AsyncNativeQuery;
import info.archinnov.achilles.query.slice.AsyncSliceQueryBuilder;
import info.archinnov.achilles.query.typed.AsyncTypedQuery;
import info.archinnov.achilles.type.IndexCondition;
import info.archinnov.achilles.type.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static info.archinnov.achilles.internal.metadata.holder.EntityMeta.EntityState.MANAGED;
import static info.archinnov.achilles.internal.metadata.holder.EntityMeta.EntityState.NOT_MANAGED;
import static info.archinnov.achilles.type.OptionsBuilder.noOptions;

/**
 * <p>
 * <strong>Stateless</strong> object to manage <strong>asynchronous</strong> entity persistence.
 * This class is totally <strong></strong>thread-safe</strong> and can be shared by many threads.
 * You should normally have only one instance of PersistenceMananger across the application
 *
 * <br/>
 * An AsyncMananger is very cheap to create from an {@link PersistenceManagerFactory}
 * </p>
 *
 * <p>
 *  <h3>I Persist asynchronously transient entity</h3>
 *  <pre class="code"><code class="java">
 *      // Insert
 *      AchillesFuture<MyEntity> managedEntityFuture = asyncManager.insert(myEntity);
 *  </code></pre>
 *
 *  <h3>II Update asynchronously for modifications</h3>
 *  <pre class="code"><code class="java">
 *      User managedUser = asyncManager.find(User.class,1L).get();
 *      user.setFirstname("DuyHai");
 *
 *      AchillesFuture<User> userFuture = asyncManager.update(user);
 *  </code></pre>
 *
 *  <h3>III Removing asynchronously entities</h3>
 *  <pre class="code"><code class="java">
 *      // Simple removed
 *      User managedUser = asyncManager.find(User.class,1L).get();
 *      AchillesFuture<User> userFuture = asyncManager.remove(managedUser);
 *
 *      // Direct remove without read-before-write
 *      AchillesFuture<Empty> emptyFuture = asyncManager.removeById(User.class,1L);
 *  </code></pre>
 *
 *  <h3>IV Loading entities asynchronously</h3>
 *  <pre class="code"><code class="java">
 *      // Read data from Cassandra
 *      AchillesFuture<User> managedUserFuture = asyncManager.find(User.class,1L);
 *  </code></pre>
 *
 *  <h3>V Direct update</h3>
 *  Please note that proxy creation always return immediately since we do not hit the database
 *  <pre class="code"><code class="java">
 *      // No data read from Cassandra
 *      User managedUserProxy = asyncManager.forUpdate(User.class,1L).get();
 *      managedUser.setAge(30);
 *
 *      // Direct update, no read from Cassandra has been done
 *      AchillesFuture<User> userFuture = asyncManager.update(managedUser);
 *  </code></pre>
 *
 *  <h3>VI Reloading state asynchronously for managed entities</h3>
 *  <pre class="code"><code class="java">
 *      // Read data from Cassandra
 *      User managedUser = asyncManager.find(User.class,1L).get();
 *      ...
 *      // Perform some logic
 *
 *      // Reload data from Cassandra into the managed entity
 *      AchillesFuture<User> userFuture = asyncManager.refresh(managedUser);
 *  </code></pre>
 *
 *  <h3>VII Removing proxy from managed entities</h3>
 *  <pre class="code"><code class="java">
 *      // Create managed entity
 *      User managedUser = asyncManager.find(User.class,1L);
 *      ...
 *      // Perform some logic
 *
 *      // Removing proxy before passing it to client via serialization
 *      User transientUser = asyncManager.removeProxy(managedUser);
 *  </code></pre>
 *
 *  <h3>VIII Accessing native Session object</h3>
 *  <pre class="code"><code class="java">
 *      Session session = asyncManager.getNativeSession();
 *      ...
 *
 *      // Issue simple CQL queries
 *      session.execute("UPDATE users SET age=:age WHERE id=:id",30,10);
 *  </code></pre>
 *
 *  <h3>IX JSON serialization/deserialization</h3>
 *  <pre class="code"><code class="java">
 *      // Serialize an object to JSON using the registered or default object mapper
 *      String json = asyncManager.serializeToJSON(myModel);
 *      ...
 *
 *      // Deserialize a JSON string into an object  the registered or default object mapper
 *      MyModel myModel = asyncManager.deserializeFromJSON(json);
 *  </code></pre>
 *
 *  <h3>X Initializing all counter fields</h3>
 *  <pre class="code"><code class="java">
 *      // Create managed entity
 *      AchillesFuture@lt;User&gt; futureUser = asyncManager.find(User.class,1L);
 *      ...
 *      // Perform some logic
 *      ...
 *
 *      // Load all lazy counter fields
 *      asyncManager.initialize(futureUser.get());
 *  </code></pre>
 * </p>
 *
 *
 * @see <a href="https://github.com/doanduyhai/Achilles/wiki/Persistence-Manager-Operations" target="_blank">Persistence Manager operations</a>
 */
public class AsyncManager extends CommonAsyncManager {
    private static final Logger log = LoggerFactory.getLogger(AsyncManager.class);

    protected AsyncManager(Map<Class<?>, EntityMeta> entityMetaMap, PersistenceContextFactory contextFactory, DaoContext daoContext, ConfigurationContext configContext) {
       super(entityMetaMap, contextFactory, daoContext, configContext);
    }


    /**
     * Find an entity, asynchronously.
     *
     * @param entityClass
     *            Entity type
     * @param primaryKey
     *            Primary key (Cassandra row key) of the entity to load
     *
     * @return AchillesFuture&lt;T&gt; future managed entity
     */
    public <T> AchillesFuture<T> find(Class<T> entityClass, Object primaryKey) {
        log.debug("Find entity class '{}' with primary key '{}'", entityClass, primaryKey);
        return super.asyncFind(entityClass, primaryKey, noOptions());
    }


    /**
     * Find an entity with the given Consistency Level for read, asynchronously
     *
     * @param entityClass
     *            Entity type
     * @param primaryKey
     *            Primary key (Cassandra row key) of the entity to load
     * @param options
     *            Options
     *
     * @return AchillesFuture&lt;T&gt; future managed entity
     */
    public <T> AchillesFuture<T> find(final Class<T> entityClass, final Object primaryKey, Options options) {
        log.debug("Find entity class '{}' with primary key '{}' and options '{}'", entityClass, primaryKey, options);
        return super.asyncFind(entityClass, primaryKey, options);
    }

    /**
     * Create a proxy for the entity update. An new empty entity will be created,
     * populated with the provided primary key and then proxified. This method
     * never returns null. Use this method to perform direct update without
     * read-before-write
     *
     *  <pre class="code"><code class="java">
     *      // No data read from Cassandra
     *      User managedUser = manager.forUpdate(User.class,1L);
     *      managedUser.setAge(33);
     *      manager.update(managedUser);
     *  </code></pre>
     *
     * @param entityClass
     *            Entity type
     * @param primaryKey
     *            Primary key (Cassandra row key) of the entity to initialize
     *
     * @return T proxy
     */
    public <T> T forUpdate(Class<T> entityClass, Object primaryKey) {
        log.debug("Get reference for entity class '{}' with primary key {}", entityClass, primaryKey);
        return super.getProxyForUpdateInternal(entityClass, primaryKey);
    }

    /**
     * Refresh an entity asynchronously.
     *
     * @param entity
     *            Entity to be refreshed
     *
     * @return AchillesFuture&lt;T&gt; future managed entity
     */
    public <T> AchillesFuture<T> refresh(T entity) throws AchillesStaleObjectStateException {
        log.debug("Refreshing entity '{}' asynchronously", proxifier.removeProxy(entity));
        return super.asyncRefresh(entity, noOptions());
    }

    /**
     * Refresh an entity with the given Consistency Level for read, asynchronously.
     *
     * @param entity
     *            Entity to be refreshed
     * @param options
     *            Options
     *
     * @return AchillesFuture&lt;T&gt; future managed entity
     */
    public <T> AchillesFuture<T> refresh(final T entity, Options options) throws AchillesStaleObjectStateException {
        log.debug("Refreshing entity '{}' asynchronously with options '{}'", proxifier.removeProxy(entity), options);
        return super.asyncRefresh(entity, options);
    }

    /**
     * Initialize all lazy fields of a set of 'managed' entities
     *
     *  <pre class="code"><code class="java">
     *      // Create a managed entity
     *      User userProxy = manager.find(User.class,1L);
     *      ...
     *      // Perform some logic
     *
     *      // Initialize all fields not yet loaded into the managed entity, including counter fields
     *      manager.initialize(userProxy);
     *  </code></pre>
     *
     * Raise an IllegalStateException if an entity is not 'managed'
     *
     */
    public <T> T initialize(final T entity) {
        log.debug("Force lazy fields initialization for entity {}", proxifier.removeProxy(entity));
        return super.initialize(entity);
    }

    /**
     * Initialize all counter fields of a list of 'managed' entities
     *
     *  <pre class="code"><code class="java">
     *      // Create managed entities
     *      User user1 = manager.find(User.class,1L);
     *      User user2 = manager.find(User.class,2L);
     *      ...
     *      // Perform some logic
     *      ...
     *
     *      // Initialize all counter fields not yet loaded into the managed entity
     *      manager.initialize(Sets.newHashSet(user1, user2));
     *  </code></pre>
     *
     * Raise an IllegalStateException if an entity is not 'managed'
     *
     */
    public <T> Set<T> initialize(final Set<T> entities) {
        return super.initialize(entities);
    }

    /**
     * Initialize all counter fields of a list of 'managed' entities
     *
     *  <pre class="code"><code class="java">
     *      // Create managed entities
     *      User user1 = manager.find(User.class,1L);
     *      User user2 = manager.find(User.class,2L);
     *      ...
     *      // Perform some logic
     *      ...
     *
     *      // Initialize all counter fields not yet loaded into the managed entity
     *      manager.initialize(Arrays.asList(user1, user2));
     *  </code></pre>
     *
     * Raise an IllegalStateException if an entity is not 'managed'
     *
     */
    public <T> List<T> initialize(final List<T> entities) {
        log.debug("Force lazy fields initialization for entity set {}", entities);
        return super.initialize(entities);
    }

    /**
     * Shorthand for manager.removeProxy(manager.initialize(T entity))
     *
     */
    public <T> T initAndRemoveProxy(T entity) {
        return super.removeProxy(super.initialize(entity));
    }

    /**
     * Shorthand for manager.removeProxy(manager.initialize(Set<T> entities))
     *
     */
    public <T> Set<T> initAndRemoveProxy(Set<T> entities) {
        return super.removeProxy(super.initialize(entities));
    }

    /**
     * Shorthand for manager.removeProxy(manager.initialize(List<T> entities))
     *
     */
    public <T> List<T> initAndRemoveProxy(List<T> entities) {
        return super.removeProxy(super.initialize(entities));
    }

    /**
     * Remove the proxy of a 'managed' entity and return the underlying "raw"
     * entity
     * <br/>
     * <br/>
     * If the argument is not a proxy object, return itself <br/>
     * Else, return the target object behind the proxy
     *
     *  <pre class="code"><code class="java">
     *      // Create managed entity
     *      User managedUser = manager.find(User.class,1L);
     *      ...
     *      // Perform some logic
     *
     *      // Removing proxy before passing it to client via serialization
     *      User transientUser = manager.removeProxy(managedUser);
     *  </code></pre>
     *
     * @param proxy
     * @return real object
     */
    public <T> T removeProxy(T proxy) {
        return super.removeProxy(proxy);
    }

    /**
     * Remove the proxy of a list of 'managed' entities and return the
     * underlying "raw" entities
     *
     * See {@link #removeProxy}
     *
     * @param proxies
     *            list of proxified entity
     * @return real object list
     */
    public <T> List<T> removeProxy(List<T> proxies) {
        log.debug("Removing proxy for a list of entities {}", proxies);
        return super.removeProxy(proxies);
    }

    /**
     * Remove the proxy of a set of 'managed' entities return the underlying
     * "raw" entities
     *
     * See {@link #removeProxy}
     *
     * @param proxies
     *            set of proxified entities
     * @return real object set
     */
    public <T> Set<T> removeProxy(Set<T> proxies) {
        log.debug("Removing proxy for a set of entities {}", proxies);
        return super.removeProxy(proxies);
    }


    /**
     * Create a builder to start an asynchronous slice query DSL. The provided entity class <strong>must</strong> be:
     *
     * <ul>
     *     <li>a entity type managed by <strong>Achilles</strong></li>
     *     <li>a clustered entity, slicing is irrelevant for non-clustered entity</li>
     * </ul>
     *
     * @see <a href="https://github.com/doanduyhai/Achilles/wiki/Queries#slice-query" target="_blank">Slice query API</a>
     *
     * @param entityClass type of the clustered entity
     * @param <T>: type of the clustered entity
     * @return AsyncSliceQueryBuilder&lt;T&gt;
     */
    public <T> AsyncSliceQueryBuilder<T> sliceQuery(Class<T> entityClass) {
        log.debug("Execute slice query for entity class {}", entityClass);
        final EntityMeta meta = super.validateSliceQueryInternal(entityClass);
        return new AsyncSliceQueryBuilder<>(sliceQueryExecutor, entityClass, meta);
    }

    /**
     * Return a CQL native query DSL
     *
     * <br/>
     * <br/>
     *
     *  <h3>Native query without bound values</h3>
     *  <pre class="code"><code class="java">
     *      RegularStatement nativeQuery = select("name",age_in_years").from("UserEntity").where(in("id",Arrays.asList(10,11))).limit(20);
     *      AchillesFuture&lt;List&lt;TypedMap&gt;&gt; actual = asyncManager.nativeQuery(nativeQuery).get();
     *  </code></pre>
     *
     *  <br/>
     *  <br/>
     *
     *  <h3>Native query with bound values</h3>
     *  <pre class="code"><code class="java">
     *      RegularStatement nativeQuery = select("name",age_in_years").from("UserEntity").where(in("id",bindMarker())).limit(bindMarker());
     *      AchillesFuture&lt;List&lt;TypedMap&gt;&gt; actual = asyncManager.nativeQuery(nativeQuery,Arrays.asList(10,11),20).get();
     *  </code></pre>
     *
     * @see <a href="https://github.com/doanduyhai/Achilles/wiki/Queries#native-query" target="_blank">Native query API</a>
     *
     * @param statement
     *            native CQL regularStatement, including limit, ttl and consistency
     *            options
     *
     * @param boundValues
     *            values to be bind to the parameterized query, if any
     *
     * @return AsyncNativeQuery
     */
    public AsyncNativeQuery nativeQuery(Statement statement, Object... boundValues) {
        log.debug("Execute native query {}", statement);
        Validator.validateNotNull(statement, "The statement for native query should not be null");
        return new AsyncNativeQuery(daoContext, configContext, statement, noOptions(), boundValues);
    }

    /**
     * Return an asynchronous CQL native query
     *
     * <br/>
     * <br/>
     *
     *  <h3>Native query without bound values</h3>
     *  <pre class="code"><code class="java">
     *      RegularStatement nativeQuery = select("name",age_in_years").from("UserEntity").where(in("id",Arrays.asList(10,11))).limit(20);
     *      AchillesFuture&lt;List&lt;TypedMap&gt;&gt; actual = asyncManager.nativeQuery(nativeQuery).get();
     *  </code></pre>
     *
     *  <br/>
     *  <br/>
     *
     *  <h3>Native query with bound values</h3>
     *  <pre class="code"><code class="java">
     *      RegularStatement nativeQuery = select("name",age_in_years").from("UserEntity").where(in("id",bindMarker())).limit(bindMarker());
     *      AchillesFuture&lt;List&lt;TypedMap&gt;&gt; actual = asyncManager.nativeQuery(nativeQuery,Arrays.asList(10,11),20).get();
     *  </code></pre>
     *
     * @see <a href="https://github.com/doanduyhai/Achilles/wiki/Queries#native-query" target="_blank">Native query API</a>
     *
     * @param statement
     *            native CQL regularStatement, including limit, ttl and consistency
     *            options
     *
     * @param options
     *            options for the query. <strong>Only LWT Result listener passed as option is taken
     *            into account</strong>. For timestamp, TTL and LWT conditions you must specify them
     *            directly in the query string
     *
     * @param boundValues
     *            values to be bind to the parameterized query, if any
     *
     * @return AsyncNativeQuery
     */
    public AsyncNativeQuery nativeQuery(Statement statement, Options options, Object... boundValues) {
        log.debug("Execute native query {}", statement);
        Validator.validateNotNull(statement, "The statement for native query should not be null");
        return new AsyncNativeQuery(daoContext, configContext, statement, options, boundValues);
    }

    /**
     * Return an asynchronous CQL typed query
     *
     * All found entities will be in <strong>managed</strong> state
     *
     * <br/>
     * <br/>
     *
     *  <h3>Typed query without bound values</h3>
     *  <pre class="code"><code class="java">
     *      RegularStatement nativeQuery = select().from("MyEntity").where().limit(3);
     *      AchillesFuture&lt;List&lt;MyEntity&gt;&gt; actual = asyncManager.typedQuery(MyEntity.class, nativeQuery).get();
     *  </code></pre>
     *
     *  <br/>
     *  <br/>
     *
     *  <h3>Typed query with bound values</h3>
     *  <pre class="code"><code class="java">
     *      RegularStatement statement = select().from("MyEntity").limit(bindMarker());
     *      AchillesFuture&lt;List&lt;MyEntity&gt;&gt; actual = asyncManager.typedQuery(MyEntity.class, statement,3).get();
     *  </code></pre>
     *
     * @see <a href="https://github.com/doanduyhai/Achilles/wiki/Queries#typed-query" target="_blank">Typed query API</a>
     *
     * @param entityClass
     *            type of entity to be returned
     *
     * @param statement
     *            native CQL regularStatement, including limit, ttl and consistency
     *            options
     *
     * @param boundValues
     *            values to be bind to the parameterized query, if any
     *
     * @return AsyncTypedQuery&lt;T&gt;
     */
    public <T> AsyncTypedQuery<T> typedQuery(Class<T> entityClass, Statement statement, Object... boundValues) {
        final EntityMeta meta = super.typedQueryInternal(entityClass, statement,boundValues);
        return new AsyncTypedQuery<>(entityClass, daoContext, configContext, statement, meta, contextFactory, MANAGED, boundValues);
    }

    /**
     * Return an asynchronous CQL indexed query
     *
     * All found entities will be in 'managed' state
     *
     * @param entityClass
     *            type of entity to be returned
     *
     * @param indexCondition
     *            index condition
     *
     * @return AsyncTypedQuery&lt;T&gt;
     */
    public <T> AsyncTypedQuery<T> indexedQuery(Class<T> entityClass, IndexCondition indexCondition) {
        log.debug("Execute indexed query for entity class {}", entityClass);
        final Statement statement = super.indexedQueryInternal(entityClass, indexCondition);
        final EntityMeta meta = super.typedQueryInternal(entityClass, statement, indexCondition.getColumnValue());
        return new AsyncTypedQuery<>(entityClass, daoContext, configContext, statement, meta, contextFactory, MANAGED, new Object[]{indexCondition.getColumnValue()});
    }

    /**
     * Return an asynchronous CQL typed query
     *
     * All found entities will be returned as raw entities and not 'managed' by
     * Achilles
     *
     * <br/>
     * <br/>
     *
     *  <h3>Raw typed query without bound values</h3>
     *  <pre class="code"><code class="java">
     *      RegularStatement nativeQuery = select().from("MyEntity").where().limit(3);
     *      AchillesFuture&lt;List&lt;MyEntity&gt;&gt; actual = asyncManager.rawTypedQuery(MyEntity.class, nativeQuery).get();
     *  </code></pre>
     *
     *  <br/>
     *  <br/>
     *
     *  <h3>Raw typed query with bound values</h3>
     *  <pre class="code"><code class="java">
     *      RegularStatement nativeQuery = select().from("MyEntity").where().limit(bindMarker());
     *      AchillesFuture&lt;List&lt;MyEntity&gt;&gt; actual = asyncManager.rawTypedQuery(MyEntity.class, nativeQuery,3).get();
     *  </code></pre>
     *
     * @see <a href="https://github.com/doanduyhai/Achilles/wiki/Queries#typed-query" target="_blank">Typed query API</a>
     *
     * @param entityClass
     *            type of entity to be returned
     *
     * @param statement
     *            native CQL regularStatement, including limit, ttl and consistency
     *            options
     *
     * @param boundValues
     *            values to be bind to the parameterized query, if any
     *
     * @return AsyncTypedQuery&lt;T&gt;
     */
    public <T> AsyncTypedQuery<T> rawTypedQuery(Class<T> entityClass, Statement statement, Object... boundValues) {
        log.debug("Execute raw typed query for entity class {}", entityClass);
        final EntityMeta meta = super.rawTypedQueryInternal(entityClass, statement, boundValues);
        return new AsyncTypedQuery<>(entityClass, daoContext, configContext, statement, meta, contextFactory, NOT_MANAGED, boundValues);
    }

    /**
     * Serialize the entity in JSON using a registered Object Mapper or default Achilles Object Mapper
     * @param entity
     * @return serialized entity in JSON
     * @throws java.io.IOException
     */
    public String serializeToJSON(Object entity) throws IOException {
        return super.serializeToJSON(entity);
    }

    /**
     * Deserialize the given JSON into entity using a registered Object Mapper or default Achilles Object Mapper
     * @param type
     * @param serialized
     * @param <T>
     * @return deserialized entity from JSON
     * @throws java.io.IOException
     */
    public <T> T deserializeFromJSON(Class<T> type, String serialized) throws IOException {
        return super.deserializeFromJSON(type, serialized);
    }

    /**
     * Return Session object from Java Driver
     * @return Session
     */
    public Session getNativeSession() {
        return super.getNativeSession();
    }

    /**
     * Create a new state-full asynchronous Batch <br/>
     * <br/>
     * <p/>
     * <strong>WARNING : This Batch is state-full and not
     * thread-safe. In case of exception, you MUST not re-use it but create
     * another one</strong>
     *
     * @return a new state-full AsyncBatch
     */
    public AsyncBatch createBatch() {
        log.debug("Spawn new AsyncBatch");
        return new AsyncBatch(entityMetaMap, contextFactory, daoContext, configContext, false);
    }


    /**
     * Create a new state-full asynchronous <strong>ordered</strong> Batch <br/>
     * <br/>
     * <p>
     * This Batch respect insertion order by generating increasing timestamp with micro second resolution.
     * If you use ordered Batch in multiple clients, do not forget to synchronize the clock between those clients
     * to avoid statements interleaving
     * </p>
     * <strong>WARNING : This Batch is state-full and not
     * thread-safe. In case of exception, you MUST not re-use it but create
     * another one</strong>
     *
     * @return a new state-full AsyncBatch
     */
    public AsyncBatch createOrderedBatch() {
        log.debug("Spawn new AsyncBatch");
        return new AsyncBatch(entityMetaMap, contextFactory, daoContext, configContext, true);
    }
}
