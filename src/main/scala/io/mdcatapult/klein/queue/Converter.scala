package io.mdcatapult.klein.queue

object Converter {

  def toStrings(exception: Throwable): List[String] = {
    val trace = exception.getStackTrace.map(_.toString).toList

    Option(exception.getCause) match {
      case Some(cause) => trace ++ List("Exception Caused By:", cause.getMessage) ++ toStrings(cause)
      case None => trace
    }
  }
}
