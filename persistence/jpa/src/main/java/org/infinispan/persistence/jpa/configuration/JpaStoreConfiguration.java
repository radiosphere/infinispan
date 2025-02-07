package org.infinispan.persistence.jpa.configuration;

import org.infinispan.commons.configuration.BuiltBy;
import org.infinispan.commons.configuration.ConfigurationFor;
import org.infinispan.commons.configuration.attributes.Attribute;
import org.infinispan.commons.configuration.attributes.AttributeDefinition;
import org.infinispan.commons.configuration.attributes.AttributeSerializer;
import org.infinispan.commons.configuration.attributes.AttributeSet;
import org.infinispan.configuration.cache.AbstractStoreConfiguration;
import org.infinispan.configuration.cache.AsyncStoreConfiguration;
import org.infinispan.configuration.serializing.SerializedWith;
import org.infinispan.persistence.jpa.JpaStore;

/**
 * JpaStoreConfiguration.
 *
 * @author <a href="mailto:rtsang@redhat.com">Ray Tsang</a>
 * @since 6.0
 */
@BuiltBy(JpaStoreConfigurationBuilder.class)
@ConfigurationFor(JpaStore.class)
@SerializedWith(JpaStoreConfigurationSerializer.class)
public class JpaStoreConfiguration extends AbstractStoreConfiguration {

   static final AttributeDefinition<String> PERSISTENCE_UNIT_NAME = AttributeDefinition.builder(org.infinispan.persistence.jpa.configuration.Attribute.PERSISTENCE_UNIT_NAME, null, String.class).immutable().build();
   static final AttributeDefinition<Class> ENTITY_CLASS = AttributeDefinition.builder(org.infinispan.persistence.jpa.configuration.Attribute.ENTITY_CLASS_NAME, null, Class.class).immutable()
         .serializer(AttributeSerializer.CLASS_NAME).build();
   static final AttributeDefinition<Boolean> STORE_METADATA = AttributeDefinition.builder(org.infinispan.persistence.jpa.configuration.Attribute.STORE_METADATA, true).immutable().build();

   public static AttributeSet attributeDefinitionSet() {
      return new AttributeSet(JpaStoreConfiguration.class, AbstractStoreConfiguration.attributeDefinitionSet(), PERSISTENCE_UNIT_NAME, ENTITY_CLASS, STORE_METADATA);
   }

   private final Attribute<String> persistenceUnitName;
   private final Attribute<Class> entityClass;
   private final Attribute<Boolean> storeMetadata;

   protected JpaStoreConfiguration(AttributeSet attributes, AsyncStoreConfiguration async) {
      super(attributes, async);
      persistenceUnitName = attributes.attribute(PERSISTENCE_UNIT_NAME);
      entityClass = attributes.attribute(ENTITY_CLASS);
      storeMetadata = attributes.attribute(STORE_METADATA);
   }

   public String persistenceUnitName() {
      return persistenceUnitName.get();
   }

   public Class<?> entityClass() {
      return entityClass.get();
   }

   public long batchSize() {
      return attributes.attribute(MAX_BATCH_SIZE).get();
   }

   public boolean storeMetadata() {
      return storeMetadata.get();
   }
}
