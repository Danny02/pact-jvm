package au.com.dius.pact.provider

import au.com.dius.pact.core.model.BrokerUrlSource
import au.com.dius.pact.core.model.Consumer
import au.com.dius.pact.core.model.ContentType
import au.com.dius.pact.core.model.FileSource
import au.com.dius.pact.core.model.HttpRequest
import au.com.dius.pact.core.model.HttpResponse
import au.com.dius.pact.core.model.Interaction
import au.com.dius.pact.core.model.InvalidPathExpression
import au.com.dius.pact.core.model.OptionalBody
import au.com.dius.pact.core.model.Pact
import au.com.dius.pact.core.model.PactReader
import au.com.dius.pact.core.model.PluginData
import au.com.dius.pact.core.model.Provider
import au.com.dius.pact.core.model.ProviderState
import au.com.dius.pact.core.model.Request
import au.com.dius.pact.core.model.RequestResponseInteraction
import au.com.dius.pact.core.model.RequestResponsePact
import au.com.dius.pact.core.model.Response
import au.com.dius.pact.core.model.UnknownPactSource
import au.com.dius.pact.core.model.UrlSource
import au.com.dius.pact.core.model.V4Interaction
import au.com.dius.pact.core.model.V4Pact
import au.com.dius.pact.core.model.generators.Generators
import au.com.dius.pact.core.model.matchingrules.MatchingRules
import au.com.dius.pact.core.model.matchingrules.MatchingRulesImpl
import au.com.dius.pact.core.model.matchingrules.RegexMatcher
import au.com.dius.pact.core.model.messaging.Message
import au.com.dius.pact.core.model.messaging.MessageInteraction
import au.com.dius.pact.core.model.v4.MessageContents
import au.com.dius.pact.core.pactbroker.IPactBrokerClient
import au.com.dius.pact.core.pactbroker.PactBrokerClient
import au.com.dius.pact.core.pactbroker.TestResult
import au.com.dius.pact.core.support.Result
import au.com.dius.pact.core.support.expressions.SystemPropertyResolver
import au.com.dius.pact.core.support.json.JsonValue
import au.com.dius.pact.provider.reporters.Event
import au.com.dius.pact.provider.reporters.VerifierReporter
import groovy.json.JsonOutput
import io.pact.plugins.jvm.core.PluginConfiguration
import io.pact.plugins.jvm.core.PluginManager
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

import java.util.function.Function

@SuppressWarnings('UnnecessaryGetter')
class ProviderVerifierSpec extends Specification {

  ProviderVerifier verifier

  def setup() {
    verifier = Spy(ProviderVerifier)
  }

  def 'if no consumer filter is defined, returns true'() {
    given:
    verifier.projectHasProperty = { false }
    def consumer = new ConsumerInfo('bob')

    when:
    boolean result = verifier.filterConsumers(consumer)

    then:
    result
  }

  def 'if a consumer filter is defined, returns false if the consumer name does not match'() {
    given:
    verifier.projectHasProperty = { it == ProviderVerifier.PACT_FILTER_CONSUMERS }
    verifier.projectGetProperty = { 'fred,joe' }
    def consumer = new ConsumerInfo('bob')

    when:
    boolean result = verifier.filterConsumers(consumer)

    then:
    !result
  }

  def 'if a consumer filter is defined, returns true if the consumer name does match'() {
    given:
    verifier.projectHasProperty = { it == ProviderVerifier.PACT_FILTER_CONSUMERS }
    verifier.projectGetProperty = { 'fred,joe,bob' }
    def consumer = new ConsumerInfo('bob')

    when:
    boolean result = verifier.filterConsumers(consumer)

    then:
    result
  }

  def 'trims whitespaces off the consumer names'() {
    given:
    verifier.projectHasProperty = { it == ProviderVerifier.PACT_FILTER_CONSUMERS }
    verifier.projectGetProperty = { 'fred,\tjoe, bob\n' }
    def consumer = new ConsumerInfo('bob')

    when:
    boolean result = verifier.filterConsumers(consumer)

    then:
    result
  }

  def 'if no interaction filter is defined, returns true'() {
    given:
    verifier.projectHasProperty = { false }
    def interaction = [:] as Interaction

    when:
    boolean result = verifier.filterInteractions(interaction)

    then:
    result
  }

  def 'if an interaction filter is defined, returns false if the interaction description does not match'() {
    given:
    verifier.projectHasProperty = { it == ProviderVerifier.PACT_FILTER_DESCRIPTION }
    verifier.projectGetProperty = { 'fred' }
    def interaction = [getDescription: { 'bob' }] as Interaction

    when:
    boolean result = verifier.filterInteractions(interaction)

    then:
    !result
  }

  def 'if an interaction filter is defined, returns true if the interaction description does match'() {
    given:
    verifier.projectHasProperty = { it == ProviderVerifier.PACT_FILTER_DESCRIPTION }
    verifier.projectGetProperty = { 'bob' }
    def interaction = [getDescription: { 'bob' }] as Interaction

    when:
    boolean result = verifier.filterInteractions(interaction)

    then:
    result
  }

  def 'uses regexs to match the description'() {
    given:
    verifier.projectHasProperty = { it == ProviderVerifier.PACT_FILTER_DESCRIPTION }
    verifier.projectGetProperty = { 'bob.*' }
    def interaction = [getDescription: { 'bobby' }] as Interaction

    when:
    boolean result = verifier.filterInteractions(interaction)

    then:
    result
  }

  def 'if no state filter is defined, returns true'() {
    given:
    verifier.projectHasProperty = { false }
    def interaction = [:] as Interaction

    when:
    boolean result = verifier.filterInteractions(interaction)

    then:
    result
  }

  def 'if a state filter is defined, returns false if the interaction state does not match'() {
    given:
    verifier.projectHasProperty = { it == ProviderVerifier.PACT_FILTER_PROVIDERSTATE }
    verifier.projectGetProperty = { 'fred' }
    def interaction = [getProviderStates: { [new ProviderState('bob')] }] as Interaction

    when:
    boolean result = verifier.filterInteractions(interaction)

    then:
    !result
  }

  def 'if a state filter is defined, returns true if the interaction state does match'() {
    given:
    verifier.projectHasProperty = { it == ProviderVerifier.PACT_FILTER_PROVIDERSTATE }
    verifier.projectGetProperty = { 'bob' }
    def interaction = [getProviderStates: { [new ProviderState('bob')] }] as Interaction

    when:
    boolean result = verifier.filterInteractions(interaction)

    then:
    result
  }

  def 'if a state filter is defined, returns true if any interaction state does match'() {
    given:
    verifier.projectHasProperty = { it == ProviderVerifier.PACT_FILTER_PROVIDERSTATE }
    verifier.projectGetProperty = { 'bob' }
    def interaction = [
      getProviderStates: { [new ProviderState('fred'), new ProviderState('bob')] }
    ] as Interaction

    when:
    boolean result = verifier.filterInteractions(interaction)

    then:
    result
  }

  def 'uses regexs to match the state'() {
    given:
    verifier.projectHasProperty = { it == ProviderVerifier.PACT_FILTER_PROVIDERSTATE }
    verifier.projectGetProperty = { 'bob.*' }
    def interaction = [getProviderStates: { [new ProviderState('bobby')] }] as Interaction

    when:
    boolean result = verifier.filterInteractions(interaction)

    then:
    result
  }

  def 'if the state filter is empty, returns false if the interaction state is defined'() {
    given:
    verifier.projectHasProperty = { it == ProviderVerifier.PACT_FILTER_PROVIDERSTATE }
    verifier.projectGetProperty = { '' }
    def interaction = [getProviderStates: { [new ProviderState('bob')] }] as Interaction

    when:
    boolean result = verifier.filterInteractions(interaction)

    then:
    !result
  }

  def 'if the state filter is empty, returns true if the interaction state is not defined'() {
    given:
    verifier.projectHasProperty = { it == ProviderVerifier.PACT_FILTER_PROVIDERSTATE }
    verifier.projectGetProperty = { '' }
    def interaction = [getProviderStates: { [] }] as Interaction

    when:
    boolean result = verifier.filterInteractions(interaction)

    then:
    result
  }

  def 'if the state filter and interaction filter is defined, must match both'() {
    given:
    verifier.projectHasProperty = { true }
    verifier.projectGetProperty = {
      switch (it) {
        case ProviderVerifier.PACT_FILTER_DESCRIPTION:
          '.*ddy'
          break
        case ProviderVerifier.PACT_FILTER_PROVIDERSTATE:
          'bob.*'
          break
      }
    }
    def interaction = [
      getProviderStates: { [new ProviderState('bobby')] },
      getDescription:  { 'freddy' }
    ] as Interaction

    when:
    boolean result = verifier.filterInteractions(interaction)

    then:
    result
  }

  def 'if the state filter and interaction filter is defined, is false if description does not match'() {
    given:
    verifier.projectHasProperty = { true }
    verifier.projectGetProperty = {
      switch (it) {
        case ProviderVerifier.PACT_FILTER_DESCRIPTION:
          '.*ddy'
          break
        case ProviderVerifier.PACT_FILTER_PROVIDERSTATE:
          'bob.*'
          break
      }
    }
    def interaction = [
      getProviderStates: { [new ProviderState('boddy')] },
      getDescription: { 'freddy' }
    ] as Interaction

    when:
    boolean result = verifier.filterInteractions(interaction)

    then:
    !result
  }

  def 'if the state filter and interaction filter is defined, is false if state does not match'() {
    given:
    verifier.projectHasProperty = { true }
    verifier.projectGetProperty = {
      switch (it) {
        case ProviderVerifier.PACT_FILTER_DESCRIPTION:
          '.*ddy'
          break
        case ProviderVerifier.PACT_FILTER_PROVIDERSTATE:
          'bob.*'
          break
      }
    }
    def interaction = [
      getProviderStates: { [new ProviderState('bobby')] },
      getDescription: { 'frebby' }
    ] as Interaction

    when:
    boolean result = verifier.filterInteractions(interaction)

    then:
    !result
  }

  def 'if the state filter and interaction filter is defined, is false if both do not match'() {
    given:
    verifier.projectHasProperty = { true }
    verifier.projectGetProperty = {
      switch (it) {
        case ProviderVerifier.PACT_FILTER_DESCRIPTION:
          '.*ddy'
          break
        case ProviderVerifier.PACT_FILTER_PROVIDERSTATE:
          'bob.*'
          break
      }
    }
    def interaction = [
      getProviderStates: { [new ProviderState('joe')] },
      getDescription: { 'authur' }
    ] as Interaction

    when:
    boolean result = verifier.filterInteractions(interaction)

    then:
    !result
  }

  def 'when loading a pact file for a consumer, it should pass on any authentication options'() {
    given:
    def pactFile = new UrlSource('http://some.pact.file/')
    def consumer = new ConsumerInfo(pactSource: pactFile, pactFileAuthentication: ['basic', 'test', 'pwd'])
    verifier.pactReader = Mock(PactReader)

    when:
    verifier.loadPactFileForConsumer(consumer)

    then:
    1 * verifier.pactReader.loadPact(pactFile, ['authentication': ['basic', 'test', 'pwd']]) >> Mock(Pact)
  }

  def 'when loading a pact file for a consumer, it handles a closure'() {
    given:
    def pactFile = new UrlSource('http://some.pact.file/')
    def consumer = new ConsumerInfo(pactSource: { pactFile })
    verifier.pactReader = Mock(PactReader)

    when:
    verifier.loadPactFileForConsumer(consumer)

    then:
    1 * verifier.pactReader.loadPact(pactFile, [:]) >> Mock(Pact)
  }

  def 'when pact.filter.pacturl is set, use that URL for the Pact file'() {
    given:
    def pactUrl = 'https://test.pact.dius.com.au/pacticipants/Foo%20Web%20Client/versions/1.0.1'
    def pactFile = new UrlSource('http://some.pact.file/')
    def consumer = new ConsumerInfo(pactSource: pactFile, pactFileAuthentication: ['basic', 'test', 'pwd'])
    verifier.pactReader = Mock(PactReader)
    verifier.projectHasProperty = { it == ProviderVerifier.PACT_FILTER_PACTURL }
    verifier.projectGetProperty = { it == ProviderVerifier.PACT_FILTER_PACTURL ? pactUrl : null }

    when:
    verifier.loadPactFileForConsumer(consumer)

    then:
    1 * verifier.pactReader.loadPact(new UrlSource(pactUrl), ['authentication': ['basic', 'test', 'pwd']]) >> Mock(Pact)
  }

  static class TestSupport {
    String testMethod() {
      '\"test method result\"'
    }
  }

  def 'is able to verify a message pact'() {
    given:
    def methods = [ TestSupport.getMethod('testMethod') ] as Set
    Message message = new Message('test', [], OptionalBody.body('\"test method result\"'.bytes))
    def interactionMessage = 'test message interaction'
    def failures = [:]
    def reporter = Mock(VerifierReporter)
    verifier.reporters = [reporter]

    when:
    def result = verifier.verifyMessage(methods, message, interactionMessage, failures, false)

    then:
    1 * reporter.receive(Event.BodyComparisonOk.INSTANCE)
    1 * reporter.receive(Event.GeneratesAMessageWhich.INSTANCE)
    1 * reporter.receive(new Event.MetadataComparisonOk(null, null))
    0 * reporter._
    result
  }

  @Unroll
  @SuppressWarnings('UnnecessaryGetter')
  @RestoreSystemProperties
  def 'after verifying a pact, the results are reported back using tags and reportVerificationResults'() {
    given:
    ProviderInfo provider = new ProviderInfo('Test Provider')
    ConsumerInfo consumer = new ConsumerInfo(name: 'Test Consumer', pactSource: UnknownPactSource.INSTANCE)
    PactBrokerClient pactBrokerClient = Mock(PactBrokerClient, constructorArgs: [''])
    verifier.verificationReporter = Mock(VerificationReporter)
    verifier.pactReader = Stub(PactReader)
    def statechange = Stub(StateChange) {
      executeStateChange(*_) >> new StateChangeResult(new Result.Ok([:]))
    }
    def interaction1 = Stub(RequestResponseInteraction)
    def interaction2 = Stub(RequestResponseInteraction)
    def mockPact = Stub(Pact) {
      getSource() >> new BrokerUrlSource('http://localhost', 'http://pact-broker')
      asV4Pact() >> new Result.Err('Not V4')
    }

    verifier.projectHasProperty = { it == ProviderVerifier.PACT_VERIFIER_PUBLISH_RESULTS }
    verifier.projectGetProperty = {
      (it == ProviderVerifier.PACT_VERIFIER_PUBLISH_RESULTS).toString()
    }
    verifier.stateChangeHandler = statechange

    verifier.pactReader.loadPact(_) >> mockPact
    mockPact.interactions >> [interaction1, interaction2]

    def tags = ['tag1', 'tag2', 'tag3']
    System.setProperty('pact.provider.tag', 'tag1,tag2 , tag3 ')

    when:
    verifier.runVerificationForConsumer([:], provider, consumer, pactBrokerClient)

    then:
    1 * verifier.verificationReporter.reportResults(_, finalResult, '0.0.0', pactBrokerClient, tags, _) >>
      new Result.Ok(true)
    1 * verifier.verifyResponseFromProvider(provider, interaction1, _, _, _, _, false) >> result1
    1 * verifier.verifyResponseFromProvider(provider, interaction2, _, _, _, _, false) >> result2

    where:

    result1                         | result2                         | finalResult
    new VerificationResult.Ok()     | new VerificationResult.Ok()     | new TestResult.Ok()
    new VerificationResult.Ok()     | new VerificationResult.Failed() | new TestResult.Failed()
    new VerificationResult.Failed() | new VerificationResult.Ok()     | new TestResult.Failed()
    new VerificationResult.Failed() | new VerificationResult.Failed() | new TestResult.Failed()
  }

  @Unroll
  @SuppressWarnings(['UnnecessaryGetter', 'LineLength'])
  @RestoreSystemProperties
  def 'after verifying a pact, the results are reported back using branch and reportVerificationResults'() {
    given:
    ProviderInfo provider = new ProviderInfo('Test Provider')
    ConsumerInfo consumer = new ConsumerInfo(name: 'Test Consumer', pactSource: UnknownPactSource.INSTANCE)
    PactBrokerClient pactBrokerClient = Mock(PactBrokerClient, constructorArgs: [''])
    verifier.verificationReporter = Mock(VerificationReporter)
    verifier.pactReader = Stub(PactReader)
    def statechange = Stub(StateChange) {
      executeStateChange(*_) >> new StateChangeResult(new Result.Ok([:]))
    }
    def interaction1 = Stub(RequestResponseInteraction)
    def interaction2 = Stub(RequestResponseInteraction)
    def mockPact = Stub(Pact) {
      getSource() >> new BrokerUrlSource('http://localhost', 'http://pact-broker')
      asV4Pact() >> new Result.Err('Not V4')
    }

    verifier.projectHasProperty = { it == ProviderVerifier.PACT_VERIFIER_PUBLISH_RESULTS }
    verifier.projectGetProperty = {
      (it == ProviderVerifier.PACT_VERIFIER_PUBLISH_RESULTS).toString()
    }
    verifier.stateChangeHandler = statechange

    verifier.pactReader.loadPact(_) >> mockPact
    mockPact.interactions >> [interaction1, interaction2]

    def branch = 'master'
    System.setProperty(ProviderVerifier.PACT_PROVIDER_BRANCH, branch)

    when:
    verifier.runVerificationForConsumer([:], provider, consumer, pactBrokerClient)

    then:
    1 * verifier.verificationReporter.reportResults(_, finalResult, '0.0.0', pactBrokerClient, [], branch) >> new Result.Ok(true)
    1 * verifier.verifyResponseFromProvider(provider, interaction1, _, _, _, _, false) >> result1
    1 * verifier.verifyResponseFromProvider(provider, interaction2, _, _, _, _, false) >> result2

    where:

    result1                         | result2                         | finalResult
    new VerificationResult.Ok()     | new VerificationResult.Ok()     | new TestResult.Ok()
    new VerificationResult.Ok()     | new VerificationResult.Failed() | new TestResult.Failed()
    new VerificationResult.Failed() | new VerificationResult.Ok()     | new TestResult.Failed()
    new VerificationResult.Failed() | new VerificationResult.Failed() | new TestResult.Failed()
  }

  def 'return a failed result if reportVerificationResults returns an error'() {
    given:
    ProviderInfo provider = new ProviderInfo('Test Provider')
    ConsumerInfo consumer = new ConsumerInfo(name: 'Test Consumer', pactSource: UnknownPactSource.INSTANCE)
    IPactBrokerClient pactBrokerClient = Mock(IPactBrokerClient)
    verifier.verificationReporter = Mock(VerificationReporter)
    verifier.pactReader = Stub(PactReader)
    def statechange = Stub(StateChange) {
      executeStateChange(*_) >> new StateChangeResult(new Result.Ok([:]))
    }
    def interaction1 = Stub(RequestResponseInteraction)
    def interaction2 = Stub(RequestResponseInteraction)
    def mockPact = Stub(Pact) {
      getSource() >> new BrokerUrlSource('http://localhost', 'http://pact-broker')
      asV4Pact() >> new Result.Err('Not V4')
    }

    verifier.projectHasProperty = { it == ProviderVerifier.PACT_VERIFIER_PUBLISH_RESULTS }
    verifier.projectGetProperty = {
      (it == ProviderVerifier.PACT_VERIFIER_PUBLISH_RESULTS).toString()
    }
    verifier.stateChangeHandler = statechange

    verifier.pactReader.loadPact(_) >> mockPact
    mockPact.interactions >> [interaction1, interaction2]

    when:
    def result = verifier.runVerificationForConsumer([:], provider, consumer, pactBrokerClient)

    then:
    1 * verifier.verificationReporter.reportResults(_, _, '0.0.0', pactBrokerClient, [], _) >>
      new Result.Err(['failed'])
    1 * verifier.verifyResponseFromProvider(provider, interaction1, _, _, _, _, false) >> new VerificationResult.Ok()
    1 * verifier.verifyResponseFromProvider(provider, interaction2, _, _, _, _, false) >> new VerificationResult.Ok()
    result instanceof VerificationResult.Failed
    result.description == 'Failed to publish results to the Pact broker'
    result.failures == ['': [new VerificationFailureType.PublishResultsFailure(['failed'])]]
  }

  @SuppressWarnings('UnnecessaryGetter')
  def 'Do not publish verification results if not all the pact interactions have been verified'() {
    given:
    ProviderInfo provider = new ProviderInfo('Test Provider')
    ConsumerInfo consumer = new ConsumerInfo(name: 'Test Consumer', pactSource: UnknownPactSource.INSTANCE)
    verifier.pactReader = Mock(PactReader)
    def statechange = Mock(StateChange) {
      executeStateChange(*_) >> new StateChangeResult(new Result.Ok([:]))
    }
    def interaction1 = Mock(RequestResponseInteraction) {
      getDescription() >> 'Interaction 1'
      getComments() >> [:]
    }
    interaction1.asSynchronousRequestResponse() >> { interaction1 }
    def interaction2 = Mock(RequestResponseInteraction) {
      getDescription() >> 'Interaction 2'
      getComments() >> [:]
    }
    interaction2.asSynchronousRequestResponse() >> { interaction2 }
    def mockPact = Mock(Pact) {
      getSource() >> UnknownPactSource.INSTANCE
      asV4Pact() >> new Result.Err('Not V4')
    }

    verifier.pactReader.loadPact(_) >> mockPact
    mockPact.interactions >> [interaction1, interaction2]
    verifier.verifyResponseFromProvider(provider, interaction1, _, _, _) >> true
    verifier.verifyResponseFromProvider(provider, interaction2, _, _, _) >> true

    verifier.projectHasProperty = { it == ProviderVerifier.PACT_FILTER_DESCRIPTION }
    verifier.projectGetProperty = { 'Interaction 2' }
    verifier.verificationReporter = Mock(VerificationReporter)
    verifier.stateChangeHandler = statechange

    when:
    verifier.runVerificationForConsumer([:], provider, consumer)

    then:
    0 * verifier.verificationReporter.reportResults(_, _, _, _, _)
  }

  @SuppressWarnings(['UnnecessaryGetter', 'LineLength'])
  def 'Ignore the verification results if publishing is disabled'() {
    given:
    def client = Mock(PactBrokerClient)
    verifier.pactReader = Mock(PactReader)
    def statechange = Mock(StateChange)

    def source = new FileSource('test.txt' as File)
    def providerInfo = new ProviderInfo(verificationType: PactVerification.ANNOTATED_METHOD)
    def consumerInfo = new ConsumerInfo()
    consumerInfo.pactSource = source

    def interaction = new RequestResponseInteraction('Test Interaction')
    def pact = new RequestResponsePact(new Provider(), new Consumer(), [interaction], [:], source)

    verifier.projectHasProperty = {
      it == ProviderVerifier.PACT_VERIFIER_PUBLISH_RESULTS
    }
    verifier.projectGetProperty = {
      switch (it) {
        case ProviderVerifier.PACT_VERIFIER_PUBLISH_RESULTS:
          return 'false'
      }
    }
    verifier.stateChangeHandler = statechange

    when:
    verifier.runVerificationForConsumer([:], providerInfo, consumerInfo, client)

    then:
    1 * verifier.pactReader.loadPact(_) >> pact
    1 * statechange.executeStateChange(_, _, _, _, _, _, _) >> new StateChangeResult(new Result.Ok([:]), '')
    1 * verifier.verifyResponseByInvokingProviderMethods(providerInfo, consumerInfo, interaction, _, _, false, _) >> new VerificationResult.Ok()
    0 * client.publishVerificationResults(_, new TestResult.Ok(), _, _)
  }

  @Unroll
  @RestoreSystemProperties
  def 'test for pact.verifier.publishResults - #description'() {
    given:
    verifier.projectHasProperty = { value != null }
    verifier.projectGetProperty = { value }
    def resolver = SystemPropertyResolver.INSTANCE

    if (value != null) {
      System.setProperty(ProviderVerifier.PACT_VERIFIER_PUBLISH_RESULTS, value)
    }

    expect:
    verifier.publishingResultsDisabled() == result
    DefaultVerificationReporter.INSTANCE.publishingResultsDisabled(resolver) == result

    where:

    description                  | value       | result
    'Property is missing'        | null        | true
    'Property is true'           | 'true'      | false
    'Property is TRUE'           | 'TRUE'      | false
    'Property is false'          | 'false'     | true
    'Property is False'          | 'False'     | true
    'Property is something else' | 'not false' | true
  }

  @RestoreSystemProperties
  def 'defaults to system properties'() {
    given:
    System.properties['provider.verifier.test'] = 'true'

    expect:
    verifier.projectHasProperty.apply('provider.verifier.test')
    verifier.projectGetProperty.apply('provider.verifier.test') == 'true'
    !verifier.projectHasProperty.apply('provider.verifier.test.other')
    verifier.projectGetProperty.apply('provider.verifier.test.other') == null
  }

  def 'verifyInteraction returns an error result if the state change request fails'() {
    given:
    ProviderInfo provider = new ProviderInfo('Test Provider')
    provider.stateChangeUrl = new URL('http://localhost:66/statechange')
    ConsumerInfo consumer = new ConsumerInfo(name: 'Test Consumer', pactSource: UnknownPactSource.INSTANCE)
    def failures = [:]
    Interaction interaction = new RequestResponseInteraction('Test Interaction',
      [new ProviderState('Test State')], new Request(), new Response(), '1234')

    when:
    def result = verifier.verifyInteraction(provider, consumer, failures, interaction)

    then:
    result instanceof VerificationResult.Failed
    result.description == 'State change request failed'
    result.failures.size() == 1
    result.failures['1234'][0].description == 'Provider state change callback failed'
    result.failures['1234'][0].result.stateChangeResult instanceof Result.Err
  }

  def 'verifyInteraction returns an error result if any matcher paths are invalid'() {
    given:
    ProviderInfo provider = new ProviderInfo('Test Provider')
    ConsumerInfo consumer = new ConsumerInfo(name: 'Test Consumer', pactSource: UnknownPactSource.INSTANCE)
    def failures = [:]
    MatchingRules matchingRules = new MatchingRulesImpl()
    matchingRules.addCategory('body')
      .addRule("\$.serviceNode.entity.status.thirdNode['@description]", new RegexMatcher('.*'))
    def json = JsonOutput.toJson([
      serviceNode: [
        entity: [
          status: [
            thirdNode: [
              '@description': 'Test'
            ]
          ]
        ]
      ]
    ])
    Interaction interaction = new RequestResponseInteraction('Test Interaction',
      [new ProviderState('Test State')], new Request(),
      new Response(200, [:], OptionalBody.body(json.bytes), matchingRules), '1234')
    def client = Mock(ProviderClient)
    client.makeRequest(_) >> new ProviderResponse(200, [:], ContentType.JSON, OptionalBody.body(json, ContentType.JSON))

    when:
    def result = verifier.verifyInteraction(provider, consumer, failures, interaction, client)

    then:
    result instanceof VerificationResult.Failed
    result.description == 'Request to provider endpoint failed with an exception'
    result.failures.size() == 1
    result.failures['1234'][0].description == 'Request to provider endpoint failed with an exception'
    result.failures['1234'][0].e instanceof InvalidPathExpression
  }

  def 'verifyInteraction sets the state change error result as pending if it is a V4 pending interaction'() {
    given:
    ProviderInfo provider = new ProviderInfo('Test Provider')
    provider.stateChangeUrl = new URL('http://localhost:66/statechange')
    ConsumerInfo consumer = new ConsumerInfo(name: 'Test Consumer', pactSource: UnknownPactSource.INSTANCE)
    def failures = [:]
    Interaction interaction = new V4Interaction.SynchronousHttp('key', 'Test Interaction',
      [new ProviderState('Test State')], new HttpRequest(), new HttpResponse(), '1234', [:], true)

    when:
    def result = verifier.verifyInteraction(provider, consumer, failures, interaction)

    then:
    result instanceof VerificationResult.Failed
    result.pending == true
  }

  def 'verifyResponseFromProvider returns an error result if the request to the provider fails with an exception'() {
    given:
    ProviderInfo provider = new ProviderInfo('Test Provider')
    def failures = [:]
    Interaction interaction = new RequestResponseInteraction('Test Interaction',
      [new ProviderState('Test State')], new Request(), new Response(), '12345678')
    def client = Mock(ProviderClient)

    when:
    def result = verifier.verifyResponseFromProvider(provider, interaction, 'Test Interaction', failures, client)

    then:
    client.makeRequest(_) >> { throw new IOException('Boom!') }
    result instanceof VerificationResult.Failed
    result.description == 'Request to provider endpoint failed with an exception'
    result.failures.size() == 1
    result.failures['12345678'][0].description == 'Request to provider endpoint failed with an exception'
    result.failures['12345678'][0].e instanceof IOException
  }

  def 'verifyResponseByInvokingProviderMethods returns an error result if the method fails with an exception'() {
    given:
    ProviderInfo provider = new ProviderInfo('Test Provider')
    def failures = [:]
    Interaction interaction = new Message('verifyResponseByInvokingProviderMethods Test Message', [],
      OptionalBody.empty(), new MatchingRulesImpl(), new Generators(), [:], 'abc123')
    IConsumerInfo consumer = Stub()
    def interactionMessage = 'Test'

    when:
    def result = verifier.verifyResponseByInvokingProviderMethods(provider, consumer, interaction,
      interactionMessage, failures, false)

    then:
    result instanceof VerificationResult.Failed
    result.description == 'Request to provider method failed with an exception'
    result.failures.size() == 1
    result.failures['abc123'][0].description == 'Request to provider method failed with an exception'
    result.failures['abc123'][0].e instanceof RuntimeException
  }

  def 'verifyInteraction sets the verification error result as pending if it is a V4 pending interaction'() {
    given:
    ProviderInfo provider = new ProviderInfo('Test Provider')
    ConsumerInfo consumer = new ConsumerInfo(name: 'Test Consumer', pactSource: UnknownPactSource.INSTANCE)
    def failures = [:]
    Interaction interaction = new V4Interaction.SynchronousHttp('key', 'Test Interaction',
      [], new HttpRequest(), new HttpResponse(), '1234', [:], true)
    def client = Mock(ProviderClient)

    when:
    def result = verifier.verifyInteraction(provider, consumer, failures, interaction, client)

    then:
    client.makeRequest(_) >> new ProviderResponse(500, [:], ContentType.JSON, OptionalBody.empty())
    result instanceof VerificationResult.Failed
    result.pending == true
  }

  def 'verifyInteraction sets the message verification error result as pending if it is a V4 pending interaction'() {
    given:
    ProviderInfo provider = new ProviderInfo('Test Provider')
    provider.verificationType = PactVerification.ANNOTATED_METHOD
    ConsumerInfo consumer = new ConsumerInfo(name: 'Test Consumer', pactSource: UnknownPactSource.INSTANCE)
    def failures = [:]
    Interaction interaction = new V4Interaction.AsynchronousMessage('key', 'Test Interaction',
      new MessageContents(OptionalBody.body('{}'.bytes, ContentType.JSON), [:], new MatchingRulesImpl(),
        new Generators()), '1234', [], [:], true)

    when:
    def result = verifier.verifyInteraction(provider, consumer, failures, interaction)

    then:
    result instanceof VerificationResult.Failed
    result.pending == true
  }

  def 'verifyResponseByFactory is able to successfully verify an AsynchronousMessage with MessageAndMetadata'() {
    given:
    verifier.responseFactory = { new MessageAndMetadata('{}'.bytes, [:]) }
    ProviderInfo provider = new ProviderInfo('Test Provider')
    def failures = [:]
    Interaction interaction = new Message('verifyResponseByFactory Test Message', [],
            OptionalBody.body('{}'.bytes, ContentType.JSON), new MatchingRulesImpl(), new Generators(), [:], 'abc123')
    IConsumerInfo consumer = Stub()
    def interactionMessage = 'Test'

    when:
    def result = verifier.verifyResponseByFactory(
            provider,
            consumer,
            interaction,
            interactionMessage,
            failures,
            false
    )

    then:
    result instanceof VerificationResult.Ok
  }

  def 'verifyResponseByFactory is able to successfully verify a SynchronousRequestResponse'() {
    given:
    verifier.responseFactory = { ['statusCode': 200, 'headers': [:], 'contentType': 'application/json', 'data': null] }
    ProviderInfo provider = new ProviderInfo('Test Provider')
    def failures = [:]
    Interaction interaction = new RequestResponseInteraction('Test Interaction',
            [new ProviderState('Test State')], new Request(), new Response(), '12345678')
    IConsumerInfo consumer = Stub()
    def interactionMessage = 'Test'

    when:
    def result = verifier.verifyResponseByFactory(
            provider,
            consumer,
            interaction,
            interactionMessage,
            failures,
            false
    )

    then:
    result instanceof VerificationResult.Ok
  }

  @SuppressWarnings('ThrowRuntimeException')
  def 'verifyResponseByFactory returns an error result if the factory method fails with an exception'() {
    given:
    verifier.responseFactory = { throw new RuntimeException('error') }
    ProviderInfo provider = new ProviderInfo('Test Provider')
    def failures = [:]
    Interaction interaction = new Message('verifyResponseByFactory Test Message', [],
            OptionalBody.empty(), new MatchingRulesImpl(), new Generators(), [:], 'abc123')
    IConsumerInfo consumer = Stub()
    def interactionMessage = 'Test'

    when:
    def result = verifier.verifyResponseByFactory(
            provider,
            consumer,
            interaction,
            interactionMessage,
            failures,
            false
    )

    then:
    result instanceof VerificationResult.Failed
    result.description == 'Verification factory method failed with an exception'
    result.failures.size() == 1
    result.failures['abc123'][0].description == 'Verification factory method failed with an exception'
    result.failures['abc123'][0].e instanceof RuntimeException
  }

  def 'when verifying a V4 Pact, it should load any required plugins'() {
    given:
    ProviderInfo provider = new ProviderInfo('Test Provider')
    ConsumerInfo consumer = new ConsumerInfo(name: 'Test Consumer', pactSource: UnknownPactSource.INSTANCE)
    PactBrokerClient pactBrokerClient = Mock(PactBrokerClient, constructorArgs: [''])
    verifier.pactReader = Stub(PactReader)
    def v4pact = Mock(V4Pact) {
      requiresPlugins() >> true
      pluginData() >> { [new PluginData('a', '1.0', [:]), new PluginData('b', '2.0', [:])] }
    }
    def mockPact = Stub(Pact) {
      getSource() >> new BrokerUrlSource('http://localhost', 'http://pact-broker')
      asV4Pact() >> new Result.Ok(v4pact)
    }

    verifier.pactReader.loadPact(_) >> mockPact
    verifier.pluginManager = Mock(PluginManager)

    when:
    verifier.runVerificationForConsumer([:], provider, consumer, pactBrokerClient)

    then:
    1 * verifier.pluginManager.loadPlugin('a', '1.0') >> new Result.Ok(null)
    1 * verifier.pluginManager.loadPlugin('b', '2.0') >> new Result.Ok(null)
  }

  def 'verifyMessage must pass through any plugin config to the content matcher'() {
    given:
    def failures = [:]
    def pluginConfiguration = new PluginConfiguration(
      [a: new JsonValue.Integer(100)],
      [b: new JsonValue.Integer(100)]
    )
    def config = [
      b: [a: new JsonValue.Integer(100)]
    ]
    def interaction = new V4Interaction.AsynchronousMessage(null, 'verifyMessage Test Message',
      new MessageContents(), null, [], [:], false, config)
    def interactionMessage = 'Test'
    verifier.responseComparer = Mock(IResponseComparison)
    def actual = OptionalBody.body('"Message Data"', ContentType.JSON)
    Function<String, Object> messageFactory = { String desc -> '"Message Data"' }

    when:
    def result = verifier.verifyMessage(messageFactory, interaction as MessageInteraction,
      '', interactionMessage, failures, false, [b: pluginConfiguration])

    then:
    1 * verifier.responseComparer.compareMessage(interaction, actual, null, [b: pluginConfiguration]) >>
      new ComparisonResult()
    result instanceof VerificationResult.Ok
  }
}
