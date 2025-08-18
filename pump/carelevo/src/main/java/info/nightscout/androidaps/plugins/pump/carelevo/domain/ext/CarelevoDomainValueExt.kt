package info.nightscout.androidaps.plugins.pump.carelevo.domain.ext

import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.basal.CarelevoBasalSegmentDomainModel
import java.util.UUID

internal fun List<CarelevoBasalSegmentDomainModel>.splitSegment() : List<CarelevoBasalSegmentDomainModel> {
    val segmentList = LinkedHashMap<Int, CarelevoBasalSegmentDomainModel>()
    val splitSegmentList = LinkedHashMap<Int, CarelevoBasalSegmentDomainModel>()

    this.forEach { segment ->
        val startTime = segment.startTime / 10
        val endTime = segment.endTime / 60
        CarelevoBasalSegmentDomainModel(
            startTime = startTime,
            endTime = endTime,
            speed = segment.speed
        )
    }

    this.forEach { segment ->
        segmentList[segment.startTime] = segment
    }

    for(i in 0..23) {
        segmentList
            .filter { it.key <= i }
            .takeIf { it.isNotEmpty() }
            ?.maxBy { it.key }
            ?.let { segment ->
                splitSegmentList[i] = CarelevoBasalSegmentDomainModel(
                    startTime = i,
                    endTime = i + 1,
                    speed = segment.value.speed
                )
            }
    }

    return splitSegmentList.map { it.value }
}

internal fun generateUUID() : String {
    return UUID.randomUUID().toString()
}