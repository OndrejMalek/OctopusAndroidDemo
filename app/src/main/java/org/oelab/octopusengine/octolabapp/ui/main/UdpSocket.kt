package org.oelab.octopusengine.octolabapp.ui.main

import android.util.Log
import org.apache.commons.lang3.builder.ReflectionToStringBuilder
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.channels.DatagramChannel
import java.nio.charset.Charset

interface IUdpSocket {
    fun open()
    fun send(
        message: String,
        ipAddress: String,
        port: String,
        charset: Charset
    )

    fun close()
}

class UdpSocket(var socket: DatagramSocket? = null) : IUdpSocket {

    override fun open() {
        socket = if (socket == null) DatagramChannel.open().socket() else socket

        if (socket == null) throw CannotOpenSocketException()

        socket!!.bind(null)
        Log.d("UdpSocket.open", "Thread: ${Thread.currentThread().name}")
    }

    class CannotOpenSocketException : Exception()

    override fun send(
        message: String,
        ipAddress: String,
        port: String,
        charset: Charset
    ) {
        if (socket == null) throw SocketNotOpenException()
        val messageBytes = message.toByteArray(charset)
        val datagramPacket = DatagramPacket(
            messageBytes,
            messageBytes.size,
            InetSocketAddress(
                InetAddress.getByName(ipAddress),
                port.toInt()
            )
        )
        socket!!.send(
            datagramPacket
        )

        Log.d("UdpSocket.send", "packet: ${ReflectionToStringBuilder.toString(datagramPacket)} \n message: $message , Thread: ${Thread.currentThread().name}")
    }


    class SocketNotOpenException : Exception()

    override fun close() {
        socket?.close()
        socket = null
    }

}

