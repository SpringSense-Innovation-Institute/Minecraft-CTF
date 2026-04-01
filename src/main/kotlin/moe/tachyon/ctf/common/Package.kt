package moe.tachyon.ctf.common

const val MAGIC_NUMBER = 0x520_1314_aabbccdd_aL
const val PROTOCOL_VERSION = 1

object RequestPacketType
{
    const val GET_RECORDS: UByte = 0x10U
    const val UPLOAD_RECORD: UByte = 0x11U
    const val DOWNLOAD_RECORD: UByte = 0x12U
}

object ResponsePacketType
{
    const val MESSAGE: UByte = 0x10U
    const val RECORD_LIST: UByte = 0x11U
    const val RECORD_DATA: UByte = 0x12U
}