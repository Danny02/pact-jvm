package au.com.dius.pact.provider.spring.spring6

import au.com.dius.pact.provider.junit5.PactVerificationContext
import au.com.dius.pact.provider.junitsupport.IgnoreNoPactsToVerify
import au.com.dius.pact.provider.junitsupport.Provider
import au.com.dius.pact.provider.junitsupport.loader.PactBroker
import org.junit.jupiter.api.TestTemplate
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit.jupiter.SpringExtension

@SpringBootApplication
open class TestApplication

@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Provider("Animal Profile Service")
@PactBroker
@IgnoreNoPactsToVerify(ignoreIoErrors = "true")
internal class PactVerificationSpringProviderTest {
  @TestTemplate
  @ExtendWith(PactVerificationSpring6Provider::class)
  fun pactVerificationTestTemplate(context: PactVerificationContext?) {
    context?.verifyInteraction()
  }
}
