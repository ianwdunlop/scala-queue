package io.mdcatapult.klein.queue

import com.spingo.op_rabbit.properties.{CorrelationId, DeliveryModePersistence, MessageProperty}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class QueueSpec  extends AnyFlatSpec with Matchers {

  "Queue message properties" must "contain DeliveryModePersistence(true)" in {
    val properties: Seq[MessageProperty] = Seq[MessageProperty]()
    val propertiesAfterCheck = Queue.ensureDeliveryModePersistence(properties)
    assert(propertiesAfterCheck.contains(DeliveryModePersistence(true)))
    assert(propertiesAfterCheck.length == 1)
  }

  "Queue message properties" must "not contain DeliveryModePersistence(false)" in {
    val properties: Seq[MessageProperty] = Seq[MessageProperty](DeliveryModePersistence(false))
    val propertiesAfterCheck = Queue.ensureDeliveryModePersistence(properties)
    assert(!propertiesAfterCheck.contains(DeliveryModePersistence(false)))
    assert(propertiesAfterCheck.contains(DeliveryModePersistence(true)))
    assert(propertiesAfterCheck.length == 1)
  }

  "When checking for message persistence all the other message properties" must "be kept" in  {
    val properties: Seq[MessageProperty] = Seq[MessageProperty](CorrelationId("1"), DeliveryModePersistence(false))
    val propertiesAfterCheck = Queue.ensureDeliveryModePersistence(properties)
    assert(!propertiesAfterCheck.contains(DeliveryModePersistence(false)))
    assert(propertiesAfterCheck.contains(DeliveryModePersistence(true)))
    assert(propertiesAfterCheck.contains(CorrelationId("1")))
    assert(propertiesAfterCheck.length == 2)
  }

}
