/**
 * Copyright 2015 Yahoo Inc. Licensed under the Apache License, Version 2.0
 * See accompanying LICENSE file.
 */

package models.form

/**
 * @author lxj
 */

sealed trait ConsumerOperation


//case class UpdateConsumerOffsets(topic: String, brokers: Seq[BrokerSelect], partitions: Int, readVersion: Int) extends ConsumerOperation
//case class UpdateConsumerOffsets(consumerGroup: String, offsets: Long) extends ConsumerOperation
case class UpdateConsumerOffsets(offsets: Long) extends ConsumerOperation
case class ResetLatestOffset(offsets: String) extends ConsumerOperation

