
package sh.btc

import sh.btc.BitcoinUtil._
import sh.ecc.Util._
import sh.util.BytesUtil._
import sh.util.StringUtil._
import sh.util.BigIntUtil._

object DataStructures {
  
  case class TxIn(txHash:String, vOut:Int) {
    var optScriptSig:Option[Seq[Byte]] = None
    def setScriptSig(scriptSig:Seq[Byte]) = {
      optScriptSig = Some(scriptSig)
      this
    }
    
    var seqNum:Long = BigInt("FFFFFFFF", 16).toLong
    def setSeqNum(long:Long) = {
      this.seqNum = long
      this
    }
    def seqNumBytes = getFixedIntBytes(seqNum, 4) // unsigned
    def unsetScriptSig = optScriptSig = None

    override def toString = txHash+":"+vOut
  }       
  case class TxOut(optAddress:Option[String], value:BigInt) {
    def this (address:String, value:BigInt) = this(Some(address), value)
    override def toString = optAddress.getOrElse("None")+":"+value
    lazy val optScriptPubKey = optAddress.map(getScriptPubKeyFromAddress)
  }        
  
  case class TxWit(data:Seq[Seq[Byte]]) { // witness is a seq of stack element, each a seq of byte
    override def toString = s"witness_{data.size}_bytes"
  }

  case class Tx(
    version:Long, ins:Seq[TxIn], outs:Seq[TxOut], wits:Array[TxWit], lockTime:Long, txid:String, 
    isSegWit:Boolean, segWitTxHash:String, size:Int, vSize:Int
  ) {
    def printTx = {
      ins foreach (in => println("In <- "+in))
      outs.zipWithIndex foreach {case (out, i) => println(s"Out -> $txid:$i => "+out)}
      println
    }    
    def serialize = createSegWitTx(version, ins zip wits, outs, lockTime)
  }

  
  class BitcoindBlockSummary(hash:String, prevBlockHash:String, time:Long, version:Long, txHashes:Seq[String]) 
  
  case class BitcoindBlock(
    hash:String, prevBlockHash:String, time:Long, version:Long, txs:Seq[Tx], hexTxs:Seq[String],
    merkleRoot:String, nBits:Seq[Byte], nonce:Long
  ) extends BitcoindBlockSummary(hash, prevBlockHash, time, version, txs.map(_.txid)) {
    if (nBits.size != 4) throw new Exception("NBits must be exactly 4 bytes")
    def serialize = {
      // block is serialized as header + numTxs + txs
      // header = version(4)+prevBlkHeaderHash(32)+merkleRoot(32)+4(time)+4(nBits)+4(nonce)
      val versionBytes = getFixedIntBytes(version, 4)
      val timeBytes = getFixedIntBytes(time, 4) // unsigned
      val nonceBytes = getFixedIntBytes(nonce, 4) // unsigned
      val prevBlockHashBytes = toggleEndianString(prevBlockHash)
      val merkleRootBytes = toggleEndianString(merkleRoot)
      val txBytes = txs.flatMap(_.serialize)
      val txBytesFromHex = hexTxs.flatMap(_.decodeHex)
      val numTxBytes = getVarIntBytes(txs.size)
      val header = versionBytes ++ prevBlockHashBytes++merkleRootBytes ++ timeBytes ++ nBits ++ nonceBytes
      val blk = header ++ numTxBytes ++ txBytesFromHex
      blk
    }
    def computeMerkleRoot = {
      /* Computing the Merkle root: The transactions are first arranged in some
      order that satisfies the consensus rules given below. Their transaction hashes
      (TXIDs) are considered as the last row (leaves) of the tree that will be constructed.
      Starting with the last row, each row is iteratively processed to get the
      previous (parent) row until the currently processing row has only one node, the
      Merkle root. If the currently processing row has two or more nodes, we first
      ensure that there are even number (say n) of them, by adding a null element
      if necessary. Then we pair the nodes to form n/2 pairs. Each pair (L, R) is
      concatenated and its hash SHA256(SHA256(L||R)) forms the parent for the next
      iteration. This process is repeated until the root is reached. */    
      // https://bitcoin.org/en/developer-reference#merkle-trees
      if (txs.size == 1) txs(0).txid // only coinbase
      else {
        var currRow = txs.map(_.txid.decodeHex.reverse)
        while (currRow.size > 1) {
          val newCurrRow = if (currRow.size.isEven) currRow else currRow :+ currRow.last
          currRow = newCurrRow.grouped(2).toSeq.map(a => dsha256(a(0) ++ a(1)))
        }
        currRow(0).reverse.encodeHex.toLowerCase
      }
    }    
  }  
}

