package org.pkl.commons

/**
 * Marks an element as experimental; downstream library consumers must opt-in to use it.
 */
@Target(
  AnnotationTarget.CLASS,
  AnnotationTarget.PROPERTY,
  AnnotationTarget.FIELD,
  AnnotationTarget.LOCAL_VARIABLE,
  AnnotationTarget.VALUE_PARAMETER,
  AnnotationTarget.CONSTRUCTOR,
  AnnotationTarget.FUNCTION,
  AnnotationTarget.PROPERTY_GETTER,
  AnnotationTarget.PROPERTY_SETTER,
  AnnotationTarget.TYPEALIAS
)
@MustBeDocumented
@RequiresOptIn("This symbol is experimental within Pkl. Please opt-in. Backward compatibility is not guaranteed.")
annotation class PklExperimental
