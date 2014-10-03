package au.com.dius.pact.matchers

import au.com.dius.pact.model.Request
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import au.com.dius.pact.model.JsonDiff.DiffConfig

@RunWith(classOf[JUnitRunner])
class MatchersTest extends Specification {

  "matchers defined" should {

    "should be false when there are no matchers" in {
      Matchers.matcherDefined("", None) must beFalse
    }

    "should be false when the path does not have a matcher entry" in {
      Matchers.matcherDefined("$.body.something", Some(Map())) must beFalse
    }

    "should be true when the path does have a matcher entry" in {
      Matchers.matcherDefined("$.body.something", Some(Map("$.body.something" -> None))) must beTrue
    }

  }

  "regex matcher" should {

    "match using the provided regex" in {
      val expected = Request("get", "/", None, None, Some("{\"value\": \"Harry\"}"), Some(Map("$.body.value" -> Map("regex" -> "Ha[a-z]*"))))
      val actual = Request("get", "/", None, None, Some("{\"value\": \"Harry\"}"), None)
      new JsonBodyMatcher().matchBody(expected, actual, DiffConfig()) must beEmpty
    }

  }

}