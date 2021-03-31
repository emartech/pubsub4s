package com.google.api.services.pubsub

import com.google.api.services.pubsub.model.{PublishRequest => javaPublishRequest}

import scala.jdk.CollectionConverters._
import scala.util._

object PublishRequest {

  def apply(messages: Seq[PubsubMessage]): Try[javaPublishRequest] = {
    val payload = sequence(messages map (x => PubsubMessage.asJava(x)))
    payload match {
      case Success(pay) =>
        Success(new javaPublishRequest().setMessages(pay.asJava))
      case _ => Failure(InvalidPubsubMesage(""))
    }
  }

  private def sequence[T](tries: Seq[Try[T]]): Try[Seq[T]] = Try(tries.map(_.get))
}
