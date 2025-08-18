package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch

import android.util.Log
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.result.ResultSuccess
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseRequset
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.model.CarelevoPatchRptInfusionInfoRequestModel
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.joda.time.DateTime
import javax.inject.Inject

class CarelevoPatchRptInfusionInfoProcessUseCase @Inject constructor(
    private val patchInfoRepository : CarelevoPatchInfoRepository
) {

    fun execute(request : CarelevoUseCaseRequset) : Single<ResponseResult<CarelevoUseCaseResponse>> {
        return Single.fromCallable {
            runCatching {
                if(request !is CarelevoPatchRptInfusionInfoRequestModel) {
                    throw IllegalArgumentException("request is not carelevoPatchRptInfusionInfoRequestModel")
                }

                Log.d("connect_test", "[CarelevoPatchRptInfusionInfoProcessUseCase] request : $request")

                val patchInfo = patchInfoRepository.getPatchInfoBySync()
                    ?: throw NullPointerException("patch info must be not null")

                val updatePatchInfoResult = patchInfoRepository.updatePatchInfo(
                    patchInfo.copy(
                        mode = request.mode,
                        runningMinutes = request.runningMinute,
                        insulinRemain = request.remains,
                        infusedTotalBasalAmount = request.infusedTotalBasalAmount,
                        infusedTotalBolusAmount = request.infusedTotalBolusAmount,
                        pumpState = request.pumpState,
                        updatedAt = DateTime.now()
                    )
                )

                if(!updatePatchInfoResult) {
                    throw IllegalStateException("update patch info is failed")
                }
                ResultSuccess
            }.fold(
                onSuccess = {
                    ResponseResult.Success(it as CarelevoUseCaseResponse)
                },
                onFailure = {
                    ResponseResult.Error(it)
                }
            )
        }.observeOn(Schedulers.io())
    }
}