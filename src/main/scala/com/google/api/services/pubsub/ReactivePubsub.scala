package com.google.api.services.pubsub

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.stream.scaladsl._
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.services.pubsub.PublishResponse.MessageId
import com.google.api.services.pubsub.model.ListSubscriptionsResponse
import utils.PortableConfiguration

import scala.concurrent._
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

class ReactivePubsub(val javaPubsub: Pubsub) extends PublisherTrait {

  import ReactivePubsub.{fqrn, ignoreAlreadyExists}

  def subscribe(project: String, subscription: String, pullRequest: PullRequest)(implicit
      blockingExecutionContext: ExecutionContext,
      s: ActorSystem,
      log: LoggingAdapter
  ): Source[Seq[ReceivedMessage], NotUsed] =
    asyncRequestToSource(() => pullAsync(project, subscription, pullRequest))

  def subscribeConcat(project: String, subscription: String, pullRequest: PullRequest)(implicit
      blockingExecutionContext: ExecutionContext,
      s: ActorSystem,
      log: LoggingAdapter
  ): Source[ReceivedMessage, NotUsed] =
    asyncRequestToSource(() => pullAsync(project, subscription, pullRequest)).mapConcat { seq => seq }

  def subscribeFromStream(project: String, subscription: String, pullRequest: PullRequest)(implicit
      ec: ExecutionContext,
      s: ActorSystem,
      log: LoggingAdapter
  ): Source[Future[List[ReceivedMessage]], NotUsed] = {

    def iter()(implicit log: LoggingAdapter): LazyList[Future[List[ReceivedMessage]]] =
      pullAsync(project, subscription, pullRequest) #:: iter()

    val res = iter().iterator
    Source.fromIterator(() => res)
  }

  // allocate additional logical thread for execution context.
  // use a thread pool for these blocking requests.
  def pullAsync(project: String, subscription: String, pullRequest: PullRequest)(implicit
      ec: ExecutionContext,
      s: ActorSystem,
      log: LoggingAdapter
  ): Future[List[ReceivedMessage]] =
    Future {
      blocking {
        pull(project, subscription, pullRequest)
      }
    }

  def pullAsyncWithRetries(retries: Int)(project: String, subscription: String, pullRequest: PullRequest)(implicit
      ec: ExecutionContext,
      system: ActorSystem,
      log: LoggingAdapter
  ) =
    retry({ () => pullAsync(project, subscription, pullRequest) }, retries)

  def pullSync(project: String, subscription: String, pullRequest: PullRequest): Try[List[ReceivedMessage]] = Try {
    PullResponse(
      javaPubsub
        .projects()
        .subscriptions
        .pull(fqrn("subscriptions", project, subscription), pullRequest)
        .execute()
    )
  }

  def pullSyncWithRetries(numRetries: Int)(project: String, subscription: String, pullRequest: PullRequest) = {

    def loop(count: Int): Try[List[ReceivedMessage]] = {
      val result = pullSync(project, subscription, pullRequest);
      result match {
        case Failure(_) if count > 0 => loop(count - 1)
        case _                       => result
      }
    }

    loop(numRetries)
  }

  private def pull(project: String, subscription: String, pullRequest: PullRequest)(implicit
      log: LoggingAdapter
  ): List[ReceivedMessage] =
    try {
      PullResponse(
        javaPubsub
          .projects()
          .subscriptions
          .pull(fqrn("subscriptions", project, subscription), pullRequest)
          .execute()
      )
    } catch {
      case e: Throwable => {
        log.error("error occurred during pull request, message: {}", e.getMessage)
        List.empty[ReceivedMessage]
      }
    }

  def getSubscriptions(project: String, subscription: String) = {
    type Req = Pubsub#Projects#Subscriptions#List

    val request: Req = javaPubsub.projects().subscriptions().list(s"projects/${project}")

    def iter(r: Req): LazyList[ListSubscriptionsResponse] = {
      val result = r.execute()
      if (result.getSubscriptions != null) {
        val nextToken = r.getPageToken
        if (nextToken != null)
          result #:: iter(r.setPageToken(nextToken))
        else
          LazyList(result)
      } else LazyList.empty
    }

    iter(request)
  }

  def createSubscription(project: String, subscription: String, topic: String, ackDeadlineSeconds: Int) = Try {
    javaPubsub
      .projects()
      .subscriptions()
      .create(
        fqrn("subscriptions", project, subscription),
        new model.Subscription()
          .setAckDeadlineSeconds(ackDeadlineSeconds)
          .setTopic(fqrn("topics", project, topic))
      )
      .execute()
  }

  def createSubscriptionIgnore(project: String, subscription: String, topic: String, ackDeadlineSeconds: Int) =
    createSubscription(project, subscription, topic, ackDeadlineSeconds) recover ignoreAlreadyExists

  def ack(project: String, subscription: String)(ackIds: List[String])(implicit ec: ExecutionContext, s: ActorSystem) =
    Future {
      blocking {
        javaPubsub
          .projects()
          .subscriptions()
          .acknowledge(
            fqrn("subscriptions", project, subscription),
            new model.AcknowledgeRequest()
              .setAckIds(ackIds.asJava)
          )
          .execute()
      }
    }

  def createTopic(project: String, topic: String) =
    Try(javaPubsub.projects().topics().create(fqrn("topics", project, topic), new model.Topic()).execute())

  def createTopicIgnore(project: String, topic: String) = createTopic(project, topic) recover ignoreAlreadyExists

  def publish[T](project: String, topic: String)(messages: Seq[T], converter: T => PubsubMessage)(implicit
      ec: ExecutionContext,
      log: LoggingAdapter
  ): Future[List[MessageId]] =
    Future(messages map converter) flatMap (publish(project, topic, _))

  def publish(project: String, topic: String, messages: Seq[PubsubMessage])(implicit
      ec: ExecutionContext,
      log: LoggingAdapter
  ): Future[List[MessageId]] =
    PublishRequest(messages) match {
      case Success(m) =>
        Future {
          blocking {
            PublishResponse(javaPubsub.projects().topics().publish(fqrn("topics", project, topic), m).execute())
          }
        } recover { case e: GoogleJsonResponseException => log.error(e, s"the request was ${m.toString}"); throw e }
      case Failure(e) => Future.failed(e)
    }

}

object ReactivePubsub {

  def apply(appName: String, credential: GoogleCredential): ReactivePubsub = apply(appName, credential, None)

  def apply(appName: String, credential: GoogleCredential, url: Option[String]): ReactivePubsub = {

    val p: Pubsub.Builder = PortableConfiguration
      .createPubsubClient(credential)
      .setApplicationName(appName)

    url match {
      case Some(u) => p.setRootUrl(u);
      case _       => ()
    }

    new ReactivePubsub(p.build())
  }

  def fqrn(resourceType: String, project: String, resource: String) = s"projects/${project}/${resourceType}/${resource}"

  val ignoreAlreadyExists: PartialFunction[Throwable, _] = {
    case e: GoogleJsonResponseException if e.getDetails.getCode == 409 => ()
  }

}
