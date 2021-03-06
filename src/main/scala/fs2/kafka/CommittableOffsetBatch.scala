/*
 * Copyright 2018 OVO Energy Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fs2.kafka

import cats.syntax.foldable._
import cats.syntax.show._
import cats.{Applicative, Foldable, Show}
import fs2.kafka.internal.instances._
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition

sealed abstract class CommittableOffsetBatch[F[_]] {
  def updated(that: CommittableOffset[F]): CommittableOffsetBatch[F]

  def offsets: Map[TopicPartition, OffsetAndMetadata]

  def commit: F[Unit]
}

object CommittableOffsetBatch {
  private[kafka] def apply[F[_]](
    offsets: Map[TopicPartition, OffsetAndMetadata],
    commit: Map[TopicPartition, OffsetAndMetadata] => F[Unit]
  ): CommittableOffsetBatch[F] = {
    val _offsets = offsets
    val _commit = commit

    new CommittableOffsetBatch[F] {
      override def updated(that: CommittableOffset[F]): CommittableOffsetBatch[F] =
        CommittableOffsetBatch(this.offsets ++ that.offsets, _commit)

      override val offsets: Map[TopicPartition, OffsetAndMetadata] =
        _offsets

      override def commit: F[Unit] =
        _commit(offsets)

      override def toString: String =
        Show[CommittableOffsetBatch[F]].show(this)
    }
  }

  def fromFoldable[F[_], G[_]](offsets: G[CommittableOffset[F]])(
    implicit F: Applicative[F],
    G: Foldable[G]
  ): CommittableOffsetBatch[F] =
    offsets.foldLeft(empty[F])(_ updated _)

  def empty[F[_]](implicit F: Applicative[F]): CommittableOffsetBatch[F] =
    new CommittableOffsetBatch[F] {
      override def updated(that: CommittableOffset[F]): CommittableOffsetBatch[F] =
        that.batch

      override val offsets: Map[TopicPartition, OffsetAndMetadata] =
        Map.empty

      override val commit: F[Unit] =
        F.unit

      override def toString: String =
        Show[CommittableOffsetBatch[F]].show(this)
    }

  implicit def committableOffsetBatchShow[F[_]]: Show[CommittableOffsetBatch[F]] =
    Show.show { cob =>
      if (cob.offsets.isEmpty) "CommittableOffsetBatch(<empty>)"
      else {
        val builder = new StringBuilder("CommittableOffsetBatch(")
        val offsets = cob.offsets.toList.sorted
        var first = true

        offsets.foreach {
          case (tp, oam) =>
            if (first) first = false
            else builder.append(", ")

            builder.append(tp.show).append(" -> ").append(oam.show)
        }

        builder.append(')').toString
      }
    }
}
