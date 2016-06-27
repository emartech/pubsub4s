package com.google.api.services.pubsub

import akka.actor.ActorSystem
import akka.NotUsed
import akka.stream.scaladsl._
import com.google.api.services.pubsub.model.ListSubscriptionsResponse
import utils.PortableConfiguration

import scala.concurrent._
import scala.util.{Failure, Try, Success}
import scala.language.implicitConversions._
import scala.collection.JavaConverters._

class ReactivePubsub(val javaPubsub: Pubsub) extends PublisherTrait {
  import ReactivePubsub.{fqrn, ignoreAlreadyExists}

  def subscribe
      (project: String, subscription: String, pullRequest: PullRequest)
      (implicit ec: ExecutionContext, s: ActorSystem): Source[Seq[ReceivedMessage], NotUsed] =
    asyncRequestToSource(() => pullAsync(project, subscription, pullRequest))

  def subscribeConcat
      (project: String, subscription: String, pullRequest: PullRequest)
      (implicit ec: ExecutionContext, s: ActorSystem): Source[ReceivedMessage, NotUsed] =
    asyncRequestToSource(() => pullAsync(project, subscription, pullRequest)).mapConcat{ seq => seq }

  def subscribeFromStream
      (project: String, subscription: String, pullRequest: PullRequest)
      (implicit ec: ExecutionContext, s: ActorSystem): Source[Future[List[ReceivedMessage]], NotUsed] = {

    def iter(): Stream[Future[List[ReceivedMessage]]] =
      pullAsync(project, subscription, pullRequest) #:: iter()

    val res = iter().toIterator
    Source.fromIterator(() => res)
  }

  // allocate additional logical thread for execution context.
  // use a thread pool for these blocking requests.
  def pullAsync
      (project: String, subscription: String, pullRequest: PullRequest)
      (implicit ec: ExecutionContext, s: ActorSystem): Future[List[ReceivedMessage]] =
    Future {
      blocking {
        javaPubsub.projects().subscriptions()
          .pull(fqrn("subscriptions", project, subscription), pullRequest).execute()
      }
    } map (PullResponse(_))

  def pullAsyncWithRetries
      (retries: Int)(project: String, subscription: String, pullRequest: PullRequest)
      (implicit ec: ExecutionContext, system: ActorSystem) =
    retry({() => pullAsync(project, subscription, pullRequest)}, retries)

  def pullSync(subscription: String, maxMessages: Int, returnImmediately: Boolean): Try[List[ReceivedMessage]] = Try {
    PullResponse(javaPubsub.projects().subscriptions.pull(subscription, PullRequest(maxMessages, returnImmediately)).execute())
  }

  def pullSyncWithRetries(numRetries: Int) (subscription: String, maxMessages: Int, returnImmediately: Boolean) = {

    def loop(count: Int): Try[List[ReceivedMessage]] = {
      val result = pullSync(subscription, maxMessages, returnImmediately); result match {
        case Failure(_) if count>0 => loop(count-1)
        case _ => result
      }
    }

    loop(numRetries)
  }

  def getSubscriptions(project: String, subscription: String) = {
    type Req = Pubsub#Projects#Subscriptions#List

    val request: Req = javaPubsub.projects().subscriptions().list(s"projects/${project}")

    def iter(r: Req): Stream[ListSubscriptionsResponse] = {
      val result = r.execute()
      if (result.getSubscriptions != null) {
        val nextToken = r.getPageToken
        if (nextToken != null)
          result #:: iter(r.setPageToken(nextToken))
        else
          Stream(result)
      } else Stream.empty
    }

    iter(request)
  }

  def createSubscription(project: String, subscription: String, topic: String, ackDeadlineSeconds: Int) = Try {
    javaPubsub.projects().subscriptions()
      .create(
        fqrn("subscriptions", project, subscription),
        new model.Subscription()
          .setAckDeadlineSeconds(ackDeadlineSeconds)
          .setTopic(fqrn("topics", project, topic))
      ).execute()
  }

  def createSubscriptionIgnore(project: String, subscription: String, topic: String, ackDeadlineSeconds: Int) =
    createSubscription(project, subscription, topic, ackDeadlineSeconds) recover ignoreAlreadyExists

  def ack
      (project: String, subscription: String) (ackIds: List[String])
      (implicit ec: ExecutionContext, s: ActorSystem) =
    Future {
      blocking {
        javaPubsub.projects().subscriptions()
          .acknowledge(
            fqrn("subscriptions", project, subscription),
            new model.AcknowledgeRequest()
              .setAckIds(ackIds.asJava)
          ).execute()
      }
    }

  def createTopic(project: String, topic: String) =
    Try(javaPubsub.projects().topics().create(fqrn("topics", project, topic), new model.Topic()).execute())

  def createTopicIgnore(project: String, topic: String) = createTopic(project, topic) recover ignoreAlreadyExists

  def publish
      (project: String, topic: String)(messages: Seq[PubsubMessage])
      (implicit ec: ExecutionContext, s: ActorSystem) =
    PublishRequest(messages) match {
      case Success(m) =>
        Future(javaPubsub.projects().topics().publish(fqrn("topics", project, topic), m).execute())
      case Failure(e) => Future.failed(e)
    }

}

object ReactivePubsub {

  def apply(appName:String) (implicit system: ActorSystem, context: ExecutionContext): ReactivePubsub =
    apply(appName, None)(system, context)

  def apply
      (appName: String, url: Option[String])
      (implicit system: ActorSystem, context: ExecutionContext): ReactivePubsub = {

    val p: Pubsub.Builder = PortableConfiguration.createPubsubClient()
      .setApplicationName(appName)

    url match { case Some(u) => p.setRootUrl(u); case _ => () }

    new ReactivePubsub(p.build())
  }

  def fqrn(resourceType:String, project:String, resource:String) = s"projects/${project}/${resourceType}/${resource}"

  val ignoreAlreadyExists : PartialFunction[Throwable,_] =
    { case er: Throwable if er.getMessage.take(3) == "409" => () }

}
