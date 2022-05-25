/**
 * Copyright 2015 Yahoo Inc. Licensed under the Apache License, Version 2.0
 * See accompanying LICENSE file.
 */

package controllers

import features.{ApplicationFeatures, KMTopicManagerFeature}
import kafka.manager.ApiError
import kafka.manager.features.ClusterFeatures
import kafka.manager.utils.ZkUtils
import models.FollowLink
import models.form._
import models.navigation.Menus
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.i18n.I18nSupport
import play.api.mvc._
import scalaz.-\/

import scala.concurrent.{ExecutionContext, Future}

/**
 * @author cvcal
 */
class Consumer (val cc: ControllerComponents, val kafkaManagerContext: KafkaManagerContext)
               (implicit af: ApplicationFeatures, menus: Menus, ec: ExecutionContext) extends AbstractController(cc) with I18nSupport {

  private[this] val kafkaManager = kafkaManagerContext.getKafkaManager

  def consumers(cluster: String) = Action.async { implicit request: RequestHeader =>
    kafkaManager.getConsumerListExtended(cluster).map { errorOrConsumerList =>
      Ok(views.html.consumer.consumerList(cluster, errorOrConsumerList)).withHeaders("X-Frame-Options" -> "SAMEORIGIN")
    }
  }

  def consumer(cluster: String, consumerGroup: String, consumerType: String) = Action.async { implicit request: RequestHeader =>
    kafkaManager.getConsumerIdentity(cluster,consumerGroup, consumerType).map { errorOrConsumerIdentity =>
      Ok(views.html.consumer.consumerView(cluster,consumerGroup,errorOrConsumerIdentity)).withHeaders("X-Frame-Options" -> "SAMEORIGIN")
    }
  }

  def consumerAndTopic(cluster: String, consumerGroup: String, topic: String, consumerType: String) = Action.async { implicit request: RequestHeader =>
    kafkaManager.getConsumedTopicState(cluster,consumerGroup,topic, consumerType).map { errorOrConsumedTopicState =>
      Ok(views.html.consumer.consumedTopicView(cluster,consumerGroup,consumerType,topic,errorOrConsumedTopicState)).withHeaders("X-Frame-Options" -> "SAMEORIGIN")
    }
  }

  //ken add
  def delConsumerGroup(cluster: String, consumerGroup: String, topic: String, consumerType: String) = Action.async { implicit request: RequestHeader =>
    kafkaManager.getConsumedTopicState(cluster,consumerGroup,topic, consumerType).map { errorOrConsumedTopicState =>
      Ok(views.html.consumer.consumerGroupDelConfirm(cluster,consumerGroup,topic,errorOrConsumedTopicState)).withHeaders("X-Frame-Options" -> "SAMEORIGIN")
    }
  }

  def resetConsumerOffset(cluster: String, consumerGroup: String,topic: String, consumerType: String) = Action.async {implicit request =>
    featureGate(KMTopicManagerFeature) {
      val errorOrFormFuture = kafkaManager.getConsumedTopicState(cluster, consumerGroup, topic, consumerType).flatMap { errorOrTopicIdentity =>
        errorOrTopicIdentity.fold(e => Future.successful(-\/(e)), { ConsumedTopicState =>
          kafkaManager.getBrokerList(cluster).map { errorOrBrokerList =>
            errorOrBrokerList.map { bl =>
              val test = ConsumedTopicState.partitionLatestOffsets.toString()
              (defaultResetLatestOffsetForm.fill(ResetLatestOffset(test)),
                bl.clusterContext)
            }
          }
        })
      }
      errorOrFormFuture.map { errorOrForm =>
        Ok(views.html.consumer.consumerRestConsumerOffsetConfirm(cluster, consumerGroup, topic, consumerType, errorOrForm)).withHeaders("X-Frame-Options" -> "SAMEORIGIN")
      }
    }
  }

  def consumerOffsetUpdateByPartition(cluster: String, consumerGroup: String, topic: String, consumerType: String, partitionNum:Int) = Action.async { implicit request =>
    featureGate(KMTopicManagerFeature) {
      val errorOrFormFuture = kafkaManager.getConsumedTopicState(cluster, consumerGroup, topic, consumerType).flatMap { errorOrTopicIdentity =>
        errorOrTopicIdentity.fold(e => Future.successful(-\/(e)), { ConsumedTopicState =>
          kafkaManager.getBrokerList(cluster).map { errorOrBrokerList =>
            errorOrBrokerList.map { bl =>
              (defaultUpdateOffsetForm.fill(UpdateConsumerOffsets(ConsumedTopicState.partitionLatestOffsets(partitionNum).toLong)),
                bl.clusterContext)
            }
          }
        })
      }
      errorOrFormFuture.map { errorOrForm =>
        Ok(views.html.consumer.consumerOffsetUpdate(cluster, consumerGroup, topic, consumerType, partitionNum, errorOrForm)).withHeaders("X-Frame-Options" -> "SAMEORIGIN")
      }
    }
  }

  def handleDelConsumerGroup(clusterName: String, consumerGroup: String) = Action.async { implicit request =>
    featureGate(KMTopicManagerFeature) {
      defaultDeleteForm.bindFromRequest.fold(
        formWithErrors => Future.successful(
          BadRequest(views.html.consumer.confirmPageView(
            clusterName,
            consumerGroup,
            -\/(ApiError(formWithErrors.error("topic").map(_.toString).getOrElse("Unknown error deleting consumer!"))),
            None))
        ),
        deleteTopic => {
          kafkaManager.delConsumerGroup(clusterName, consumerGroup).map { errorOrSuccess =>
            implicit val clusterFeatures = errorOrSuccess.toOption.map(_.clusterFeatures).getOrElse(ClusterFeatures.default)
            Ok(views.html.common.resultOfCommand(
              views.html.navigation.clusterMenu(clusterName, "Consumer", "Consumer View", menus.clusterMenus(clusterName)),
              models.navigation.BreadCrumbs.withNamedViewAndClusterAndConsumerWithType("Consumer View",clusterName,consumerGroup,"zk","Delete Consumer"),
              errorOrSuccess,
              "Delete Consumer",
              FollowLink("Go to Consumer list.", routes.Consumer.consumers(clusterName).toString()),
              FollowLink("Try again.", routes.Consumer.consumers(clusterName).toString())
            )).withHeaders("X-Frame-Options" -> "SAMEORIGIN")
          }
        }
      )
    }
  }

  /*def handleResetConsumerOffset(clusterName: String, consumerGroup: String) = Action.async { implicit request =>
    featureGate(KMTopicManagerFeature) {
      defaultDeleteForm.bindFromRequest.fold(
        formWithErrors => Future.successful(
          BadRequest(views.html.consumer.confirmPageView(
            clusterName,
            consumerGroup,
            -\/(ApiError(formWithErrors.error("topic").map(_.toString).getOrElse("Unknown error reseting consumer!"))),
            None))
        ),
        deleteTopic => {
          kafkaManager.resetConsumerOffset(clusterName, consumerGroup).map { errorOrSuccess =>
            implicit val clusterFeatures = errorOrSuccess.toOption.map(_.clusterFeatures).getOrElse(ClusterFeatures.default)
            Ok(views.html.common.resultOfCommand(
              views.html.navigation.clusterMenu(clusterName, "Consumer", "Consumer View", menus.clusterMenus(clusterName)),
              models.navigation.BreadCrumbs.withNamedViewAndClusterAndConsumerWithType("Consumer View",clusterName,consumerGroup,"zk","Reset Consumer"),
              errorOrSuccess,
              "Reset Consumer",
              FollowLink("Go to Consumer list.", routes.Consumer.consumers(clusterName).toString()),
              FollowLink("Try again.", routes.Consumer.consumers(clusterName).toString())
            ))
          }
        }
      )
    }
  }*/

  def handleResetConsumerOffset(clusterName: String, consumerGroup: String, topic: String) = Action.async { implicit request =>
    featureGate(KMTopicManagerFeature) {
      defaultResetLatestOffsetForm.bindFromRequest.fold(
        formWithErrors => {
          kafkaManager.getClusterContext(clusterName).map { clusterContext =>
            BadRequest(views.html.consumer.consumerRestConsumerOffsetConfirm(clusterName, consumerGroup, topic, "zk",clusterContext.map(c => (formWithErrors, c))))
          }.recover {
            case t =>
              implicit val clusterFeatures = ClusterFeatures.default
              Ok(views.html.common.resultOfCommand(
                views.html.navigation.clusterMenu(clusterName, "Topic", "Topic View", menus.clusterMenus(clusterName)),
                models.navigation.BreadCrumbs.withNamedViewAndClusterAndTopic("Topic View", clusterName, topic, "Add Partitions"),
                -\/(ApiError(s"Unknown error : ${t.getMessage}")),
                "Add Partitions",
                FollowLink("Try again.", routes.Topic.addPartitions(clusterName, topic).toString()),
                FollowLink("Try again.", routes.Topic.addPartitions(clusterName, topic).toString())
              )).withHeaders("X-Frame-Options" -> "SAMEORIGIN")
          }
        },
        resetLatestOffsetForm => {
          kafkaManager.resetConsumerOffset(clusterName, consumerGroup,topic,resetLatestOffsetForm.offsets.toString()).map { errorOrSuccess =>
            implicit val clusterFeatures = errorOrSuccess.toOption.map(_.clusterFeatures).getOrElse(ClusterFeatures.default)
            Ok(views.html.common.resultOfCommand(
              views.html.navigation.clusterMenu(clusterName, "Topic", "Topic View", menus.clusterMenus(clusterName)),
              models.navigation.BreadCrumbs.withNamedViewAndClusterAndConsumerWithType("Consumer View",clusterName,consumerGroup,"zk",topic),
              errorOrSuccess,
              "Update Offset",
              FollowLink("Go to consumerAndTopic view.", routes.Consumer.consumerAndTopic(clusterName, consumerGroup,topic,"zk").toString()),
              FollowLink("Try again.", routes.Consumer.consumerAndTopic(clusterName, consumerGroup, topic, "zk").toString())
            )).withHeaders("X-Frame-Options" -> "SAMEORIGIN")
          }
        }
      )
    }
  }

  def handleConsumerOffsetUpdateByPartition(clusterName: String, consumerGroup: String, topic: String, partitionNum:Int) = Action.async { implicit request =>
    featureGate(KMTopicManagerFeature) {
      defaultUpdateOffsetForm.bindFromRequest.fold(
        formWithErrors => {
          kafkaManager.getClusterContext(clusterName).map { clusterContext =>
            BadRequest(views.html.consumer.consumerOffsetUpdate(clusterName, consumerGroup, topic, "zk", partitionNum,clusterContext.map(c => (formWithErrors, c))))
          }.recover {
            case t =>
              implicit val clusterFeatures = ClusterFeatures.default
              Ok(views.html.common.resultOfCommand(
                views.html.navigation.clusterMenu(clusterName, "Topic", "Topic View", menus.clusterMenus(clusterName)),
                models.navigation.BreadCrumbs.withNamedViewAndClusterAndTopic("Topic View", clusterName, topic, "Add Partitions"),
                -\/(ApiError(s"Unknown error : ${t.getMessage}")),
                "Add Partitions",
                FollowLink("Try again.", routes.Topic.addPartitions(clusterName, topic).toString()),
                FollowLink("Try again.", routes.Topic.addPartitions(clusterName, topic).toString())
              )).withHeaders("X-Frame-Options" -> "SAMEORIGIN")
          }
        },
        updateOffsetForm => {
          val offsetPath = "%s/%s/%s/%s/%s".format(ZkUtils.ConsumersPath, consumerGroup, "offsets", topic, partitionNum)
          kafkaManager.updateOffsets(clusterName, offsetPath, updateOffsetForm.offsets.toLong).map { errorOrSuccess =>
            //          kafkaManager.addTopicPartitions(clusterName, addTopicPartitions.topic, addTopicPartitions.brokers.filter(_.selected).map(_.id), addTopicPartitions.partitions, addTopicPartitions.readVersion).map { errorOrSuccess =>
            implicit val clusterFeatures = errorOrSuccess.toOption.map(_.clusterFeatures).getOrElse(ClusterFeatures.default)
            Ok(views.html.common.resultOfCommand(
              views.html.navigation.clusterMenu(clusterName, "Topic", "Topic View", menus.clusterMenus(clusterName)),
              models.navigation.BreadCrumbs.withNamedViewAndClusterAndConsumerWithType("Consumer View",clusterName,consumerGroup,"zk",topic),
              errorOrSuccess,
              "Update Offset",
              FollowLink("Go to consumerAndTopic view.", routes.Consumer.consumerAndTopic(clusterName, consumerGroup,topic,"zk").toString()),
              FollowLink("Try again.", routes.Consumer.consumerOffsetUpdateByPartition(clusterName, consumerGroup, topic, "zk", partitionNum).toString())
            )).withHeaders("X-Frame-Options" -> "SAMEORIGIN")
          }
        }
      )
    }
  }

  val defaultResetLatestOffsetForm = Form(
    mapping(
      //"consumerGroup" -> nonEmptyText,
      "offsets" -> nonEmptyText.verifying()
    )(ResetLatestOffset.apply)(ResetLatestOffset.unapply)
  )

  val defaultUpdateOffsetForm = Form(
    mapping(
      //"consumerGroup" -> nonEmptyText,
      "offsets" -> longNumber
    )(UpdateConsumerOffsets.apply)(UpdateConsumerOffsets.unapply)
  )

  val defaultDeleteForm = Form(
    mapping(
      "topic" -> nonEmptyText.verifying(maxLength(250))
    )(DeleteTopic.apply)(DeleteTopic.unapply)
  )
}
