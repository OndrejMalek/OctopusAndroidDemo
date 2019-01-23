package org.oelab.octopusengine.octolabapp.ui.main

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.channels.DatagramChannel

interface IUdpSocket {
    fun open()
    fun send(
        message: String,
        ipAddress: String,
        port: String
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
        port: String
    ) {
        if (socket == null) throw SocketNotOpenException()
        val messageBytes = message.toByteArray()
        socket!!.send(
            DatagramPacket(
                messageBytes,
                messageBytes.size,
                InetSocketAddress(
                    InetAddress.getByName(ipAddress),
                    port.toInt()
                )
            )
        )

        Log.d("UdpSocket.send", "message: $message , Thread: ${Thread.currentThread().name}")
    }


    class SocketNotOpenException : Exception()

    override fun close() {
        socket!!.close()
    }

}

fun toRGBUdpMessage(rgb: RGB) =
    "{R: ${rgb.red}, G:${rgb.green}, B:${rgb.blue}}\n"