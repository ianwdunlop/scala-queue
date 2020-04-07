package io.mdcatapult.klein.queue

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ConverterSpec extends AnyFlatSpec with Matchers {

  "Converter.toStrings" should "include the message of a nested exception" in {
    val x = new Exception("top exception", new RuntimeException("nested exception"))
    val stackLines = Converter.toStrings(x)

    stackLines should contain inOrder ("Exception Caused By:", "nested exception")
  }
}
