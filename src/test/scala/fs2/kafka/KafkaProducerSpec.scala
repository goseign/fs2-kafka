package fs2.kafka

import cats.effect.IO
import fs2.{Chunk, Stream}
import org.apache.kafka.clients.producer.ProducerRecord

final class KafkaProducerSpec extends BaseKafkaSpec {
  it("should be able to produce records with single") {
    withKafka { (config, topic) =>
      createCustomTopic(topic, partitions = 3)
      val toProduce = (0 until 100).map(n => s"key-$n" -> s"value->$n")

      val produced =
        (for {
          producer <- producerStream[IO].using(producerSettings(config))
          message <- Stream.chunk(Chunk.seq(toProduce).map {
            case passthrough @ (key, value) =>
              ProducerMessage.single(new ProducerRecord(topic, key, value), passthrough)
          })
          batched <- Stream.eval(producer.produceBatched(message)).buffer(toProduce.size)
          passthrough <- Stream.eval(batched.map(_.passthrough))
        } yield passthrough).compile.toVector.unsafeRunSync()

      produced should contain theSameElementsAs toProduce

      val consumed =
        consumeNumberKeyedMessagesFrom[String, String](topic, produced.size)

      consumed should contain theSameElementsAs produced
    }
  }

  it("should be able to produce records with multiple") {
    withKafka { (config, topic) =>
      createCustomTopic(topic, partitions = 3)
      val toProduce = (0 until 10).map(n => s"key-$n" -> s"value->$n").toList
      val toPassthrough = "passthrough"

      val produced =
        (for {
          producer <- producerStream[IO].using(producerSettings(config))
          records = toProduce.map {
            case (key, value) =>
              new ProducerRecord(topic, key, value)
          }
          message = ProducerMessage.multiple(records, toPassthrough)
          result <- Stream.eval(producer.produce(message))
        } yield result).compile.lastOrError.unsafeRunSync

      produced match {
        case ProducerResult.Multiple(parts, passthrough) =>
          val produced = parts.map(part => (part.record.key, part.record.value))
          assert(produced == toProduce && passthrough == toPassthrough)

        case result =>
          fail(s"unexpected producer result: $result")
      }

      val consumed =
        consumeNumberKeyedMessagesFrom[String, String](topic, toProduce.size)

      consumed should contain theSameElementsAs toProduce
    }
  }

  it("should be able to produce zero records with multiple") {
    withKafka { (config, topic) =>
      createCustomTopic(topic, partitions = 3)
      val passthrough = "passthrough"

      val result =
        (for {
          producer <- producerStream[IO].using(producerSettings(config))
          records = List.empty[ProducerRecord[String, String]]
          message = ProducerMessage.multiple(records, passthrough)
          result <- Stream.eval(producer.produce(message))
        } yield result).compile.lastOrError.unsafeRunSync

      assert(result.passthrough == passthrough)
    }
  }

  it("should be able to produce zero records with passthrough") {
    withKafka { (config, topic) =>
      createCustomTopic(topic, partitions = 3)
      val passthrough = "passthrough"

      val result =
        (for {
          producer <- producerStream[IO].using(producerSettings(config))
          result <- Stream.eval(producer.produce(ProducerMessage.passthrough(passthrough)))
        } yield result).compile.lastOrError.unsafeRunSync

      assert(result.passthrough == passthrough)
    }
  }
}
