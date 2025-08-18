package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch

import android.util.Log
import info.nightscout.androidaps.plugins.pump.carelevo.domain.CarelevoPatchObserver
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.RequestResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.SafetyCheckResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.SafetyCheckResultModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.result.ResultSuccess
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchRepository
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.ofType
import io.reactivex.rxjava3.schedulers.Schedulers
import org.joda.time.DateTime
import javax.inject.Inject

class CarelevoPatchSafetyCheckUseCase @Inject constructor(
    private val patchObserver : CarelevoPatchObserver,
    private val patchRepository : CarelevoPatchRepository,
    private val patchInfoRepository : CarelevoPatchInfoRepository
) {

    fun execute() : Single<ResponseResult<CarelevoUseCaseResponse>> {
        return Single.fromCallable {
            runCatching {
                patchRepository.requestSafetyCheck()
                    .observeOn(Schedulers.io())
                    .blockingGet()
                    .takeIf { it is RequestResult.Pending }
                    ?: throw IllegalStateException("request safety check is not pending")

                Log.d("connect_test", "[CarelevoRxPatchSafetyCheckUseCase] 1. 안전점검 요청")

                val requestSafetyCheckResult = patchObserver.patchEvent
                    .ofType<SafetyCheckResultModel>()
                    .blockingFirst()

                Log.d("connect_test", "[CarelevoRxPatchSafetyCheckUseCase] 2. 안전점검 요청 결과 수신 : $requestSafetyCheckResult")

                if(!(requestSafetyCheckResult.result == SafetyCheckResult.REP_REQUEST || requestSafetyCheckResult.result == SafetyCheckResult.REP_REQUEST1)) {
                    throw IllegalStateException("request safety check result is failed")
                }

                val currentThread = Thread.currentThread().name
                Log.d("connect_test", "[CarelevoRxPatchSafetyCheckUseCase] current thread : $currentThread")

                val ackResult = patchObserver.patchEvent
                    .ofType<SafetyCheckResultModel>()
                    .blockingFirst()

                Log.d("connect_test", "[CarelevoRxPatchSafetyCheckUseCase] 3. 안전점검 요청 결과 액크 수신 : $ackResult")

                if(ackResult.result != SafetyCheckResult.SUCCESS) {
                    throw IllegalStateException("safety check ack result is failed")
                }

                val patchInfo = patchInfoRepository.getPatchInfoBySync()
                    ?: throw NullPointerException("patch info must be not null")

                val updatePatchInfoResult = patchInfoRepository.updatePatchInfo(
                    patchInfo.copy(checkSafety = true, updatedAt = DateTime.now())
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
        }.subscribeOn(Schedulers.io())
    }
}