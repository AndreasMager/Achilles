package info.archinnov.achilles.entity.operations;

import info.archinnov.achilles.entity.EntityHelper;
import info.archinnov.achilles.entity.metadata.EntityMeta;
import info.archinnov.achilles.validation.Validator;

import java.util.Map;

/**
 * EntityValidator
 * 
 * @author DuyHai DOAN
 * 
 */
public class EntityValidator
{
	private EntityHelper helper = new EntityHelper();

	@SuppressWarnings("rawtypes")
	public void validateEntity(Object entity, Map<Class<?>, EntityMeta<?>> entityMetaMap)
	{
		Validator.validateNotNull(entity, "Entity should not be null");

		Class baseClass = helper.deriveBaseClass(entity);
		EntityMeta<?> entityMeta = entityMetaMap.get(baseClass);
		validateEntity(entity, entityMeta);

	}

	public void validateEntity(Object entity, EntityMeta<?> entityMeta)
	{
		Validator.validateNotNull(entityMeta, "The entity " + entity.getClass().getCanonicalName()
				+ " is not managed by Achilles");

		Object id = helper.getKey(entity, entityMeta.getIdMeta());
		if (id == null)
		{
			throw new IllegalArgumentException("Cannot get primary key for entity "
					+ entity.getClass().getCanonicalName());
		}
	}

	@SuppressWarnings("unchecked")
	public <T, ID> void validateNotCFDirectMapping(Object entity,
			Map<Class<?>, EntityMeta<?>> entityMetaMap)
	{
		Validator.validateNotNull(entity, "Entity should not be null");

		Class<T> baseClass = (Class<T>) helper.deriveBaseClass(entity);
		EntityMeta<ID> entityMeta = (EntityMeta<ID>) entityMetaMap.get(baseClass);

		if (entityMeta.isColumnFamilyDirectMapping())
		{
			throw new IllegalArgumentException(
					"This operation is not allowed for an entity directly mapped to a native column family");
		}

	}
}
