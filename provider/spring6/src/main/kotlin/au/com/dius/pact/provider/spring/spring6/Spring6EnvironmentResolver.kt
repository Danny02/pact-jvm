package au.com.dius.pact.provider.spring.spring6

import au.com.dius.pact.core.support.expressions.SystemPropertyResolver
import au.com.dius.pact.core.support.expressions.ValueResolver
import org.springframework.core.env.Environment

class Spring6EnvironmentResolver(private val environment: Environment) : ValueResolver {
  override fun resolveValue(property: String?): String? {
    val tuple = SystemPropertyResolver.PropertyValueTuple(property).invoke()
    return environment.getProperty(tuple.propertyName, tuple.defaultValue)
  }

  override fun resolveValue(property: String?, default: String?): String? {
    return environment.getProperty(property, default)
  }

  override fun propertyDefined(property: String) = environment.containsProperty(property)
}
