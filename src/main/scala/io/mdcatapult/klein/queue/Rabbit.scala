package io.mdcatapult.klein.queue

import com.spingo.op_rabbit.RabbitControl
import com.spingo.op_rabbit.RabbitControl.Run

class Rabbit extends RabbitControl {

  /**
    * Check the liveness of the connection to the queue.
    * Op-rabbit does this already so just return true if running.
    *
    * @return
    */
  def checkLiveness(): Boolean = {
    running == Run
  }
}
