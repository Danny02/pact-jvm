package au.com.dius.pact.consumer

import au.com.dius.pact.consumer.dsl.DslPart
import au.com.dius.pact.consumer.dsl.Matcher
import au.com.dius.pact.consumer.dsl.MetadataBuilder
import au.com.dius.pact.consumer.xml.PactXmlBuilder
import au.com.dius.pact.core.model.Consumer
import au.com.dius.pact.core.model.ContentType
import au.com.dius.pact.core.model.InvalidPactException
import au.com.dius.pact.core.model.OptionalBody
import au.com.dius.pact.core.model.Pact
import au.com.dius.pact.core.model.PactSpecVersion
import au.com.dius.pact.core.model.Provider
import au.com.dius.pact.core.model.ProviderState
import au.com.dius.pact.core.model.V4Interaction
import au.com.dius.pact.core.model.V4Pact
import au.com.dius.pact.core.model.generators.Category
import au.com.dius.pact.core.model.messaging.Message
import au.com.dius.pact.core.model.messaging.MessagePact
import au.com.dius.pact.core.model.v4.MessageContents
import org.json.JSONObject
import java.util.Locale

/**
 * PACT DSL builder for v3 specification messages or v4 asynchronous messages
 */
class MessagePactBuilder @JvmOverloads constructor(
  /**
   * The consumer for the pact.
   */
  private var consumer: Consumer = Consumer(),

  /**
   * The provider for the pact.
   */
  private var provider: Provider = Provider(),

  /**
   * Provider states
   */
  private var providerStates: MutableList<ProviderState> = mutableListOf(),

  /**
   * Messages for the pact
   */
  private var messages: MutableList<V4Interaction.AsynchronousMessage> = mutableListOf(),

  /**
   * Specification Version
   */
  private var specVersion: PactSpecVersion = PactSpecVersion.V3
) {

  constructor(specVersion: PactSpecVersion) :
    this(Consumer(), Provider(), mutableListOf(), mutableListOf(), specVersion)

  /**
   * Name the consumer of the pact
   *
   * @param consumer Consumer name
   */
  fun consumer(consumer: String): MessagePactBuilder {
    this.consumer = Consumer(consumer)
    return this
  }

  /**
   * Name the provider that the consumer has a pact with.
   *
   * @param provider provider name
   * @return this builder.
   */
  fun hasPactWith(provider: String): MessagePactBuilder {
    this.provider = Provider(provider)
    return this
  }

  /**
   * Sets the provider state.
   *
   * @param providerState description of the provider state
   * @return this builder.
   */
  fun given(providerState: String): MessagePactBuilder {
    this.providerStates.add(ProviderState(providerState))
    return this
  }

  /**
   * Sets the provider state.
   *
   * @param providerState description of the provider state
   * @param params key/value pairs to describe state
   * @return this builder.
   */
  fun given(providerState: String, params: Map<String, Any>): MessagePactBuilder {
    this.providerStates.add(ProviderState(providerState, params))
    return this
  }

  /**
   * Sets the provider state.
   *
   * @param providerState state of the provider
   * @return this builder.
   */
  fun given(providerState: ProviderState): MessagePactBuilder {
    this.providerStates.add(providerState)
    return this
  }

  /**
   * Adds a message expectation in the pact.
   *
   * @param description message description.
   */
  fun expectsToReceive(description: String): MessagePactBuilder {
    messages.add(V4Interaction.AsynchronousMessage("", description, providerStates = providerStates))
    return this
  }

  /**
   *  Adds the expected metadata to the message
   */
  fun withMetadata(metadata: Map<String, Any>): MessagePactBuilder {
    if (messages.isEmpty()) {
      throw InvalidPactException("expectsToReceive is required before withMetaData")
    }

    val message = messages.last()
    message.contents = message.contents.copy(metadata = metadata.mapValues { (key, value) ->
      if (value is Matcher) {
        message.contents.matchingRules.addCategory("metadata").addRule(key, value.matcher!!)
        if (value.generator != null) {
          message.contents.generators.addGenerator(category = Category.METADATA, generator = value.generator!!)
        }
        value.value
      } else {
        value
      }
    }.toMutableMap())
    return this
  }

  /**
   *  Adds the expected metadata to the message using a builder
   */
  fun withMetadata(consumer: java.util.function.Consumer<MetadataBuilder>): MessagePactBuilder {
    if (messages.isEmpty()) {
      throw InvalidPactException("expectsToReceive is required before withMetaData")
    }

    val message = messages.last()
    val metadataBuilder = MetadataBuilder()
    consumer.accept(metadataBuilder)
    message.contents = message.contents.copy(metadata = metadataBuilder.values)
    message.contents.matchingRules.addCategory(metadataBuilder.matchers)
    message.contents.generators.addGenerators(Category.METADATA, metadataBuilder.generators)
    return this
  }

  /**
   * Adds the JSON body as the message content
   */
  fun withContent(body: DslPart): MessagePactBuilder {
    if (messages.isEmpty()) {
      throw InvalidPactException("expectsToReceive is required before withContent")
    }

    val message = messages.last()
    val metadata = message.contents.metadata.toMutableMap()
    val contentTypeEntry = metadata.entries.find {
      it.key.lowercase() == "contenttype" || it.key.lowercase() == "content-type"
    }

    var contentType = ContentType.JSON
    if (contentTypeEntry == null) {
      metadata["contentType"] = contentType.toString()
    } else {
      contentType = ContentType(contentTypeEntry.value.toString())
      metadata.remove(contentTypeEntry.key)
      metadata["contentType"] = contentTypeEntry.value
    }

    val parent = body.close()!!
    message.contents = message.contents.copy(
      contents = OptionalBody.body(parent.toString().toByteArray(contentType.asCharset()), contentType),
      metadata = metadata
    )
    message.contents.matchingRules.addCategory(parent.matchers)
    message.contents.generators.addGenerators(parent.generators)

    return this
  }

  /**
   * Adds the XML body as the message content
   */
  fun withContent(xmlBuilder: PactXmlBuilder): MessagePactBuilder {
    if (messages.isEmpty()) {
      throw InvalidPactException("expectsToReceive is required before withContent")
    }

    val message = messages.last()
    val metadata = message.contents.metadata.toMutableMap()
    val contentTypeEntry = metadata.entries.find {
      it.key.lowercase() == "contenttype" || it.key.lowercase() == "content-type"
    }

    var contentType = ContentType.XML
    if (contentTypeEntry == null) {
      metadata["contentType"] = contentType.toString()
    } else {
      contentType = ContentType(contentTypeEntry.value.toString())
      metadata.remove(contentTypeEntry.key)
      metadata["contentType"] = contentTypeEntry.value
    }

    message.contents = message.contents.copy(
      contents = OptionalBody.body(xmlBuilder.asBytes(contentType.asCharset()), contentType),
      metadata = metadata
    )
    message.contents.matchingRules.addCategory(xmlBuilder.matchingRules)
    message.contents.generators.addGenerators(xmlBuilder.generators)

    return this
  }

  /**
   * Adds the text as the message contents
   */
  @JvmOverloads
  fun withContent(contents: String, contentType: String = "text/plain"): MessagePactBuilder {
    if (messages.isEmpty()) {
      throw InvalidPactException("expectsToReceive is required before withContent")
    }

    val message = messages.last()
    val metadata = message.contents.metadata.toMutableMap()
    metadata["contentType"] = contentType

    val ct = ContentType(contentType)
    message.contents = message.contents.copy(
      contents = OptionalBody.body(contents.toByteArray(ct.asCharset()), ct),
      metadata = metadata
    )

    return this
  }

  /**
   * Adds the JSON body as the message content
   */
  fun withContent(json: JSONObject): MessagePactBuilder {
    if (messages.isEmpty()) {
      throw InvalidPactException("expectsToReceive is required before withContent")
    }

    val message = messages.last()
    val metadata = message.contents.metadata.toMutableMap()
    val contentTypeEntry = metadata.entries.find {
      it.key.lowercase() == "contenttype" || it.key.lowercase() == "content-type"
    }

    var contentType = ContentType.JSON
    if (contentTypeEntry == null) {
      metadata["contentType"] = contentType.toString()
    } else {
      contentType = ContentType(contentTypeEntry.value.toString())
    }

    message.contents = message.contents.copy(
      contents = OptionalBody.body(json.toString().toByteArray(contentType.asCharset()), contentType),
      metadata = metadata
    )

    return this
  }

  /**
   * Terminates the DSL and builds a pact to represent the interactions
   */
  fun <P : Pact?> toPact(pactClass: Class<P>): P {
    return when {
      pactClass.isAssignableFrom(V4Pact::class.java) -> {
        V4Pact(consumer, provider, messages.toMutableList()) as P
      }
      pactClass.isAssignableFrom(MessagePact::class.java) -> {
        return MessagePact(provider, consumer, messages.map { it.asV3Interaction() }.toMutableList()) as P
      }
      else -> {
        throw IllegalArgumentException(pactClass.simpleName + " is not a valid Pact class")
      }
    }
  }

  /**
   * Convert this builder into a Pact
   */
  fun <P : Pact> toPact(): P {
    return if (specVersion == PactSpecVersion.V4) {
      V4Pact(consumer, provider, messages.toMutableList()) as P
    } else {
      MessagePact(provider, consumer, messages.map { it.asV3Interaction() }.toMutableList()) as P
    }
  }
}
