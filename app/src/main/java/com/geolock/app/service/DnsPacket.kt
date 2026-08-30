package com.geolock.app.service

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object DnsPacket {
    fun extractUdpPayload(packet: ByteArray, length: Int): DnsQuery? {
        if (length < 28) return null
        val version = (packet[0].toInt() ushr 4) and 0xF
        if (version != 4) return null
        val ihl = (packet[0].toInt() and 0xF) * 4
        if (ihl < 20 || length < ihl + 8) return null
        if ((packet[9].toInt() and 0xFF) != 17) return null
        val destPort = unsignedShort(packet, ihl + 2)
        if (destPort != 53) return null
        val srcPort = unsignedShort(packet, ihl)
        val payloadOffset = ihl + 8
        val payloadLength = length - payloadOffset
        if (payloadLength < 12) return null
        val payload = packet.copyOfRange(payloadOffset, length)
        val host = parseQuestionName(payload) ?: return DnsQuery(ihl, srcPort, packet, payload, null)
        return DnsQuery(ihl, srcPort, packet, payload, host)
    }

    fun sinkhole(query: DnsQuery): ByteArray {
        val answer = buildSinkholeDns(query.dns)
        return wrapUdp(query.original, query.ihl, query.srcPort, answer)
    }

    fun forward(query: DnsQuery, protect: (DatagramSocket) -> Boolean): ByteArray? {
        val socket = DatagramSocket()
        return try {
            if (!protect(socket)) return null
            socket.soTimeout = 2000
            val upstream = InetAddress.getByName("1.1.1.1")
            socket.send(DatagramPacket(query.dns, query.dns.size, upstream, 53))
            val buffer = ByteArray(4096)
            val incoming = DatagramPacket(buffer, buffer.size)
            socket.receive(incoming)
            wrapUdp(query.original, query.ihl, query.srcPort, buffer.copyOf(incoming.length))
        } catch (_: Exception) {
            null
        } finally {
            socket.close()
        }
    }

    private fun buildSinkholeDns(request: ByteArray): ByteArray {
        val out = ByteBuffer.allocate(request.size + 16).order(ByteOrder.BIG_ENDIAN)
        out.put(request)
        out.putShort(2, (0x8180).toShort())
        out.putShort(6, 1)
        out.putShort(0xC00C.toShort())
        out.putShort(1)
        out.putShort(1)
        out.putInt(30)
        out.putShort(4)
        out.putInt(0)
        val result = ByteArray(out.position())
        out.rewind()
        out.get(result)
        return result
    }

    private fun wrapUdp(original: ByteArray, ihl: Int, clientPort: Int, dns: ByteArray): ByteArray {
        val ipHeader = original.copyOfRange(0, ihl)
        val total = ihl + 8 + dns.size
        val out = ByteArray(total)
        System.arraycopy(ipHeader, 0, out, 0, ihl)
        out[2] = (total ushr 8).toByte()
        out[3] = total.toByte()
        out[8] = 64
        repeat(4) { index ->
            val src = original[12 + index]
            out[12 + index] = original[16 + index]
            out[16 + index] = src
        }
        out[ihl] = (53 ushr 8).toByte()
        out[ihl + 1] = 53.toByte()
        out[ihl + 2] = (clientPort ushr 8).toByte()
        out[ihl + 3] = clientPort.toByte()
        val udpLen = 8 + dns.size
        out[ihl + 4] = (udpLen ushr 8).toByte()
        out[ihl + 5] = udpLen.toByte()
        out[ihl + 6] = 0
        out[ihl + 7] = 0
        System.arraycopy(dns, 0, out, ihl + 8, dns.size)
        out[10] = 0
        out[11] = 0
        val sum = ipChecksum(out, ihl)
        out[10] = (sum ushr 8).toByte()
        out[11] = sum.toByte()
        return out
    }

    private fun parseQuestionName(dns: ByteArray): String? {
        if (dns.size < 13) return null
        var offset = 12
        val labels = ArrayList<String>()
        var guard = 0
        while (offset < dns.size && guard++ < 32) {
            val len = dns[offset].toInt() and 0xFF
            if (len == 0) break
            if (len and 0xC0 != 0) return null
            if (offset + 1 + len > dns.size) return null
            labels += String(dns, offset + 1, len, Charsets.US_ASCII)
            offset += 1 + len
        }
        return labels.joinToString(".").lowercase().ifBlank { null }
    }

    private fun unsignedShort(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
    }

    private fun ipChecksum(packet: ByteArray, headerLength: Int): Int {
        var sum = 0
        var i = 0
        while (i < headerLength) {
            sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            i += 2
        }
        while (sum ushr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return sum.inv() and 0xFFFF
    }

    data class DnsQuery(
        val ihl: Int,
        val srcPort: Int,
        val original: ByteArray,
        val dns: ByteArray,
        val host: String?
    )
}
