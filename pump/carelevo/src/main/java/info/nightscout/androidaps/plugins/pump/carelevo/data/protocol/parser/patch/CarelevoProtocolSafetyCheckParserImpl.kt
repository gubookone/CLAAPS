package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.patch

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolSafetyCheckRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.CarelevoProtocolParser

class CarelevoProtocolSafetyCheckParserImpl(
    override val command : Int
) : CarelevoProtocolParser<ByteArray, ProtocolSafetyCheckRspModel> {

    override fun parse(data: ByteArray): ProtocolSafetyCheckRspModel {
        val timestamp = System.currentTimeMillis()
        val cmd = data[0].toUByte().toInt()
        val result = data[1].toUByte().toInt()
        val remains100 = data[2].toUByte().toInt() * 100
        val remainsInteger = data[3].toUByte().toInt()
        val volume = remains100 + remainsInteger

        return ProtocolSafetyCheckRspModel(
            timestamp,
            cmd,
            result,
            volume
        )
    }
}