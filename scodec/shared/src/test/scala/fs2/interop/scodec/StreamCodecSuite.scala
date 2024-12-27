/*
 * Copyright (c) 2013 Functional Streams for Scala
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

// Adapted from scodec-stream, licensed under 3-clause BSD

package fs2
package interop
package scodec

import org.scalacheck._
import Prop._
import _root_.scodec.Err
import _root_.scodec.bits._
import _root_.scodec.codecs
import _root_.scodec.codecs._
import _root_.scodec.Decoder
import cats.effect.IO

import scala.concurrent.duration._

class StreamCodecSuite extends Fs2Suite {

  property("many/tryMany") {
    Prop.forAll { (ints: List[Int]) =>
      val bits = vector(int32).encode(Vector.empty[Int] ++ ints).require
      val bits2 = StreamEncoder.many(int32).encodeAllValid(ints)
      assertEquals(bits, bits2)
      assertEquals(StreamDecoder.many(int32).decode[Fallible](Stream(bits)).toList, Right(ints))
      assertEquals(StreamDecoder.tryMany(int32).decode[Fallible](Stream(bits2)).toList, Right(ints))
    }
  }

  test("many/tryMany insufficient") {
    val bits = hex"00000001 00000002 0000".bits
    assertEquals(StreamDecoder.many(int32).decode[Fallible](Stream(bits)).toList, Right(List(1, 2)))
    assertEquals(
      StreamDecoder.tryMany(int32).decode[Fallible](Stream(bits)).toList,
      Right(List(1, 2))
    )
  }

  test("tryMany example") {
    val bits = StreamEncoder.many(int32).encodeAllValid(Vector(1, 2, 3))
    assert(
      StreamDecoder.tryMany(int32).decode[Fallible](Stream(bits)).toList == Right(List(1, 2, 3))
    )
  }

  test("many + flatMap + tryMany") {
    val decoder = StreamDecoder.many(bits(4)).flatMap { _ =>
      StreamDecoder.tryMany(
        bits(4).flatMap { b =>
          if (b == bin"0111") codecs.fail[BitVector](Err(""))
          else codecs.provide(b)
        }
      )
    }
    val actual = decoder
      .decode[Fallible](Stream.emits(hex"1a bc d7 ab 7a bc".toArray.map(BitVector(_))))
      .compile
      .fold(BitVector.empty)(_ ++ _)
    assert(actual == Right(hex"abcdababc".bits.drop(4)))
  }

  property("isolate") {
    forAll { (ints: List[Int], _: Long) =>
      val bits = vector(int32).encode(ints.toVector).require
      val d =
        StreamDecoder.many(int32).isolate(bits.size).map(_ => 0) ++
          StreamDecoder.many(int32).isolate(bits.size).map(_ => 1)
      val s = Stream(bits ++ bits)
      assertEquals(
        d.decode[Fallible](s).toVector,
        Right(
          Vector.fill(ints.size)(0) ++ Vector.fill(ints.size.toInt)(1)
        )
      )
    }
  }

  test("filter") {
    val bits = StreamEncoder.many(int32).encodeAllValid(Vector(1, 2, 3, 4))
    val decoder = StreamDecoder.tryMany(int32)
    val filteredDecoder = for (n <- decoder if n % 2 != 0) yield n
    assertEquals(filteredDecoder.decode[Fallible](Stream(bits)).toList, Right(List(1, 3)))
  }

  def genChunkSize = Gen.choose(1L, 128L)
  def genSmallListOfString = Gen.choose(0, 10).flatMap(n => Gen.listOfN(n, Gen.alphaStr))

  property("list of fixed size strings") {
    forAll(genSmallListOfString, genChunkSize) { (strings: List[String], chunkSize: Long) =>
      val bits = StreamEncoder.many(utf8_32).encodeAllValid(strings)
      val chunks = Stream.emits(BitVector.GroupedOp(bits).grouped(chunkSize).toSeq).covary[Fallible]
      chunks.through(StreamDecoder.many(utf8_32).toPipe).toList == Right(strings)
    }
  }

  def genSmallListOfInt = Gen.choose(0, 10).flatMap(n => Gen.listOfN(n, Arbitrary.arbitrary[Int]))
  property("list of fixed size ints") {
    forAll(genSmallListOfInt, genChunkSize) { (ints: List[Int], chunkSize: Long) =>
      val bits = StreamEncoder.many(int32).encodeAllValid(ints)
      val chunks = Stream.emits(BitVector.GroupedOp(bits).grouped(chunkSize).toSeq).covary[Fallible]
      chunks.through(StreamDecoder.many(int32).toPipe).toList == Right(ints)
    }
  }

  property("encode - emit") {
    forAll { (toEmit: Int, ints: List[Int]) =>
      val bv: BitVector = int32.encode(toEmit).require
      val e: StreamEncoder[Int] = StreamEncoder.emit[Int](bv)
      e.encode(Stream.emits(ints).covary[Fallible]).compile.fold(BitVector.empty)(_ ++ _) == Right(
        bv
      )
    }
  }

  test("encode - tryOnce") {
    assert(
      (StreamEncoder.tryOnce(codecs.fail[Int](Err("error"))) ++ StreamEncoder.many(int8))
        .encode(Stream(1, 2).covary[Fallible])
        .toList == Right(List(hex"01".bits, hex"02".bits))
    )
  }

  test("scodec-stream/issues/75") {
    val twoBytes = hex"0000"
    val dec = constant(twoBytes)
    val choiceDec = Decoder.choiceDecoder(dec, dec)
    val expected = List(())
    Stream
      .emits(twoBytes.toArray)
      .covary[IO]
      .metered(100.millis)
      .through(StreamDecoder.tryMany(choiceDec).toPipeByte)
      .compile
      .toList
      .map(actual => assert(actual == expected))
  }

  test("isolate raises error at end of input") {
    val decoder = StreamDecoder.isolate(800)(StreamDecoder.once(fixedSizeBytes(100, bytes)))
    val result = Stream
      .chunk(Chunk.byteVector(hex"00"))
      .covary[Fallible]
      .through(decoder.toPipeByte[Fallible])
      .compile
      .drain
    assertEquals(result, Left(CodecError(Err.InsufficientBits(800, 8, Nil))))
  }

  test("records with various chunks") {
    import _root_.scodec.bits._
    import _root_.scodec.codecs._
    case class Record(id: Int, bytes: ByteVector)
    val record = (int32 :: variableSizeBytes(int32, bytes)).as[Record]
    val count = 1024
    val all = (0 until count).map(i => Record(i, ByteVector.high(40))).toVector
    val encoded = vector(record).encode(all).require

    val decoder =
      StreamDecoder.many(int32).flatMap { _ =>
        StreamDecoder.once(int32).flatMap { size =>
          StreamDecoder
            .once(
              StreamDecoder
                .isolate(size * 8L)(StreamDecoder.once(fixedSizeBytes(size.toLong, bytes)))
                .strict
            )
            .flatMap(StreamDecoder.emits)
        }
      }

    val chunky = {
      var chunks = List.empty[BitVector]
      var rem = encoded
      while (rem.nonEmpty) {
        val (hd, tl) = rem.splitAt(11 * 8)
        chunks = hd :: chunks
        rem = tl
      }
      Chunk.from(chunks.reverse)
    }

    val result =
      Stream.chunk(chunky).chunkLimit(1).unchunks.through(decoder.toPipe[Fallible]).compile.toVector
    assertEquals(result.toOption.get.size, count)
  }
}
