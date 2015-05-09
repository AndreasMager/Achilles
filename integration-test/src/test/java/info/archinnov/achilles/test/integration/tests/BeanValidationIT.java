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
package info.archinnov.achilles.test.integration.tests;

import static info.archinnov.achilles.configuration.ConfigurationParameters.BEAN_VALIDATION_ENABLE;
import static info.archinnov.achilles.configuration.ConfigurationParameters.ENTITIES_LIST;
import static info.archinnov.achilles.configuration.ConfigurationParameters.EVENT_INTERCEPTORS;
import static info.archinnov.achilles.configuration.ConfigurationParameters.KEYSPACE_NAME;
import static info.archinnov.achilles.configuration.ConfigurationParameters.NATIVE_SESSION;
import static org.fest.assertions.api.Assertions.assertThat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import info.archinnov.achilles.test.integration.entity.CompleteBean;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Rule;
import org.junit.Test;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import info.archinnov.achilles.configuration.ConfigurationParameters;
import info.archinnov.achilles.exception.AchillesBeanValidationException;
import info.archinnov.achilles.persistence.PersistenceManager;
import info.archinnov.achilles.persistence.PersistenceManagerFactory;
import info.archinnov.achilles.persistence.PersistenceManagerFactory.PersistenceManagerFactoryBuilder;
import info.archinnov.achilles.test.integration.AchillesInternalCQLResource;
import info.archinnov.achilles.test.integration.entity.EntityWithClassLevelConstraint;
import info.archinnov.achilles.test.integration.entity.EntityWithFieldLevelConstraint;
import info.archinnov.achilles.test.integration.entity.EntityWithGroupConstraint;
import info.archinnov.achilles.test.integration.entity.EntityWithGroupConstraint.CustomValidationInterceptor;
import info.archinnov.achilles.test.integration.entity.EntityWithPropertyLevelConstraint;

public class BeanValidationIT {

    @Rule
    public AchillesInternalCQLResource resource = new AchillesInternalCQLResource(
            EntityWithClassLevelConstraint.TABLE_NAME, EntityWithFieldLevelConstraint.TABLE_NAME,
            EntityWithPropertyLevelConstraint.TABLE_NAME);

    private PersistenceManager manager = resource.getPersistenceManager();

    @Test
    public void should_validate_entity_constrained_on_field() throws Exception {
        // Given
        Long id = RandomUtils.nextLong(0,Long.MAX_VALUE);
        EntityWithFieldLevelConstraint entity = new EntityWithFieldLevelConstraint();
        entity.setId(id);
        entity.setName("name");
        manager.insert(entity);

        // When
        EntityWithFieldLevelConstraint found = manager.find(EntityWithFieldLevelConstraint.class, id);

        // Then
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("name");
    }

    @Test
    public void should_validate_entity_constrained_on_property() throws Exception {
        // Given
        Long id = RandomUtils.nextLong(0,Long.MAX_VALUE);
        EntityWithPropertyLevelConstraint entity = new EntityWithPropertyLevelConstraint();
        entity.setId(id);
        entity.setName("name");
        manager.insert(entity);

        // When
        EntityWithPropertyLevelConstraint found = manager.find(EntityWithPropertyLevelConstraint.class, id);

        // Then
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("name");
    }

    @Test
    public void should_validate_entity_constrained_on_class() throws Exception {
        // Given
        Long id = RandomUtils.nextLong(0,Long.MAX_VALUE);
        EntityWithClassLevelConstraint entity = new EntityWithClassLevelConstraint();
        entity.setId(id);
        entity.setFirstname("firstname");
        entity.setLastname("lastname");
        manager.insert(entity);

        // When
        EntityWithClassLevelConstraint found = manager.find(EntityWithClassLevelConstraint.class, id);

        // Then
        assertThat(found).isNotNull();
        assertThat(found.getFirstname()).isEqualTo("firstname");
        assertThat(found.getLastname()).isEqualTo("lastname");
    }

    @Test
    public void should_error_on_invalid_field_persist() throws Exception {
        // Given
        boolean exceptionRaised = false;
        Long id = RandomUtils.nextLong(0,Long.MAX_VALUE);
        EntityWithFieldLevelConstraint entity = new EntityWithFieldLevelConstraint();
        entity.setId(id);

        StringBuilder errorMessage = new StringBuilder("Bean validation error : \n");
        errorMessage.append("\tproperty 'name' of class '");
        errorMessage.append(EntityWithFieldLevelConstraint.class.getCanonicalName()).append("'");

        try {
            // When
            manager.insert(entity);
        } catch (AchillesBeanValidationException ex) {
            // Then
            assertThat(ex.getMessage()).contains(errorMessage.toString());
            exceptionRaised = true;
        }
        assertThat(exceptionRaised).isTrue();
    }

    @Test
    public void should_error_on_invalid_field_update() throws Exception {
        // Given
        boolean exceptionRaised = false;
        Long id = RandomUtils.nextLong(0,Long.MAX_VALUE);
        EntityWithFieldLevelConstraint entity = new EntityWithFieldLevelConstraint();
        entity.setId(id);
        entity.setName("name");

        StringBuilder errorMessage = new StringBuilder("Bean validation error : \n");
        errorMessage.append("\tproperty 'name' of class '");
        errorMessage.append(EntityWithFieldLevelConstraint.class.getCanonicalName()).append("'");
        manager.insert(entity);

        try {
            // When
            final EntityWithFieldLevelConstraint proxy = manager.forUpdate(EntityWithFieldLevelConstraint.class, entity.getId());

            proxy.setName(null);
            manager.update(proxy);
        } catch (AchillesBeanValidationException ex) {

            // Then
            assertThat(ex.getMessage()).contains(errorMessage.toString());
            exceptionRaised = true;
        }
        assertThat(exceptionRaised).isTrue();
    }

    @Test
    public void should_error_on_invalid_property_persist() throws Exception {
        // Given
        boolean exceptionRaised = false;
        Long id = RandomUtils.nextLong(0,Long.MAX_VALUE);
        EntityWithPropertyLevelConstraint entity = new EntityWithPropertyLevelConstraint();
        entity.setId(id);

        StringBuilder errorMessage = new StringBuilder("Bean validation error : \n");
        errorMessage.append("\tproperty 'name' of class '");
        errorMessage.append(EntityWithPropertyLevelConstraint.class.getCanonicalName()).append("'");

        try {
            // When
            manager.insert(entity);
        } catch (AchillesBeanValidationException ex) {
            // Then
            assertThat(ex.getMessage()).contains(errorMessage.toString());
            exceptionRaised = true;
        }
        assertThat(exceptionRaised).isTrue();
    }

    @Test
    public void should_error_on_invalid_property_update() throws Exception {
        // Given
        boolean exceptionRaised = false;
        Long id = RandomUtils.nextLong(0,Long.MAX_VALUE);
        EntityWithPropertyLevelConstraint entity = new EntityWithPropertyLevelConstraint();
        entity.setId(id);
        entity.setName("name");

        StringBuilder errorMessage = new StringBuilder("Bean validation error : \n");
        errorMessage.append("\tproperty 'name' of class '");
        errorMessage.append(EntityWithPropertyLevelConstraint.class.getCanonicalName()).append("'");
        manager.insert(entity);

        try {
            // When
            final EntityWithPropertyLevelConstraint proxy = manager.forUpdate(EntityWithPropertyLevelConstraint.class, entity.getId());

            proxy.setName(null);
            manager.update(proxy);
        } catch (AchillesBeanValidationException ex) {
            // Then
            assertThat(ex.getMessage()).contains(errorMessage.toString());
            exceptionRaised = true;
        }
        assertThat(exceptionRaised).isTrue();
    }

    @Test
    public void should_error_on_invalid_class_persist() throws Exception {
        // Given
        boolean exceptionRaised = false;
        Long id = RandomUtils.nextLong(0,Long.MAX_VALUE);
        EntityWithClassLevelConstraint entity = new EntityWithClassLevelConstraint();
        entity.setId(id);
        entity.setFirstname("fn");

        StringBuilder errorMessage = new StringBuilder("Bean validation error : \n");
        errorMessage.append("\tfirstname and lastname should not be blank for class '");
        errorMessage.append(EntityWithClassLevelConstraint.class.getCanonicalName()).append("'");

        try {
            // When
            manager.insert(entity);
        } catch (AchillesBeanValidationException ex) {
            // Then
            assertThat(ex.getMessage()).contains(errorMessage.toString());
            exceptionRaised = true;
        }
        assertThat(exceptionRaised).isTrue();
    }

    @Test
    public void should_error_on_invalid_class_update() throws Exception {
        // Given
        boolean exceptionRaised = false;
        Long id = RandomUtils.nextLong(0,Long.MAX_VALUE);
        EntityWithClassLevelConstraint entity = new EntityWithClassLevelConstraint();
        entity.setId(id);
        entity.setFirstname("fn");
        entity.setLastname("ln");

        StringBuilder errorMessage = new StringBuilder("Bean validation error : \n");
        errorMessage.append("\tfirstname and lastname should not be blank for class '");
        errorMessage.append(EntityWithClassLevelConstraint.class.getCanonicalName()).append("'");

        manager.insert(entity);

        try {
            // When
            final EntityWithClassLevelConstraint proxy = manager.forUpdate(EntityWithClassLevelConstraint.class, entity.getId());

            proxy.setFirstname(null);
            manager.update(proxy);
        } catch (AchillesBeanValidationException ex) {
            // Then
            assertThat(ex.getMessage()).contains(errorMessage.toString());
            exceptionRaised = true;
        }
        assertThat(exceptionRaised).isTrue();
    }

    @Test
    public void should_use_custom_bean_validator_interceptor() throws Exception {
        // Given
        Long id = RandomUtils.nextLong(0,Long.MAX_VALUE);
        boolean exceptionRaised = false;

        Session nativeSession = this.manager.getNativeSession();
        Cluster cluster = nativeSession.getCluster();
        CustomValidationInterceptor interceptor = new CustomValidationInterceptor();

        Map<ConfigurationParameters, Object> configMap = new HashMap<>();
        configMap.put(NATIVE_SESSION, nativeSession);
        configMap.put(ENTITIES_LIST, Arrays.asList(EntityWithGroupConstraint.class));
        configMap.put(BEAN_VALIDATION_ENABLE, true);
        configMap.put(EVENT_INTERCEPTORS, Arrays.asList(interceptor));
        configMap.put(KEYSPACE_NAME, "achilles_test");
        PersistenceManagerFactory managerFactory = PersistenceManagerFactoryBuilder.build(cluster, configMap);
        PersistenceManager manager = managerFactory.createPersistenceManager();

        EntityWithGroupConstraint entity = new EntityWithGroupConstraint();
        entity.setId(id);

        StringBuilder errorMessage = new StringBuilder("Bean validation error : \n");
        errorMessage.append("\tproperty 'name' of class '");
        errorMessage.append(EntityWithGroupConstraint.class.getCanonicalName()).append("'");

        try {
            // When
            manager.insert(entity);
        } catch (AchillesBeanValidationException ex) {
            // Then
            assertThat(ex.getMessage()).contains(errorMessage.toString());
            exceptionRaised = true;
        }
        assertThat(exceptionRaised).isTrue();
    }
}
