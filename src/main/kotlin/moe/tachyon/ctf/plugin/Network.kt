package moe.tachyon.ctf.plugin

import moe.tachyon.ctf.common.MAGIC_NUMBER
import moe.tachyon.ctf.common.PROTOCOL_VERSION
import moe.tachyon.ctf.common.RequestPacketType
import moe.tachyon.ctf.common.ResponsePacketType
import moe.tachyon.ctf.plugin.MinecraftCtfPlugin.recordsFolder
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.Socket

private inline fun <T> withConnection(block: (input: DataInputStream, output: DataOutputStream) -> T): T
{
    Socket("127.0.0.1", 9898).use()
    { socket ->
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())

        output.writeLong(MAGIC_NUMBER)
        output.writeInt(PROTOCOL_VERSION)

        if (input.readLong() != MAGIC_NUMBER)
        {
            error("Server sent invalid magic number, disconnecting")
        }

        val protocolVersion = input.readInt()
        if (protocolVersion != PROTOCOL_VERSION)
        {
            error("Server sent unsupported protocol version $protocolVersion, disconnecting")
        }

        return block(input, output)
    }
}

fun uploadRecord(file: File) = withConnection()
{ input, output ->
    output.writeByte(RequestPacketType.UPLOAD_RECORD.toInt())
    output.writeUTF(file.nameWithoutExtension)
    output.writeLong(file.readBytes().size.toLong())
    file.inputStream().use()
    {
        it.copyTo(output)
    }
}

fun getRecords(): List<Pair<String, Long>> = withConnection()
{ input, output ->
    output.writeByte(RequestPacketType.GET_RECORDS.toInt())
    val packetType = input.readUnsignedByte().toUByte()
    if (packetType != ResponsePacketType.RECORD_LIST)
    {
        if (packetType == ResponsePacketType.MESSAGE)
        {
            val message = input.readUTF()
            error(message)
        }
        error("Server sent unexpected packet type $packetType, disconnecting")
    }

    val recordCount = input.readInt()
    val records = mutableListOf<Pair<String, Long>>()
    repeat(recordCount)
    {
        val name = input.readUTF()
        val size = input.readLong()
        records += name to size
    }
    records
}

fun downloadRecord(fileName: String, onProgress: (cur: Long, tot: Long) -> Unit) = withConnection()
{ input, output ->
    output.writeByte(RequestPacketType.DOWNLOAD_RECORD.toInt())
    output.writeUTF(fileName)

    val packetType = input.readUnsignedByte().toUByte()
    if (packetType != ResponsePacketType.RECORD_DATA)
    {
        if (packetType == ResponsePacketType.MESSAGE)
        {
            val message = input.readUTF()
            error(message)
        }
        error("Server sent unexpected packet type $packetType, disconnecting")
    }

    val name = input.readUTF()
    require(name == fileName) { "Server sent mismatched file name($name), disconnecting" }
    val size = input.readLong()
    val file = File(recordsFolder, "$fileName.dat")
    file.parentFile.mkdirs()
    file.outputStream().use()
    { fos ->
        onProgress(0, size)
        val buffer = ByteArray(4096)
        var remaining = size
        while (remaining > 0)
        {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read == -1) break
            fos.write(buffer, 0, read)
            remaining -= read
            onProgress(size - remaining, size)
        }
    }
}