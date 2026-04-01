package moe.tachyon.ctf.server

import moe.tachyon.ctf.common.MAGIC_NUMBER
import moe.tachyon.ctf.common.PROTOCOL_VERSION
import moe.tachyon.ctf.common.RequestPacketType
import moe.tachyon.ctf.common.ResponsePacketType
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections

private val clients = Collections.synchronizedList(mutableListOf<Triple<Socket, DataInputStream, DataOutputStream>>())

fun main(args: Array<String>)
{
    val port = args.getOrNull(0)?.toIntOrNull() ?: 9898

    val server = ServerSocket(port)

    println("Server started on port $port")

    while (true)
    {
        val client = server.accept()
        println("Client connected: ${client.inetAddress.hostAddress}:${client.port}")
        Thread { work(client) }.start()
    }
}

private fun work(client: Socket): Unit = client.use()
{ client ->
    val input = DataInputStream(client.getInputStream())
    val output = DataOutputStream(client.getOutputStream())

    if (input.readLong() != MAGIC_NUMBER)
    {
        println("Client ${client.inetAddress.hostAddress}:${client.port} sent invalid magic number, disconnecting")
        return
    }

    val protocolVersion = input.readInt()
    if (protocolVersion != PROTOCOL_VERSION)
    {
        println("Client ${client.inetAddress.hostAddress}:${client.port} sent unsupported protocol version $protocolVersion, disconnecting")
        return
    }

    output.writeLong(MAGIC_NUMBER)
    output.writeInt(PROTOCOL_VERSION)

    clients += Triple(client, DataInputStream(client.getInputStream()), DataOutputStream(client.getOutputStream()))

    try
    {
        while (client.isConnected && !client.isClosed)
        {
            when (val packetType = input.readUnsignedByte().toUByte())
            {
                RequestPacketType.GET_RECORDS   -> handleGetRecords(input, output)
                RequestPacketType.UPLOAD_RECORD -> handleUploadRecord(input, output)
                RequestPacketType.DOWNLOAD_RECORD -> handleDownloadRecord(input, output)
                else -> throw IllegalArgumentException("Unknown packet type: $packetType")
            }
        }
    }
    finally
    {
        clients -= Triple(client, input, output)
        println("Client disconnected: ${client.inetAddress.hostAddress}:${client.port}")
    }
}

private fun handleGetRecords(input: DataInputStream, output: DataOutputStream)
{
    val files = File("records").listFiles()?.filter { it.extension == "dat" } ?: emptyList()
    output.writeByte(ResponsePacketType.RECORD_LIST.toInt())
    output.writeInt(files.size)
    for (file in files)
    {
        output.writeUTF(file.nameWithoutExtension)
        output.writeLong(file.length())
    }
}

private fun handleUploadRecord(input: DataInputStream, output: DataOutputStream)
{
    val fileName = input.readUTF()
    val fileLength = input.readLong()
    val file = File("records/$fileName.dat")
    file.parentFile.mkdirs()
    val buffer = ByteArray(4096)
    runCatching()
    {
        file.outputStream().use()
        { fos ->
            var remaining = fileLength
            while (remaining > 0)
            {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read == -1) error("Unexpected end of stream")
                fos.write(buffer, 0, read)
                remaining -= read
            }
        }
    }.onFailure()
    {
        file.delete()
    }.getOrThrow()
}

private fun handleDownloadRecord(input: DataInputStream, output: DataOutputStream)
{
    val fileName = input.readUTF()
    val file = File("records/$fileName.dat")
    if (!file.exists())
    {
        println("Client requested non-existent record: $fileName")
        output.writeByte(ResponsePacketType.MESSAGE.toInt())
        output.writeUTF("Record not found")
        return
    }

    output.writeByte(ResponsePacketType.RECORD_DATA.toInt())
    output.writeUTF(file.nameWithoutExtension)
    output.writeLong(file.length())
    file.inputStream().use()
    {
        it.copyTo(output)
    }
}