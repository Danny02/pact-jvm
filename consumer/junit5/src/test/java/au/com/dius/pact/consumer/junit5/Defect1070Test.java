package au.com.dius.pact.consumer.junit5;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit.MockServerConfig;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import static au.com.dius.pact.consumer.dsl.LambdaDsl.newJsonArrayMinLike;
import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "ApiProvider", pactVersion = PactSpecVersion.V3)
@MockServerConfig(port = "1234")
public class Defect1070Test {
  private Map<String, String> headers = MapUtils.putAll(new HashMap<>(), new String[] {
    "Content-Type", "application/json"
  });

  @BeforeEach
  public void setUp(MockServer mockServer) {
    assertThat(mockServer, is(notNullValue()));
  }

  @Pact(consumer = "ApiConsumer")
  public RequestResponsePact articles(PactDslWithProvider builder) {
    return builder
            .given("This is a test")
            .uponReceiving("GET request to retrieve default values")
                    .matchPath(format("/api/test/%s", "\\d{1,8}"))
                    .method("GET")
                    .willRespondWith()
                    .status(200)
                    .headers(headers)
                    .body(newJsonArrayMinLike(1, values -> values.object(value -> {
                              value.numberType("id", 32432);
                              value.stringType("name", "testId254");
                              value.numberType("size", 1445211);
                            }
                    )).build())
                    .toPact();
  }

  @Test
  @PactTestFor
  void testApi(MockServer mockServer) throws IOException {
    ClassicHttpResponse httpResponse = (ClassicHttpResponse) Request.get(mockServer.getUrl() + "/api/test/1234").execute().returnResponse();
    assertThat(httpResponse.getCode(), is(equalTo(200)));
    assertThat(IOUtils.toString(httpResponse.getEntity().getContent()),
      is(equalTo("[{\"id\":32432,\"name\":\"testId254\",\"size\":1445211}]")));
  }
}
