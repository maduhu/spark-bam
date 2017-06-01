package org.hammerlab.bam.iterator

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder.LITTLE_ENDIAN

import org.hammerlab.bgzf.block.{ Block, ByteStream, ByteStreamI }
import org.hammerlab.bgzf.{ Pos, block }
import org.hammerlab.io.{ Buffer, ByteChannel }
import org.hammerlab.iterator.FlatteningIterator._
import org.hammerlab.iterator.{ FlatteningIterator, SimpleBufferedIterator }

/**
 * Interface for iterators that wrap a (compressed) BAM-file [[InputStream]] and emit one object for each underlying
 * record.
 */
trait RecordIterator[T, Stream <: ByteStreamI[_]]
  extends SimpleBufferedIterator[T] {

//  def makeBlockStream: Stream

//  val compressedByteChannel: Channel

  // Uncompressed BGZF blocks
//  val blockStream: Stream = makeBlockStream //block.Stream(compressedByteChannel)

  // Uncompressed bytes; also exposes pointer to current-block
  val stream: Stream

//  val uncompressedBytes: FlatteningIterator[Byte, Block] = blockStream.smush

  // Uncompressed byte-channel, for reading ints into a buffer
  val uncompressedByteChannel: ByteChannel = stream

  private val b4 = Buffer(4)

  def readInt: Int = {
    b4.position(0)
    uncompressedByteChannel.read(b4)
    b4.position(0)
    b4.getInt
  }

  def getHeaderEndPos: Pos = {
    uncompressedByteChannel.read(b4)
    require(b4.array().map(_.toChar).mkString("") == "BAM\1")

    val headerLength = readInt

    /** Skip [[headerLength]] bytes */
    stream.drop(headerLength)

    val numReferences = readInt

    for {
      _ ← 0 until numReferences
    } {
      val refNameLen = readInt
      val nameBuffer = Buffer(refNameLen + 4)  // reference name and length
      uncompressedByteChannel.read(nameBuffer)
    }

    curPos.get
  }

  val headerEndPos = getHeaderEndPos

  def curBlock: Option[Block] = stream.curBlock
  def curPos: Option[Pos] = stream.curPos
}

//trait MakeBlockStream {
//  self: RecordIterator[_, ByteStream] ⇒
//  def makeBlockStream: block.Stream =
//    block.Stream(compressedByteChannel)
//}