package io.hydrosphere.mist.worker

import java.util.concurrent.Executors.newFixedThreadPool

import akka.cluster.ClusterEvent._
import io.hydrosphere.mist.Messages.{StartStreamingJob, WorkerDidStart}
import io.hydrosphere.mist.contexts.ContextBuilder
import io.hydrosphere.mist.jobs.JobConfiguration
import akka.cluster.Cluster
import akka.actor.{Actor, ActorLogging, Props}
import io.hydrosphere.mist.{Constants, MistConfig}

import scala.concurrent.{ExecutionContext}
import scala.util.{Random}

class StreamingNode(path:String, className: String, name: String, externalId: String) extends Actor with ActorLogging{

  val executionContext = ExecutionContext.fromExecutorService(newFixedThreadPool(MistConfig.Settings.threadNumber))

  private val cluster = Cluster(context.system)

  private val serverAddress = Random.shuffle[String, List](MistConfig.Akka.Worker.serverList).head + "/user/" + Constants.Actors.workerManagerName
  private val serverActor = cluster.system.actorSelection(serverAddress)

  val nodeAddress = cluster.selfAddress

  lazy val contextWrapper = ContextBuilder.namedSparkContext(name)

  val jobConfiguration = new JobConfiguration(path, className, name, Map().empty, Option(externalId))

  override def preStart(): Unit = {
    serverActor ! WorkerDidStart("StreamingJobStarter", cluster.selfAddress.toString)
    cluster.subscribe(self, InitialStateAsEvents, classOf[MemberEvent], classOf[UnreachableMember])
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
  }

  override def receive: Receive = {
    case MemberUp(member) =>
      if (member.address == cluster.selfAddress) {
        serverActor ! new StartStreamingJob(jobConfiguration)
        cluster.system.shutdown()
      }

    case MemberExited(member) =>
      if (member.address == cluster.selfAddress) {
        cluster.system.shutdown()
      }

    case MemberRemoved(member, prevStatus) =>
      if (member.address == cluster.selfAddress) {
        sys.exit(0)
      }
  }
}

object StreamingNode {
  def props(name: String): Props = Props(classOf[StreamingNode], name)
}